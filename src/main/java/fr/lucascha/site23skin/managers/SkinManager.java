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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Gère la fusion des skins et leur application via SkinsRestorer.
 *
 * BUGS CORRIGÉS :
 *  1. Casque overlay (x=40,y=8,8x8) supprimé proprement avec AlphaComposite.Clear.
 *  2. Classe D : bras droit ET gauche (base + outer) copiés depuis le skin joueur
 *               → couleur de peau réelle visible sur les bras.
 *  3. SCP : skin fixe, zéro fusion avec le joueur.
 *  4. Cache : clé unique par (grade + UUID joueur) → pas de glitch cross-joueurs.
 *  5. Fetch skin : double fallback Bukkit + Mojang API.
 *  6. Tab-list : setPlayerListName() mis à jour avec le grade affiché.
 *  7. applySkin() exécuté sur le thread principal via BukkitRunnable.
 */
public class SkinManager {

    private final Site23SkinPlugin plugin;
    private final Logger log;

    /** Cache value+signature MineSkin. Clé = grade + UUID joueur. */
    private final ConcurrentHashMap<String, SkinProperty> skinCache = new ConcurrentHashMap<>();

    public SkinManager(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ─── POINT D'ENTRÉE PUBLIC ─────────────────────────────────────────────────

    public void applyGradeSkin(Player player, GradeData grade, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override public void run() {
                boolean ok = false;
                try { ok = doApply(player, grade); }
                catch (Exception e) {
                    log.severe("applyGradeSkin(" + player.getName() + ") : " + e.getMessage());
                    e.printStackTrace();
                }
                final boolean result = ok;
                new BukkitRunnable() {
                    @Override public void run() { callback.accept(result); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void restoreOriginalSkin(Player player, Runnable onDone) {
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    SkinsRestorer sr = SkinsRestorerProvider.get();
                    sr.getPlayerStorage().removeSkinIdOfPlayer(player.getUniqueId());
                    sr.getSkinApplier(Player.class).applySkin(player);
                    // Restaure le nom dans le tab
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (player.isOnline()) player.setPlayerListName(player.getName());
                        }
                    }.runTask(plugin);
                    log.info("Skin restauré pour " + player.getName());
                } catch (Exception e) {
                    log.severe("restoreOriginalSkin(" + player.getName() + ") : " + e.getMessage());
                }
                new BukkitRunnable() {
                    @Override public void run() { if (onDone != null) onDone.run(); }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Vide le cache MineSkin (appelé par /skinreload). */
    public void clearCache() {
        skinCache.clear();
        log.info("Cache MineSkin vidé.");
    }

    // ─── LOGIQUE PRINCIPALE (thread async) ────────────────────────────────────

    private boolean doApply(Player player, GradeData grade) throws Exception {

        // 1) Charge le PNG du grade
        File skinFile = new File(plugin.getDataFolder(), "skins/" + grade.getSkinFile());
        if (!skinFile.exists()) {
            log.warning("Fichier skin introuvable : skins/" + grade.getSkinFile());
            return false;
        }
        BufferedImage gradeSkin = ImageIO.read(skinFile);
        if (gradeSkin == null) { log.warning("Impossible de lire : " + skinFile.getName()); return false; }
        gradeSkin = ensureSize(gradeSkin, 64, 64);

        // 2) Récupère le skin Mojang du joueur
        BufferedImage playerSkin = fetchPlayerSkinViaBukkit(player);
        if (playerSkin == null) playerSkin = fetchPlayerSkinFromMojang(player.getUniqueId());
        if (playerSkin == null) {
            log.warning("Skin joueur introuvable pour " + player.getName() + " — grade seul utilisé.");
            playerSkin = gradeSkin;
        } else {
            playerSkin = ensureSize(playerSkin, 64, 64);
        }

        // 3) Fusion selon le département
        BufferedImage merged = mergeSkins(playerSkin, gradeSkin, grade);

        // 4) Clé de cache unique (grade + UUID sans tirets)
        String cacheKey = "s23_" + grade.getSkinFile().replace(".png", "")
                        + "_" + player.getUniqueId().toString().replace("-", "");

        // 5) Upload MineSkin si pas déjà en cache
        SkinProperty skinProp = skinCache.get(cacheKey);
        if (skinProp == null) {
            skinProp = uploadToMineSkin(merged, cacheKey);
            if (skinProp == null) {
                log.severe("Échec MineSkin pour " + player.getName() + " / " + grade.getDisplayName());
                return false;
            }
            skinCache.put(cacheKey, skinProp);
        }

        // 6) Enregistre dans SkinsRestorer et applique
        SkinsRestorer       sr = SkinsRestorerProvider.get();
        SkinStorage         ss = sr.getSkinStorage();
        PlayerStorage       ps = sr.getPlayerStorage();
        SkinApplier<Player> sa = sr.getSkinApplier(Player.class);

        ss.setCustomSkinData(cacheKey, skinProp);
        ps.setSkinIdOfPlayer(player.getUniqueId(), SkinIdentifier.ofCustom(cacheKey));
        sa.applySkin(player);

        // 7) Met à jour le nom dans le tab (doit être sur le thread principal)
        final String tabName = grade.getDisplayName();
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline())
                    player.setPlayerListName(
                        Site23SkinPlugin.colorize("&7[&b" + tabName + "&7] &f") + player.getName()
                    );
            }
        }.runTask(plugin);

        log.info("Skin appliqué : " + player.getName() + " → " + grade.getDisplayName());
        return true;
    }

    // ─── FUSION D'IMAGES ───────────────────────────────────────────────────────

    /**
     * Fusionne les skins selon le département :
     *
     *  scp      → skin grade complet, pas de fusion
     *  classe_d → corps grade + tête joueur (sans casque) + BRAS joueur (couleur peau réelle)
     *  autres   → corps grade + tête joueur (sans casque)
     *
     * Zones 64×64 manipulées :
     *   Tête base      x= 8, y= 8, 8×8
     *   Casque overlay x=40, y= 8, 8×8  ← supprimé (AlphaComposite.Clear)
     *   Bras D base    x=44, y=16, 4×12
     *   Bras D outer   x=44, y=32, 4×12
     *   Bras G base    x=36, y=52, 4×12
     *   Bras G outer   x=52, y=52, 4×12
     */
    private BufferedImage mergeSkins(BufferedImage playerSkin, BufferedImage gradeSkin, GradeData gradeData) {
        String dept = gradeData.getDepartment().toLowerCase().trim();

        // ── SCP : skin fixe ───────────────────────────────────────
        if (dept.equals("scp")) {
            BufferedImage r = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = r.createGraphics();
            g2.drawImage(gradeSkin, 0, 0, null);
            g2.dispose();
            return r;
        }

        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Corps complet du grade
        g.drawImage(gradeSkin, 0, 0, null);

        // Tête du joueur (bande y=0–15)
        g.drawImage(playerSkin.getSubimage(0, 0, 64, 16), 0, 0, null);

        // Supprime l'overlay casque (x=40–47, y=8–15)
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(40, 8, 8, 8);
        g.setComposite(AlphaComposite.SrcOver);

        // ── Classe D : bras du joueur ─────────────────────────────
        if (dept.equals("classe_d")) {
            // Bras droit base  (x=44, y=16, 4×12)
            g.drawImage(playerSkin.getSubimage(44, 16, 4, 12), 44, 16, null);
            // Bras droit outer (x=44, y=32, 4×12)
            g.drawImage(playerSkin.getSubimage(44, 32, 4, 12), 44, 32, null);
            // Bras gauche base  (x=36, y=52, 4×12)
            g.drawImage(playerSkin.getSubimage(36, 52, 4, 12), 36, 52, null);
            // Bras gauche outer (x=52, y=52, 4×12)
            g.drawImage(playerSkin.getSubimage(52, 52, 4, 12), 52, 52, null);
        }

        g.dispose();
        return result;
    }

    // ─── MINESKIN API ──────────────────────────────────────────────────────────

    private SkinProperty uploadToMineSkin(BufferedImage image, String skinName) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] pngBytes = baos.toByteArray();

