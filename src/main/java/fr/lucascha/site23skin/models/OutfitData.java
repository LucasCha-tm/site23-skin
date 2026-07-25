package fr.lucascha.site23skin.models;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Map;

/**
 * Représente une tenue complète (6 slots : helmet, chestplate, leggings, boots, mainhand, offhand).
 * Chaque slot est un ItemStack prêt à être équipé.
 */
public class OutfitData {

    public static class SlotItem {
        public final Material material;
        public final Color leatherColor;   // null si pas d'armure en cuir
        public final boolean unbreakable;
        public final boolean hideFlags;
        public final int customModelData;
        public final Map<Enchantment, Integer> enchants;

        public SlotItem(Material material, Color leatherColor, boolean unbreakable,
                        boolean hideFlags, int customModelData, Map<Enchantment, Integer> enchants) {
            this.material       = material;
            this.leatherColor   = leatherColor;
            this.unbreakable    = unbreakable;
            this.hideFlags      = hideFlags;
            this.customModelData = customModelData;
            this.enchants       = enchants;
        }

        /** Construit l'ItemStack final à équiper. */
        public ItemStack build() {
            if (material == null || material == Material.AIR) return new ItemStack(Material.AIR);

            ItemStack item = new ItemStack(material);
            ItemMeta  meta = item.getItemMeta();
            if (meta == null) return item;

            if (leatherColor != null && meta instanceof LeatherArmorMeta lam) {
                lam.setColor(leatherColor);
            }
            meta.setUnbreakable(unbreakable);
            if (hideFlags) meta.addItemFlags(ItemFlag.values());
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            enchants.forEach((ench, lvl) -> meta.addEnchant(ench, lvl, true));

            item.setItemMeta(meta);
            return item;
        }
    }

    private final String gradeId;
    private final SlotItem helmet;
    private final SlotItem chestplate;
    private final SlotItem leggings;
    private final SlotItem boots;
    private final SlotItem mainhand;
    private final SlotItem offhand;

    public OutfitData(String gradeId, SlotItem helmet, SlotItem chestplate,
                      SlotItem leggings, SlotItem boots,
                      SlotItem mainhand, SlotItem offhand) {
        this.gradeId    = gradeId;
        this.helmet     = helmet;
        this.chestplate = chestplate;
        this.leggings   = leggings;
        this.boots      = boots;
        this.mainhand   = mainhand;
        this.offhand    = offhand;
    }

    public String   getGradeId()    { return gradeId; }
    public SlotItem getHelmet()     { return helmet; }
    public SlotItem getChestplate() { return chestplate; }
    public SlotItem getLeggings()   { return leggings; }
    public SlotItem getBoots()      { return boots; }
    public SlotItem getMainhand()   { return mainhand; }
    public SlotItem getOffhand()    { return offhand; }
}
