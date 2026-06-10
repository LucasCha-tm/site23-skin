package fr.scp.outfits.models;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente une tenue complète associée à un grade.
 */
public class OutfitData {

    private final String gradeId;
    private final String displayName;
    private final String department;

    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final ItemStack mainhand;
    private final ItemStack offhand;

    public OutfitData(String gradeId, String displayName, String department,
                      ItemStack helmet, ItemStack chestplate, ItemStack leggings,
                      ItemStack boots, ItemStack mainhand, ItemStack offhand) {
        this.gradeId = gradeId;
        this.displayName = displayName;
        this.department = department;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.mainhand = mainhand;
        this.offhand = offhand;
    }

    // =========================================
    // Getters
    // =========================================

    public String getGradeId() { return gradeId; }
    public String getDisplayName() { return displayName; }
    public String getDepartment() { return department; }

    public ItemStack getHelmet()     { return helmet != null ? helmet.clone() : null; }
    public ItemStack getChestplate() { return chestplate != null ? chestplate.clone() : null; }
    public ItemStack getLeggings()   { return leggings != null ? leggings.clone() : null; }
    public ItemStack getBoots()      { return boots != null ? boots.clone() : null; }
    public ItemStack getMainhand()   { return mainhand != null ? mainhand.clone() : null; }
    public ItemStack getOffhand()    { return offhand != null ? offhand.clone() : null; }

    // =========================================
    // Builder statique pour construire depuis la config
    // =========================================

    public static class Builder {
        private String gradeId;
        private String displayName;
        private String department;
        private ItemStack helmet;
        private ItemStack chestplate;
        private ItemStack leggings;
        private ItemStack boots;
        private ItemStack mainhand;
        private ItemStack offhand;

        public Builder gradeId(String id)       { this.gradeId = id; return this; }
        public Builder displayName(String name) { this.displayName = name; return this; }
        public Builder department(String dept)  { this.department = dept; return this; }
        public Builder helmet(ItemStack item)   { this.helmet = item; return this; }
        public Builder chestplate(ItemStack i)  { this.chestplate = i; return this; }
        public Builder leggings(ItemStack i)    { this.leggings = i; return this; }
        public Builder boots(ItemStack i)       { this.boots = i; return this; }
        public Builder mainhand(ItemStack i)    { this.mainhand = i; return this; }
        public Builder offhand(ItemStack i)     { this.offhand = i; return this; }

        public OutfitData build() {
            return new OutfitData(gradeId, displayName, department,
                    helmet, chestplate, leggings, boots, mainhand, offhand);
        }
    }

    // =========================================
    // Utilitaire pour construire un ItemStack depuis la config
    // =========================================

    public static ItemStack buildItem(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return null;

        String matName = section.getString("material", "AIR");
        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.AIR;
        }

        if (material == Material.AIR) return new ItemStack(Material.AIR);

        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Couleur cuir
        if (meta instanceof LeatherArmorMeta leatherMeta && section.contains("color")) {
            int r = section.getInt("color.r", 160);
            int g = section.getInt("color.g", 101);
            int b = section.getInt("color.b", 64);
            leatherMeta.setColor(Color.fromRGB(r, g, b));
        }

        // Incassable
        if (section.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        // Masquer les flags
        if (section.getBoolean("hide-flags", false)) {
            meta.addItemFlags(ItemFlag.values());
        }

        // Custom model data
        int cmd = section.getInt("custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        // Enchantements
        if (section.contains("enchants")) {
            for (String enchantStr : section.getStringList("enchants")) {
                String[] parts = enchantStr.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment enchant = Enchantment.getByName(parts[0].toUpperCase());
                        int level = Integer.parseInt(parts[1]);
                        if (enchant != null) {
                            meta.addEnchant(enchant, level, true);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }
}
