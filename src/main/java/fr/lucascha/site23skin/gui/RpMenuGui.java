package fr.lucascha.site23skin.gui;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.Arrays;

/**
 * /rpmenu — GUI en deux étapes :
 *
 *  PAGE 1 : Choix du département (un item par département)
 *  PAGE 2 : Choix du grade dans ce département
 *
 *  Cliquer sur un grade → applique le skin (/skingrade) ET la tenue (/tenue) d'un coup.
 */
public class RpMenuGui {

    // Titres détectés par le listener
    public static final String TITLE_MAIN  = "\u00a78[\u00a7cSite-23\u00a78] \u00a7bChoisissez votre camp";
    public static final String TITLE_GRADE = "\u00a78[\u00a7cSite-23\u00a78] \u00a7bChoisissez votre grade";

    // Département → Material de l'icône
    private static final Map<String, Material> DEPT_ICONS = new LinkedHashMap<>();
    static {
        DEPT_ICONS.put("securite",         Material.IRON_SWORD);
        DEPT_ICONS.put("scientifique",     Material.BOOK);
        DEPT_ICONS.put("medical",          Material.POTION);
        DEPT_ICONS.put("administration",   Material.WRITABLE_BOOK);
        DEPT_ICONS.put("dist",             Material.REDSTONE);
        DEPT_ICONS.put("chaos_insurgency", Material.CROSSBOW);
        DEPT_ICONS.put("classe_d",         Material.ORANGE_WOOL);
        DEPT_ICONS.put("scp",              Material.NETHER_STAR);
    }

    // Département → Nom affiché
    private static final Map<String, String> DEPT_NAMES = new LinkedHashMap<>();
    static {
        DEPT_NAMES.put("securite",         "&cSécurité");
        DEPT_NAMES.put("scientifique",     "&bScientifique");
        DEPT_NAMES.put("medical",          "&dMédical");
        DEPT_NAMES.put("administration",   "&eAdministration");
        DEPT_NAMES.put("dist",             "&6D.I.S.T.");
        DEPT_NAMES.put("chaos_insurgency", "&4Chaos Insurgency");
        DEPT_NAMES.put("classe_d",         "&6Classe D");
        DEPT_NAMES.put("scp",              "&5SCP");
    }

    private final Site23SkinPlugin plugin;

    public RpMenuGui(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── PAGE 1 : Départements ──────────────────────────────────────────────

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);

        // Remplissage décoratif
        ItemStack filler = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Collecte les départements qui ont au moins un grade chargé
        Map<String, List<GradeData>> byDept = getGradesByDept();

        // Positions centrées pour jusqu'à 8 départements
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        int idx = 0;

