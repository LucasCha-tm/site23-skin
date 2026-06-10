package fr.scp.outfits.managers;

import fr.scp.outfits.SCPOutfitsPlugin;
import fr.scp.outfits.models.OutfitData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Gère le chargement des tenues depuis les fichiers YAML
 * et leur application/retrait sur les joueurs.
 */
public class OutfitManager {

    private final SCPOutfitsPlugin plugin;
    private final Logger log;
    private final Map<String, OutfitData> outfits = new HashMap<>();

    public OutfitManager(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // =========================================
    // Chargement
    // =========================================

    /**
     * Charge tous les fichiers .yml du dossier outfits/
     */
    public void loadAllOutfits() {
        outfits.clear();
        File outfitsFolder = new File(plugin.getDataFolder(), "outfits");

        if (!outfitsFolder.exists() || !outfitsFolder.isDirectory()) {
            log.warning("Dossier outfits introuvable : " + outfitsFolder.getPath());
            return;
        }

        File[] files = outfitsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("Aucun fichier de tenue trouvé dans outfits/");
            return;
        }

        for (File file : files) {
            String gradeId = file.getName().replace(".yml", "").toLowerCase();
            try {
                OutfitData data = loadOutfit(file, gradeId);
                if (data != null) {
                    outfits.put(gradeId, data);
                }
            } catch (Exception e) {
                log.severe("Erreur lors du chargement de " + file.getName() + " : " + e.getMessage());
            }
        }

        log.info(outfits.size() + " tenues chargées depuis outfits/");
    }

    /**
     * Charge un seul fichier de tenue
     */
    private OutfitData loadOutfit(File file, String gradeId) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String displayName = config.getString("display-name", gradeId);
        String department  = config.getString("department", "inconnu");

        OutfitData.Builder builder = new OutfitData.Builder()
                .gradeId(gradeId)
                .displayName(displayName)
                .department(department);

        // Charge chaque slot
        builder.helmet(OutfitData.buildItem(config.getConfigurationSection("helmet")));
        builder.chestplate(OutfitData.buildItem(config.getConfigurationSection("chestplate")));
        builder.leggings(OutfitData.buildItem(config.getConfigurationSection("leggings")));
        builder.boots(OutfitData.buildItem(config.getConfigurationSection("boots")));
        builder.mainhand(OutfitData.buildItem(config.getConfigurationSection("mainhand")));
        builder.offhand(OutfitData.buildItem(config.getConfigurationSection("offhand")));

        return builder.build();
    }

    // =========================================
    // Application / Retrait
    // =========================================

    /**
     * Applique la tenue du grade donné sur un joueur.
     * Retire d'abord l'ancienne tenue si elle existe.
     *
     * @param player  Le joueur cible
     * @param gradeId L'identifiant du grade (ex: "os", "recrue")
     * @return true si la tenue a été appliquée, false si le grade est inconnu
     */
    public boolean applyOutfit(Player player, String gradeId) {
        OutfitData outfit = outfits.get(gradeId.toLowerCase());
        if (outfit == null) {
            return false;
        }

        PlayerInventory inv = player.getInventory();

        // Applique chaque pièce (null = on ne touche pas au slot)
        if (outfit.getHelmet() != null)     inv.setHelmet(outfit.getHelmet());
        if (outfit.getChestplate() != null) inv.setChestplate(outfit.getChestplate());
        if (outfit.getLeggings() != null)   inv.setLeggings(outfit.getLeggings());
        if (outfit.getBoots() != null)      inv.setBoots(outfit.getBoots());

        player.updateInventory();
        return true;
    }

    /**
     * Retire l'armure (slots casque, plastron, jambières, bottes) du joueur.
     * Ne touche pas à la main ni à l'inventaire principal.
     */
    public void removeOutfit(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        player.updateInventory();
    }

    // =========================================
    // Getters utilitaires
    // =========================================

    public OutfitData getOutfit(String gradeId) {
        return outfits.get(gradeId.toLowerCase());
    }

    public boolean hasOutfit(String gradeId) {
        return outfits.containsKey(gradeId.toLowerCase());
    }

    public Set<String> getAllGradeIds() {
        return Collections.unmodifiableSet(outfits.keySet());
    }

    public int getOutfitCount() {
        return outfits.size();
    }

    /**
     * Retourne tous les grades d'un département donné
     */
    public List<String> getGradesByDepartment(String department) {
        List<String> result = new ArrayList<>();
        for (OutfitData outfit : outfits.values()) {
            if (outfit.getDepartment().equalsIgnoreCase(department)) {
                result.add(outfit.getGradeId());
            }
        }
        return result;
    }
}
