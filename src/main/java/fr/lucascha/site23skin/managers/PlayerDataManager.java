package fr.lucascha.site23skin.managers;

import fr.lucascha.site23skin.Site23SkinPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persiste le grade actuel et le skin original de chaque joueur
 * dans players.yml.
 */
public class PlayerDataManager {

    private final Site23SkinPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    // UUID -> gradeId actuellement appliqué
    private final Map<UUID, String> playerGrades = new HashMap<>();
    // UUID -> valeur du skin original (texture Mojang base64) avant modification
    private final Map<UUID, String> originalSkins = new HashMap<>();

    public PlayerDataManager(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    // ── Persistence ──────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Impossible de créer players.yml"); return; }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String grade = data.getString("players." + uuidStr + ".grade");
                    String orig  = data.getString("players." + uuidStr + ".original-skin");
                    if (grade != null) playerGrades.put(uuid, grade);
                    if (orig  != null) originalSkins.put(uuid, orig);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info(playerGrades.size() + " joueurs chargés depuis players.yml");
    }

    public void saveAll() {
        if (data == null) return;
        for (Map.Entry<UUID, String> e : playerGrades.entrySet()) {
            String path = "players." + e.getKey();
            data.set(path + ".grade", e.getValue());
        }
        for (Map.Entry<UUID, String> e : originalSkins.entrySet()) {
            data.set("players." + e.getKey() + ".original-skin", e.getValue());
        }
        save();
    }

    private void save() {
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Impossible de sauvegarder players.yml : " + e.getMessage()); }
    }

    // ── API ──────────────────────────────────────────────────────

    public void setGrade(UUID uuid, String gradeId) {
        playerGrades.put(uuid, gradeId);
        data.set("players." + uuid + ".grade", gradeId);
        save();
    }

    public void removeGrade(UUID uuid) {
        playerGrades.remove(uuid);
        data.set("players." + uuid + ".grade", null);
        save();
    }

    public String getGrade(UUID uuid)     { return playerGrades.get(uuid); }
    public boolean hasGrade(UUID uuid)    { return playerGrades.containsKey(uuid); }

    /** Sauvegarde la valeur de texture originale du joueur (avant qu'on la modifie). */
    public void saveOriginalSkin(UUID uuid, String textureValue) {
        if (originalSkins.containsKey(uuid)) return; // Ne pas écraser l'original réel
        originalSkins.put(uuid, textureValue);
        data.set("players." + uuid + ".original-skin", textureValue);
        save();
    }

    public String getOriginalSkin(UUID uuid)  { return originalSkins.get(uuid); }
    public boolean hasOriginalSkin(UUID uuid) { return originalSkins.containsKey(uuid); }

    public void clearOriginalSkin(UUID uuid) {
        originalSkins.remove(uuid);
        data.set("players." + uuid + ".original-skin", null);
        save();
    }
}