        for (Map.Entry<String, Material> entry : DEPT_ICONS.entrySet()) {
            String dept = entry.getKey();
            if (!byDept.containsKey(dept)) continue;
            if (idx >= slots.length) break;

            List<GradeData> grades = byDept.get(dept);
            String deptName = DEPT_NAMES.getOrDefault(dept, dept);

            ItemStack icon = new ItemStack(entry.getValue());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize(deptName));
                List<String> lore = new ArrayList<>();
                lore.add(colorize("&7" + grades.size() + " grade(s) disponible(s)"));
                lore.add("");
                lore.add(colorize("&eCliquez pour voir les grades."));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slots[idx++], icon);
        }

        player.openInventory(inv);
    }

    // ─── PAGE 2 : Grades d'un département ──────────────────────────────────

    public void openGrades(Player player, String department) {
        // permission check done per-grade below
        List<GradeData> grades = getGradesByDept().getOrDefault(department, Collections.emptyList());
        if (grades.isEmpty()) {
            player.sendMessage(plugin.format("&cAucun grade disponible pour ce département."));
            return;
        }

        String deptName = DEPT_NAMES.getOrDefault(department, department);
        String title = colorize("&8[&cSite-23&8] &7" + stripColor(deptName));
        // Tronque si trop long (limite Minecraft = 32 chars)
        if (title.length() > 32) title = title.substring(0, 32);

        // Taille adaptée (multiple de 9, min 27)
        int rows = Math.max(3, (int) Math.ceil((grades.size() + 2) / 9.0) + 1);
        if (rows > 6) rows = 6;
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        ItemStack filler = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < rows * 9; i++) inv.setItem(i, filler);

        // Bouton retour slot 0
        inv.setItem(0, makeBack());

        // Grades à partir du slot 10 (ligne 2, col 2) pour laisser de la marge
        int slot = 10;
        for (GradeData grade : grades) {
            if (slot >= rows * 9 - 1) break;
            // Saute les bordures droite/gauche pour centrer
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) { slot += 2; }

            Material icon = gradeIcon(grade);
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize("&f" + grade.getDisplayName()));
                List<String> lore = new ArrayList<>();
                lore.add(colorize("&7Grade : &b" + grade.getId()));
                lore.add(colorize("&7Département : " + deptName));
                lore.add("");
                boolean hasSkin   = new java.io.File(plugin.getDataFolder(), "skins/" + grade.getSkinFile()).exists();
                boolean hasOutfit = plugin.getOutfitManager().hasOutfit(grade.getId());
                lore.add(colorize("&7Skin : "   + (hasSkin   ? "&a✔" : "&c✘ (fichier manquant)")));
                lore.add(colorize("&7Tenue : "  + (hasOutfit ? "&a✔" : "&c✘ (outfit manquant)")));
                lore.add("");
                String perm = "site23skin.grade." + grade.getId();
                boolean hasPerm = player.hasPermission(perm);
                if (hasPerm) {
                    lore.add(colorize("&eCliquez pour choisir ce grade !"));
                } else {
                    lore.add(colorize("&c🔒 Vous n'avez pas accès à ce grade."));
                    lore.add(colorize("&8Permission : " + perm));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            // Griser l'item si pas de permission
            String permCheck = "site23skin.grade." + grade.getId();
            if (!player.hasPermission(permCheck)) {
                ItemStack locked = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta lockedMeta = locked.getItemMeta();
                if (lockedMeta != null) {
                    lockedMeta.setDisplayName(colorize("&c&l🔒 " + grade.getDisplayName()));
                    lockedMeta.setLore(Arrays.asList(
                        colorize("&7Grade : &b" + grade.getId()),
                        colorize("&7Département : " + deptName),
                        "",
                        colorize("&cVous n'avez pas accès à ce grade."),
                        colorize("&8Permission : site23skin.grade." + grade.getId())
                    ));
                    locked.setItemMeta(lockedMeta);
                }
                inv.setItem(slot, locked);
            } else {
                inv.setItem(slot, item);
            }
            slot++;
        }

        player.openInventory(inv);
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private Map<String, List<GradeData>> getGradesByDept() {
        Map<String, List<GradeData>> map = new LinkedHashMap<>();
        for (String id : plugin.getGradeManager().getAllIds()) {
            GradeData g = plugin.getGradeManager().getGrade(id);
            map.computeIfAbsent(g.getDepartment().toLowerCase(), k -> new ArrayList<>()).add(g);
        }
        return map;
    }

    private Material gradeIcon(GradeData grade) {
        return switch (grade.getDepartment().toLowerCase()) {
            case "securite"         -> Material.IRON_CHESTPLATE;
            case "scientifique"     -> Material.BOOK;
            case "medical"          -> Material.POTION;
            case "administration"   -> Material.PAPER;
            case "dist"             -> Material.REDSTONE;
            case "chaos_insurgency" -> Material.LEATHER_CHESTPLATE;
            case "classe_d"         -> Material.ORANGE_WOOL;
            case "scp"              -> Material.NETHER_STAR;
            default                 -> Material.STONE;
        };
    }

    private ItemStack filler(Material mat) {
        ItemStack g = new ItemStack(mat);
        ItemMeta m = g.getItemMeta();
        if (m != null) { m.setDisplayName(" "); g.setItemMeta(m); }
        return g;
    }

    private ItemStack makeBack() {
        ItemStack b = new ItemStack(Material.ARROW);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.setDisplayName(colorize("&c← Retour"));
            m.setLore(Collections.singletonList(colorize("&7Revenir au choix du camp.")));
            b.setItemMeta(m);
        }
        return b;
    }

    private static String colorize(String s) { return Site23SkinPlugin.colorize(s); }

    private static String stripColor(String s) {
        return s.replaceAll("&[0-9a-fklmnor]", "");
    }
}