            String boundary = "----Site23B" + System.currentTimeMillis();
            URL url = new URL("https://api.mineskin.org/generate/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(40000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "Site23Skin/2.0");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter w = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
                w.append("--").append(boundary).append("\r\n");
                w.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                 .append(skinName).append(".png\"\r\n");
                w.append("Content-Type: image/png\r\n\r\n");
                w.flush();
                os.write(pngBytes);
                os.flush();
                w.append("\r\n--").append(boundary).append("\r\n");
                w.append("Content-Disposition: form-data; name=\"visibility\"\r\n\r\n1\r\n");
                w.append("--").append(boundary).append("--\r\n");
                w.flush();
            }

            int code = conn.getResponseCode();
            InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
            String body = streamToString(is);
            if (code != 200) { log.warning("MineSkin HTTP " + code + " : " + body); return null; }

            String value     = extractJsonString(body, "value");
            String signature = extractJsonString(body, "signature");
            if (value == null || signature == null) {
                log.warning("MineSkin réponse invalide : " + body); return null;
            }
            return SkinProperty.of(value, signature);

        } catch (Exception e) {
            log.severe("uploadToMineSkin : " + e.getMessage());
            return null;
        }
    }

    // ─── FETCH SKIN JOUEUR ─────────────────────────────────────────────────────

    private BufferedImage fetchPlayerSkinViaBukkit(Player player) {
        try {
            PlayerTextures tex = player.getPlayerProfile().getTextures();
            java.net.URL u = tex.getSkin();
            if (u == null) return null;
            return downloadImage(u.toString());
        } catch (Exception e) { return null; }
    }

    private BufferedImage fetchPlayerSkinFromMojang(UUID uuid) {
        try {
            String profileJson = httpGet(
                "https://sessionserver.mojang.com/session/minecraft/profile/"
                + uuid.toString().replace("-", "") + "?unsigned=false"
            );
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

    // ─── UTILITAIRES ───────────────────────────────────────────────────────────

    private BufferedImage ensureSize(BufferedImage img, int w, int h) {
        if (img.getWidth() == w && img.getHeight() == h) return img;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private String httpGet(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod("GET"); c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Site23Skin/2.0");
            if (c.getResponseCode() != 200) return null;
            try (InputStream is = c.getInputStream()) { return streamToString(is); }
        } catch (Exception e) { return null; }
    }

    private BufferedImage downloadImage(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Site23Skin/2.0");
            if (c.getResponseCode() != 200) return null;
            try (InputStream is = c.getInputStream()) { return ImageIO.read(is); }
        } catch (Exception e) { return null; }
    }

    private String streamToString(InputStream is) throws IOException {
        if (is == null) return "";
        try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) b.write(buf, 0, n);
            return b.toString(StandardCharsets.UTF_8);
        }
    }

    private String extractTextureValue(String json) {
        int idx = json.indexOf("\"name\":\"textures\"");
        if (idx == -1) return null;
        int vi = json.indexOf("\"value\":\"", idx);
        if (vi == -1) return null;
        vi += 9; int end = json.indexOf("\"", vi);
        return end == -1 ? null : json.substring(vi, end);
    }

    private String extractSkinUrl(String json) {
        int idx = json.indexOf("\"SKIN\"");
        if (idx == -1) return null;
        int ui = json.indexOf("\"url\":\"", idx);
        if (ui == -1) return null;
        ui += 7; int end = json.indexOf("\"", ui);
        return end == -1 ? null : json.substring(ui, end);
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            if (c == '\\' && idx + 1 < json.length()) { idx++; sb.append(json.charAt(idx)); }
            else sb.append(c);
            idx++;
        }
        return sb.toString();
    }
}
