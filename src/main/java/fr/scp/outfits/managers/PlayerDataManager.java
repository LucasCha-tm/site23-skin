package fr.scp.outfits.managers;

import fr.scp.outfits.SCPOutfitsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Sauvegarde et charge le grade actuel de chaque joueur
 * dans un fichier players.yml pour la persistance.
 */
public class PlayerDataManager {

    private final SCPOutfitsPlugin plugin;
    private final Logger log;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // Cache en mémoire : UUID -> gradeId
    private final Map<UUID, String> playerGrades = new HashMap<>();

    public PlayerDataManager(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "players.yml");
        loadData();
    }

    // =========================================
    // Chargement / Sauvegarde
    // =========================================

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                log.severe("Impossible de créer players.yml : " + e.getMessage());
                return;
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String grade = dataConfig.getString("players." + uuidStr + ".grade");
                    if (grade != null && !grade.isEmpty()) {
                        playerGrades.put(uuid, grade);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        log.info(playerGrades.size() + " grades de joueurs chargés.");
    }

    public void saveAll() {
        if (dataConfig == null) return;

        for (Map.Entry<UUID, String> entry : playerGrades.entrySet()) {
            String path = "players." + entry.getKey().toString();
            dataConfig.set(path + ".grade", entry.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            log.severe("Impossible de sauvegarder players.yml : " + e.getMessage());
        }
    }

    private void saveOne(UUID uuid, String grade) {
        if (dataConfig == null) return;
        String path = "players." + uuid.toString();
        dataConfig.set(path + ".grade", grade);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            log.severe("Impossible de sauvegarder players.yml : " + e.getMessage());
        }
    }

    // =========================================
    // API
    // =========================================

    /**
     * Définit le grade d'un joueur et sauvegarde immédiatement.
     */
    public void setGrade(UUID uuid, String gradeId) {
        playerGrades.put(uuid, gradeId);
        saveOne(uuid, gradeId);
    }

    /**
     * Supprime le grade d'un joueur (ex: après /removeoutfit).
     */
    public void removeGrade(UUID uuid) {
        playerGrades.remove(uuid);
        if (dataConfig != null) {
            dataConfig.set("players." + uuid.toString(), null);
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                log.severe("Impossible de sauvegarder players.yml : " + e.getMessage());
            }
        }
    }

    /**
     * Retourne le grade actuel d'un joueur, ou null s'il n'en a pas.
     */
    public String getGrade(UUID uuid) {
        return playerGrades.get(uuid);
    }

    public boolean hasGrade(UUID uuid) {
        return playerGrades.containsKey(uuid);
    }
}
