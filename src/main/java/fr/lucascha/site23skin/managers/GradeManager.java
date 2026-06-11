package fr.lucascha.site23skin.managers;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Charge et expose la liste des grades depuis config.yml.
 */
public class GradeManager {

    private final Site23SkinPlugin plugin;
    private final Map<String, GradeData> grades = new LinkedHashMap<>();

    public GradeManager(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadGrades() {
        grades.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("grades");
        if (section == null) {
            plugin.getLogger().warning("Aucune section 'grades' trouvée dans config.yml !");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(key);
            if (g == null) continue;

            String display   = g.getString("display",   key);
            String dept      = g.getString("department","inconnu");
            String skinFile  = g.getString("skin-file", key + ".png");

            grades.put(key.toLowerCase(), new GradeData(key.toLowerCase(), display, dept, skinFile));
        }

        plugin.getLogger().info(grades.size() + " grades chargés depuis config.yml");
    }

    public GradeData getGrade(String id)   { return grades.get(id.toLowerCase()); }
    public boolean   hasGrade(String id)   { return grades.containsKey(id.toLowerCase()); }
    public Set<String> getAllIds()         { return Collections.unmodifiableSet(grades.keySet()); }
    public int getGradeCount()             { return grades.size(); }
}
