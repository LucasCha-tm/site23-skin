package fr.lucascha.site23skin.managers;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import net.skinsrestorer.api.PlayerWrapper;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Gère la fusion des skins et leur application via l'API SkinsRestorer.
 *
 * LOGIQUE DE FUSION :
 *   Un skin Minecraft est une image 64x64 px.
 *   Lignes y=0..15  → zone TÊTE + chapeau  → conservée depuis le joueur original
 *   Lignes y=16..63 → zone CORPS/BRAS/JAMBES → remplacée par le skin de grade
 */
public class SkinManager {

    private final Site23SkinPlugin plugin;
    private final Logger log;

    public SkinManager(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ─────────────────────────────────────────────────────────────
    // POINT D'ENTRÉE PRINCIPAL
    // ─────────────────────────────────────────────────────────────

    /**
     * Applique le skin fusionné (tête joueur + corps grade) de manière asynchrone.
     *
     * @param player   Le joueur cible
     * @param grade    Le grade à appliquer
     * @param callback Appelé sur le thread principal : true = succès, false = échec
     */
    public void applyGradeSkin(Player player, GradeData grade, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = doApply(player, grade);
                } catch (Exception e) {
                    log.severe("Erreur application skin pour " + player.getName() + " : " + e.getMessage());
                    e.printStackTrace();
                }
                final boolean result = success;
                new BukkitRunnable() {
                    @Override public void run() { callback.accept(result); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Restaure le skin Mojang original du joueur (supprime le skin custom SR).
     *
     * @param player Le joueur
     * @param onDone Callback exécuté sur le thread principal une fois terminé
     */
    public void restoreOriginalSkin(Player player, Runnable onDone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    SkinsRestorer sr = plugin.getSkinsRestorer();
                    PlayerStorage ps = sr.getPlayerStorage();
                    // Supprime le skin custom → SR remet le skin Mojang du joueur
                    ps.removeSkinIdOfPlayer(PlayerWrapper.of(player));
                    sr.getSkinApplier(Player.class).applySkin(player);
                    log.info("Skin restauré pour " + player.getName());
                } catch (Exception e) {
                    log.severe("Erreur restauration skin " + player.getName() + " : " + e.getMessage());
                }
                new BukkitRunnable() {
                    @Override public void run() { if (onDone != null) onDone.run(); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    // ─────────────────────────────────────────────────────────────
    // LOGIQUE INTERNE (thread async)
    // ─────────────────────────────────────────────────────────────

    private boolean doApply(Player player, GradeData grade) throws Exception {

        // 1) Charge le PNG du skin de grade
        File skinFile = new File(plugin.getDataFolder(), "skins/" + grade.getSkinFile());
        if (!skinFile.exists()) {
            log.warning("Fichier skin introuvable : skins/" + grade.getSkinFile()
                    + "  → Place le PNG dans plugins/Site23Skin/skins/");
            return false;
        }
        BufferedImage gradeSkin = ImageIO.read(skinFile);
        if (gradeSkin == null) {
            log.warning("Impossible de lire : " + skinFile.getName());
            return false;
        }
        gradeSkin = ensureSize(gradeSkin, 64, 64);

        // 2) Récupère le skin actuel du joueur depuis Mojang
        BufferedImage playerSkin = fetchPlayerSkinFromMojang(player.getUniqueId());
        if (playerSkin == null) {
            log.warning("Skin Mojang introuvable pour " + player.getName()
                    + " – le corps du grade sera utilisé pour la tête aussi.");
            playerSkin = gradeSkin;
        } else {
            playerSkin = ensureSize(playerSkin, 64, 64);
            // Sauvegarde la texture originale pour pouvoir la restaurer
            plugin.getPlayerDataManager().saveOriginalSkin(
                    player.getUniqueId(), fetchRawTextureValue(player.getUniqueId()));
        }

        // 3) Fusion : tête+chapeau du joueur (y 0-15) + corps du grade (y 16-63)
        BufferedImage merged = mergeSkins(playerSkin, gradeSkin);

        // 4) Convertit l'image fusionnée en PNG base64
        String base64Png = imageToBase64(merged);

        // 5) Crée le payload de texture Minecraft
        String texturePayload = buildTexturePayload(base64Png);

        // 6) Enregistre et applique via SkinsRestorer
        SkinsRestorer sr = plugin.getSkinsRestorer();
        SkinStorage   ss = sr.getSkinStorage();
        PlayerStorage ps = sr.getPlayerStorage();

        String skinId = "site23_" + player.getUniqueId().toString().replace("-", "");

        SkinProperty skinProp = SkinProperty.of("textures", texturePayload, "");
        ss.setCustomSkinData(skinId, skinProp);
        ps.setSkinIdOfPlayer(PlayerWrapper.of(player), skinId);
        sr.getSkinApplier(Player.class).applySkin(player);

        log.info("✔ Skin appliqué à " + player.getName() + " (grade : " + grade.getDisplayName() + ")");
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // FUSION D'IMAGES
    // ─────────────────────────────────────────────────────────────

    /**
     * Fusionne deux skins 64x64 :
     *   - Base   : skin de grade (corps complet)
     *   - Dessus : lignes 0-15 du skin joueur (tête + chapeau)
     *
     * Layout skin Minecraft 64x64 :
     *   y= 0.. 7 : tête principale (faces)
     *   y= 8..15 : couche chapeau (overlay)
     *   y=16..31 : buste + bras droits
     *   y=32..47 : jambes + bras gauches
     *   y=48..63 : couches overlay corps/bras/jambes
     */
    private BufferedImage mergeSkins(BufferedImage playerSkin, BufferedImage gradeSkin) {
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();

        // 1. Pose le skin de grade en base (corps, bras, jambes)
        g.drawImage(gradeSkin, 0, 0, null);

        // 2. Écrase les 16 premières lignes avec la tête du joueur original
        g.drawImage(playerSkin.getSubimage(0, 0, 64, 16), 0, 0, null);

        g.dispose();
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // RÉCUPÉRATION DU SKIN JOUEUR (API Mojang)
    // ─────────────────────────────────────────────────────────────

    private BufferedImage fetchPlayerSkinFromMojang(UUID uuid) {
        try {
            String textureValue = fetchRawTextureValue(uuid);
            if (textureValue == null) return null;

            // Décode le payload base64 → JSON
            String textureJson = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);

            // Extrait l'URL du PNG
            String skinUrl = extractSkinUrl(textureJson);
            if (skinUrl == null) {
                log.warning("Aucune URL skin dans les textures de " + uuid);
                return null;
            }

            return downloadImage(skinUrl);
        } catch (Exception e) {
            log.warning("fetchPlayerSkinFromMojang(" + uuid + ") : " + e.getMessage());
            return null;
        }
    }

    /**
     * Récupère la valeur base64 brute de la propriété "textures" du profil Mojang.
     */
    private String fetchRawTextureValue(UUID uuid) {
        try {
            String uuidNoDash = uuid.toString().replace("-", "");
            String json = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDash);
            if (json == null) return null;
            return extractTextureValue(json);
        } catch (Exception e) {
            log.warning("fetchRawTextureValue(" + uuid + ") : " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES IMAGE
    // ─────────────────────────────────────────────────────────────

    /** Redimensionne l'image si elle n'est pas déjà à la bonne taille (nearest-neighbor). */
    private BufferedImage ensureSize(BufferedImage img, int w, int h) {
        if (img.getWidth() == w && img.getHeight() == h) return img;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Encode un BufferedImage PNG en base64. */
    private String imageToBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Construit le payload JSON de texture attendu par SkinsRestorer.
     * Format : base64({ "textures": { "SKIN": { "url": "data:image/png;base64,<B64>" } } })
     */
    private String buildTexturePayload(String base64Png) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64," + base64Png + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES HTTP / JSON (sans librairie externe)
    // ─────────────────────────────────────────────────────────────

    private String httpGet(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Site23Skin/1.0");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warning("HTTP GET échoué (" + urlStr + ") : " + e.getMessage());
            return null;
        }
    }

    private BufferedImage downloadImage(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Site23Skin/1.0");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream is = conn.getInputStream()) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            log.warning("downloadImage échoué (" + urlStr + ") : " + e.getMessage());
            return null;
        }
    }

    /**
     * Extrait la valeur base64 de la propriété "textures" depuis le JSON de profil Mojang.
     * Exemple JSON : {"properties":[{"name":"textures","value":"eyJ0...","signature":"..."}]}
     */
    private String extractTextureValue(String profileJson) {
        int idx = profileJson.indexOf("\"name\":\"textures\"");
        if (idx == -1) return null;
        int valIdx = profileJson.indexOf("\"value\":\"", idx);
        if (valIdx == -1) return null;
        valIdx += 9; // saute : "value":"
        int end = profileJson.indexOf("\"", valIdx);
        if (end == -1) return null;
        return profileJson.substring(valIdx, end);
    }

    /**
     * Extrait l'URL du PNG skin depuis le JSON de texture décodé.
     * Exemple JSON : {"textures":{"SKIN":{"url":"https://textures.minecraft.net/..."}}}
     */
    private String extractSkinUrl(String textureJson) {
        int idx = textureJson.indexOf("\"SKIN\"");
        if (idx == -1) return null;
        int urlIdx = textureJson.indexOf("\"url\":\"", idx);
        if (urlIdx == -1) return null;
        urlIdx += 7; // saute : "url":"
        int end = textureJson.indexOf("\"", urlIdx);
        if (end == -1) return null;
        return textureJson.substring(urlIdx, end);
    }
}
