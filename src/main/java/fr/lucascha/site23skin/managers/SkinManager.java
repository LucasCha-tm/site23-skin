package fr.lucascha.site23skin.managers;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinApplier;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
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
 * Gère la fusion des skins et leur application via SkinsRestorer API.
 *
 * LOGIQUE DE FUSION (skin 64x64) :
 *   y = 0..15  → tête + chapeau  → conservé depuis le skin Mojang du joueur
 *   y = 16..63 → corps/bras/jambes → remplacé par le skin de grade
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
     */
    public void applyGradeSkin(Player player, GradeData grade, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    success = doApply(player, grade);
                } catch (Exception e) {
                    log.severe("Erreur applyGradeSkin(" + player.getName() + ") : " + e.getMessage());
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
     * Restaure le skin Mojang original du joueur.
     */
    public void restoreOriginalSkin(Player player, Runnable onDone) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    SkinsRestorer sr = SkinsRestorerProvider.get();
                    // Supprime le skin custom → SR remet le skin Mojang du joueur
                    sr.getPlayerStorage().removeSkinIdOfPlayer(player.getUniqueId());
                    sr.getSkinApplier(Player.class).applySkin(player);
                    log.info("Skin restauré pour " + player.getName());
                } catch (Exception e) {
                    log.severe("Erreur restoreOriginalSkin(" + player.getName() + ") : " + e.getMessage());
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

        // 1) Charge le PNG du grade
        File skinFile = new File(plugin.getDataFolder(), "skins/" + grade.getSkinFile());
        if (!skinFile.exists()) {
            log.warning("Fichier skin introuvable : skins/" + grade.getSkinFile()
                    + " → Place le PNG dans plugins/Site23Skin/skins/");
            return false;
        }
        BufferedImage gradeSkin = ImageIO.read(skinFile);
        if (gradeSkin == null) {
            log.warning("Impossible de lire l'image : " + skinFile.getName());
            return false;
        }
        gradeSkin = ensureSize(gradeSkin, 64, 64);

        // 2) Récupère le skin Mojang actuel du joueur
        //    On utilise l'API Bukkit Paper (PlayerProfile) pour lire la texture,
        //    ce qui ne nécessite pas d'appel HTTP supplémentaire.
        BufferedImage playerSkin = fetchPlayerSkinViaBukkit(player);
        if (playerSkin == null) {
            // Fallback : appel direct à l'API Mojang
            playerSkin = fetchPlayerSkinFromMojang(player.getUniqueId());
        }

        if (playerSkin == null) {
            log.warning("Impossible de récupérer le skin de " + player.getName()
                    + " — le skin de grade sera utilisé intégralement.");
            playerSkin = gradeSkin;
        } else {
            playerSkin = ensureSize(playerSkin, 64, 64);
        }

        // 3) Sauvegarde la texture originale pour restauration ultérieure
        saveOriginalSkinIfNeeded(player);

        // 4) Fusion : tête+chapeau du joueur (y 0-15) + corps du grade (y 16-63)
        BufferedImage merged = mergeSkins(playerSkin, gradeSkin);

        // 5) Convertit en PNG base64
        String base64Png = imageToBase64(merged);

        // 6) Crée le payload de texture Minecraft
        //    Format : base64( {"textures":{"SKIN":{"url":"data:image/png;base64,<B64>"}}} )
        String textureValue = buildTextureValue(base64Png);

        // 7) Crée la SkinProperty (value=texture, signature="" — OK pour custom skins SR)
        SkinProperty skinProp = SkinProperty.of(textureValue, "");

        // 8) Enregistre le skin custom dans SR et l'applique
        SkinsRestorer sr        = SkinsRestorerProvider.get();
        SkinStorage   ss        = sr.getSkinStorage();
        PlayerStorage ps        = sr.getPlayerStorage();
        SkinApplier<Player> sa  = sr.getSkinApplier(Player.class);

        // Identifiant unique pour ce skin fusionné (par joueur)
        String skinName = "site23_" + player.getUniqueId().toString().replace("-", "");

        // Stocke le skin custom
        ss.setCustomSkinData(skinName, skinProp);

        // Associe ce skin au joueur
        ps.setSkinIdOfPlayer(player.getUniqueId(), SkinIdentifier.ofCustom(skinName));

        // Applique immédiatement
        sa.applySkin(player);

        log.info("✔ Skin appliqué à " + player.getName() + " (grade : " + grade.getDisplayName() + ")");
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // FUSION D'IMAGES
    // ─────────────────────────────────────────────────────────────

    /**
     * Fusionne deux skins 64x64 :
     *   - Base   : skin de grade (tout le corps)
     *   - Dessus : lignes 0-15 du skin joueur (tête + overlay chapeau)
     */
    private BufferedImage mergeSkins(BufferedImage playerSkin, BufferedImage gradeSkin) {
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(gradeSkin, 0, 0, null);                               // corps du grade
        g.drawImage(playerSkin.getSubimage(0, 0, 64, 16), 0, 0, null);  // tête du joueur
        g.dispose();
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // RÉCUPÉRATION DU SKIN JOUEUR
    // ─────────────────────────────────────────────────────────────

    /**
     * Récupère le skin via l'API Bukkit/Paper (PlayerProfile).
     * Ne nécessite pas d'appel HTTP — utilise les données déjà chargées par le serveur.
     */
    private BufferedImage fetchPlayerSkinViaBukkit(Player player) {
        try {
            var profile = player.getPlayerProfile();
            PlayerTextures textures = profile.getTextures();
            java.net.URL skinUrl = textures.getSkin();
            if (skinUrl == null) return null;
            return downloadImage(skinUrl.toString());
        } catch (Exception e) {
            log.fine("fetchPlayerSkinViaBukkit(" + player.getName() + ") : " + e.getMessage());
            return null;
        }
    }

    /**
     * Fallback : récupère le skin depuis l'API sessionserver Mojang.
     */
    private BufferedImage fetchPlayerSkinFromMojang(UUID uuid) {
        try {
            String raw = fetchRawTextureValue(uuid);
            if (raw == null) return null;
            String textureJson = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            String skinUrl = extractSkinUrl(textureJson);
            if (skinUrl == null) return null;
            return downloadImage(skinUrl);
        } catch (Exception e) {
            log.warning("fetchPlayerSkinFromMojang(" + uuid + ") : " + e.getMessage());
            return null;
        }
    }

    /**
     * Sauvegarde la valeur de texture originale du joueur si pas encore fait.
     */
    private void saveOriginalSkinIfNeeded(Player player) {
        PlayerDataManager dm = plugin.getPlayerDataManager();
        if (dm.hasOriginalSkin(player.getUniqueId())) return;
        try {
            String raw = fetchRawTextureValue(player.getUniqueId());
            if (raw != null) dm.saveOriginalSkin(player.getUniqueId(), raw);
        } catch (Exception e) {
            log.fine("saveOriginalSkinIfNeeded(" + player.getName() + ") : " + e.getMessage());
        }
    }

    private String fetchRawTextureValue(UUID uuid) {
        try {
            String uuidNoDash = uuid.toString().replace("-", "");
            String json = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDash);
            if (json == null) return null;
            return extractTextureValue(json);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES IMAGE
    // ─────────────────────────────────────────────────────────────

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

    private String imageToBase64(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Construit la valeur de texture (base64 du JSON Minecraft).
     * Format : base64({ "textures": { "SKIN": { "url": "data:image/png;base64,<B64>" } } })
     */
    private String buildTextureValue(String base64Png) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64," + base64Png + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES HTTP / JSON
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
                byte[] buf = new byte[4096]; int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                return baos.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.fine("httpGet(" + urlStr + ") : " + e.getMessage());
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
            try (InputStream is = conn.getInputStream()) { return ImageIO.read(is); }
        } catch (Exception e) {
            log.fine("downloadImage(" + urlStr + ") : " + e.getMessage());
            return null;
        }
    }

    /** Extrait la valeur base64 "textures" du JSON de profil Mojang. */
    private String extractTextureValue(String profileJson) {
        int idx = profileJson.indexOf("\"name\":\"textures\"");
        if (idx == -1) return null;
        int vi = profileJson.indexOf("\"value\":\"", idx);
        if (vi == -1) return null;
        vi += 9;
        int end = profileJson.indexOf("\"", vi);
        return end == -1 ? null : profileJson.substring(vi, end);
    }

    /** Extrait l'URL du skin PNG depuis le JSON de texture décodé. */
    private String extractSkinUrl(String textureJson) {
        int idx = textureJson.indexOf("\"SKIN\"");
        if (idx == -1) return null;
        int ui = textureJson.indexOf("\"url\":\"", idx);
        if (ui == -1) return null;
        ui += 7;
        int end = textureJson.indexOf("\"", ui);
        return end == -1 ? null : textureJson.substring(ui, end);
    }
}
