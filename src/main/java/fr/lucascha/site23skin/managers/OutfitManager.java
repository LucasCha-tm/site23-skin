package fr.lucascha.site23skin.managers;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.OutfitData;
import fr.lucascha.site23skin.models.OutfitData.SlotItem;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Charge les fichiers outfits/<gradeId>.yml depuis le dossier de données du plugin.
 * Si le fichier n'existe pas sur disque, tente de le copier depuis les ressources internes.
 */
public class OutfitManager {

    private final Site23SkinPlugin plugin;
    private final Logger log;
    private final Map<String, OutfitData> outfits = new HashMap<>();

    public OutfitManager(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ─── CHARGEMENT ────────────────────────────────────────────────────────────

    public void loadAll() {
        outfits.clear();

        // Copie les fichiers intégrés s'ils n'existent pas encore sur disque
        File outfitsDir = new File(plugin.getDataFolder(), "outfits");
        if (!outfitsDir.exists()) outfitsDir.mkdirs();

        // Liste les ressources embarquées (noms connus)
        String[] known = {
            "recrue","os","oss","ods","major","lieutenant","capitaine","general",
            "stagiaire_sc","scientifique","scientifique_experimente","administrateur_scientifique",
            "junior_manager","manager","site_assistant_manager","site_manager",
            "site_senior_manager","site_director",
            "technicien","technicien_confirme",
            "stagiaire_med","docteur","medecin","chirurgien","responsable_medical",
            "ic_recrue","soldat","caporal","sergeant","ic_major","ic_lieutenant",
            "ic_capitaine","ic_general",
            "classe_d","classe_d_jaune","classe_d_blanc","classe_d_rouge","classe_d_noir"
        };

        for (String id : known) {
            File f = new File(outfitsDir, id + ".yml");
            if (!f.exists()) {
                try {
                    plugin.saveResource("outfits/" + id + ".yml", false);
                } catch (Exception ignored) { /* pas de ressource interne */ }
            }
        }

        // Charge tous les YML présents dans outfits/
        File[] files = outfitsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("Aucun fichier trouvé dans outfits/ !");
            return;
        }

        for (File f : files) {
            String gradeId = f.getName().replace(".yml", "").toLowerCase();
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                OutfitData outfit = parse(gradeId, cfg);
                outfits.put(gradeId, outfit);
            } catch (Exception e) {
                log.warning("Erreur lecture outfits/" + f.getName() + " : " + e.getMessage());
            }
        }
        log.info(outfits.size() + " tenues chargées depuis outfits/");
    }

    // ─── ACCÈS ─────────────────────────────────────────────────────────────────

    public OutfitData getOutfit(String gradeId) {
        return outfits.get(gradeId.toLowerCase());
    }

    public boolean hasOutfit(String gradeId) {
        return outfits.containsKey(gradeId.toLowerCase());
    }

    public int getCount() { return outfits.size(); }

    // ─── PARSING ───────────────────────────────────────────────────────────────

    private OutfitData parse(String gradeId, YamlConfiguration cfg) {
        return new OutfitData(
            gradeId,
            parseSlot(cfg, "helmet"),
            parseSlot(cfg, "chestplate"),
            parseSlot(cfg, "leggings"),
            parseSlot(cfg, "boots"),
            parseSlot(cfg, "mainhand"),
            parseSlot(cfg, "offhand")
        );
    }

    private SlotItem parseSlot(YamlConfiguration cfg, String slot) {
        String path = slot + ".";

        String matName = cfg.getString(path + "material", "AIR").toUpperCase();
        Material mat;
        try { mat = Material.valueOf(matName); }
        catch (IllegalArgumentException e) {
            log.warning("Matériau inconnu '" + matName + "' dans slot " + slot + " — remplacé par AIR");
            mat = Material.AIR;
        }

        // Couleur cuir (optionnelle)
        Color color = null;
        if (cfg.contains(path + "color")) {
            int r = cfg.getInt(path + "color.r", 255);
            int g = cfg.getInt(path + "color.g", 255);
            int b = cfg.getInt(path + "color.b", 255);
            color = Color.fromRGB(r, g, b);
        }

        boolean unbreakable    = cfg.getBoolean(path + "unbreakable", false);
        boolean hideFlags      = cfg.getBoolean(path + "hide-flags",  false);
        int     customModelData= cfg.getInt(path + "custom-model-data", 0);

        // Enchantements
        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        List<String> enchList = cfg.getStringList(path + "enchants");
        for (String entry : enchList) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                Enchantment ench = Enchantment.getByName(parts[0].toUpperCase());
                if (ench == null) { log.warning("Enchantement inconnu : " + parts[0]); continue; }
                enchants.put(ench, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                log.warning("Niveau invalide pour enchantement : " + entry);
            }
        }

        return new SlotItem(mat, color, unbreakable, hideFlags, customModelData, enchants);
    }
}
