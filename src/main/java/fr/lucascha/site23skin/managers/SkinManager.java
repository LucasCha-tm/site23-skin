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
 *
 * SIGNATURE : passage par MineSkin API pour obtenir une vraie paire
 * value + signature Mojang signée (sans ça, le client ignore le skin silencieusement).
 */
public class SkinManager {

    private final Site23SkinPlugin plugin;
    private final Logger log;

    // Cache : skinName → SkinProperty (value+signature) pour éviter les appels répétés à MineSkin
    private final java.util.Map<String, SkinProperty> skinCache = new java.util.concurrent.ConcurrentHashMap<>();

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
        BufferedImage playerSkin = fetchPlayerSkinViaBukkit(player);
        if (playerSkin == null) {
            playerSkin = fetchPlayerSkinFromMojang(player.getUniqueId());
        }

        if (playerSkin == null) {
            log.warning("Impossible de récupérer le skin de " + player.getName()
                    + " — le skin de grade sera utilisé intégralement.");
            playerSkin = gradeSkin;
        } else {
            playerSkin = ensureSize(playerSkin, 64, 64);
        }

        // 3) Fusion : tête+chapeau du joueur (y 0-15) + corps du grade (y 16-63)
        BufferedImage merged = mergeSkins(playerSkin, gradeSkin);

        // 4) Clé de cache unique : grade + UUID joueur (la tête change selon le joueur)
        String cacheKey = "site23_" + grade.getSkinFile().replace(".png", "")
                        + "_" + player.getUniqueId().toString().replace("-", "");

        // 5) Obtenir value+signature via MineSkin (avec cache)
        SkinProperty skinProp = skinCache.get(cacheKey);
        if (skinProp == null) {
            skinProp = uploadToMineSkin(merged, cacheKey);
            if (skinProp == null) {
                log.severe("Échec de l'upload MineSkin pour " + player.getName()
                        + " (grade: " + grade.getDisplayName() + ")");
                return false;
            }
            skinCache.put(cacheKey, skinProp);
            log.info("MineSkin : skin mis en cache pour " + cacheKey);
        } else {
            log.fine("Cache hit MineSkin pour " + cacheKey);
        }

        // 6) Enregistre dans SR et applique
        SkinsRestorer        sr = SkinsRestorerProvider.get();
        SkinStorage          ss = sr.getSkinStorage();
        PlayerStorage        ps = sr.getPlayerStorage();
        SkinApplier<Player>  sa = sr.getSkinApplier(Player.class);

        ss.setCustomSkinData(cacheKey, skinProp);
        ps.setSkinIdOfPlayer(player.getUniqueId(), SkinIdentifier.ofCustom(cacheKey));
        sa.applySkin(player);

        log.info("✔ Skin appliqué à " + player.getName() + " (grade : " + grade.getDisplayName() + ")");
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // MINESKIN API — upload → value + signature Mojang signée
    // ─────────────────────────────────────────────────────────────

    /**
     * Upload le skin PNG fusionné sur MineSkin et récupère la paire value+signature.
     * MineSkin génère une vraie signature Mojang acceptée par tous les clients.
     *
     * Doc API : https://rest.mineskin.org/docs
     */
    private SkinProperty uploadToMineSkin(BufferedImage image, String skinName) {
        try {
            // Convertit l'image en bytes PNG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] pngBytes = baos.toByteArray();

            // Prépare le multipart/form-data
            String boundary = "----Site23Boundary" + System.currentTimeMillis();
            URL url = new URL("https://api.mineskin.org/generate/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "Site23Skin/1.0 (contact: admin@site23)");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

                // Champ "file"
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                      .append(skinName).append(".png\"").append("\r\n");
                writer.append("Content-Type: image/png").append("\r\n");
                writer.append("\r\n");
                writer.flush();
                os.write(pngBytes);
                os.flush();
                writer.append("\r\n");

                // Champ "visibility" (0 = public, 1 = private)
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"visibility\"").append("\r\n");
                writer.append("\r\n").append("1").append("\r\n");

                // Ferme le boundary
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = streamToString(is);

            if (responseCode != 200) {
                log.warning("MineSkin HTTP " + responseCode + " : " + responseBody);
                return null;
            }

            // Parse le JSON de réponse (sans dépendance externe)
            String value     = extractJsonString(responseBody, "value");
            String signature = extractJsonString(responseBody, "signature");

            if (value == null || signature == null) {
                log.warning("MineSkin réponse invalide (value/signature manquants) : " + responseBody);
                return null;
            }

            return SkinProperty.of(value, signature);

        } catch (Exception e) {
            log.severe("uploadToMineSkin exception : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FUSION D'IMAGES
    // ─────────────────────────────────────────────────────────────

    /**
     * Fusionne deux skins 64x64 :
     *   - Base   : skin de grade (tout le corps)
     *   - Dessus : tête du joueur (y=0-15) SANS l'overlay casque (x=40-47, y=8-15)
     */
    private BufferedImage mergeSkins(BufferedImage playerSkin, BufferedImage gradeSkin) {
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();

        // 1) Corps complet du grade
        g.drawImage(gradeSkin, 0, 0, null);

        // 2) Bande y=0-15 du joueur (tête + métadonnées)
        g.drawImage(playerSkin.getSubimage(0, 0, 64, 16), 0, 0, null);

        // 3) Efface l'overlay casque (x=40-47, y=8-15) pour ne pas l'afficher
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(40, 8, 8, 8);
        g.setComposite(AlphaComposite.SrcOver);

        g.dispose();
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // RÉCUPÉRATION DU SKIN JOUEUR
    // ─────────────────────────────────────────────────────────────

    private BufferedImage fetchPlayerSkinViaBukkit(Player player) {
        try {
            PlayerTextures textures = player.getPlayerProfile().getTextures();
            java.net.URL skinUrl = textures.getSkin();
            if (skinUrl == null) return null;
            return downloadImage(skinUrl.toString());
        } catch (Exception e) {
            log.fine("fetchPlayerSkinViaBukkit(" + player.getName() + ") : " + e.getMessage());
            return null;
        }
    }

    private BufferedImage fetchPlayerSkinFromMojang(UUID uuid) {
        try {
            String uuidNoDash = uuid.toString().replace("-", "");
            String profileJson = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDash);
            if (profileJson == null) return null;
            String rawTexture = extractTextureValue(profileJson);
            if (rawTexture == null) return null;
            String textureJson = new String(Base64.getDecoder().decode(rawTexture), StandardCharsets.UTF_8);
            String skinUrl = extractSkinUrl(textureJson);
            if (skinUrl == null) return null;
            return downloadImage(skinUrl);
        } catch (Exception e) {
            log.warning("fetchPlayerSkinFromMojang(" + uuid + ") : " + e.getMessage());
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
            try (InputStream is = conn.getInputStream()) {
                return streamToString(is);
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

    private String streamToString(InputStream is) throws IOException {
        if (is == null) return "";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toString(StandardCharsets.UTF_8);
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

    /**
     * Extrait une valeur string d'un JSON simple (sans librairie).
     * Cherche "key":"valeur" et retourne valeur.
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            if (c == '\\' && idx + 1 < json.length()) {
                idx++;
                sb.append(json.charAt(idx));
            } else {
                sb.append(c);
            }
            idx++;
        }
        return sb.toString();
    }
}
