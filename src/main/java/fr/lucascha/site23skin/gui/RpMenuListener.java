package fr.lucascha.site23skin.gui;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import fr.lucascha.site23skin.models.OutfitData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Gère les clics dans les deux pages du /rpmenu.
 *
 * PAGE 1 (TITLE_MAIN)  → clic sur département → ouvre page 2
 * PAGE 2 (titre dept)  → clic slot 0 = retour, clic grade = applique skin + tenue
 */
public class RpMenuListener implements Listener {

    private final Site23SkinPlugin plugin;
    private final RpMenuGui gui;

    // Mapping nom affiché département → id département
    private static final Map<String, String> DISPLAY_TO_DEPT = new LinkedHashMap<>();
    static {
        DISPLAY_TO_DEPT.put("Sécurité",         "securite");
        DISPLAY_TO_DEPT.put("Scientifique",      "scientifique");
        DISPLAY_TO_DEPT.put("Médical",           "medical");
        DISPLAY_TO_DEPT.put("Administration",    "administration");
        DISPLAY_TO_DEPT.put("D.I.S.T.",          "dist");
        DISPLAY_TO_DEPT.put("Chaos Insurgency",  "chaos_insurgency");
        DISPLAY_TO_DEPT.put("Classe D",          "classe_d");
        DISPLAY_TO_DEPT.put("SCP",               "scp");
    }

    public RpMenuListener(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.gui    = new RpMenuGui(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // ── PAGE 1 : choix du département ─────────────────────────────────────
        if (title.equals(RpMenuGui.TITLE_MAIN)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;

            // Strip §codes pour comparer
            String rawName = meta.getDisplayName().replaceAll("§[0-9a-fklmnor]", "");
            String dept = DISPLAY_TO_DEPT.get(rawName);
            if (dept == null) return;

            gui.openGrades(player, dept);
            return;
        }

        // ── PAGE 2 : choix du grade ────────────────────────────────────────────
        if (title.contains("[") && title.contains("Site-23")) {
            // Vérifie que c'est bien une page grade (pas le TITLE_MAIN)
            if (title.equals(RpMenuGui.TITLE_MAIN)) return;

            event.setCancelled(true);
            if (event.getRawSlot() < 0) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            // Bouton retour
            if (event.getRawSlot() == 0 || clicked.getType() == Material.ARROW) {
                gui.openMain(player);
                return;
            }

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.hasLore()) return;

            // Récupère le gradeId depuis le lore "Grade : <id>"
            String gradeId = null;
            for (String line : meta.getLore()) {
                String clean = line.replaceAll("§[0-9a-fklmnor]", "");
                if (clean.startsWith("Grade : ")) {
                    gradeId = clean.substring("Grade : ".length()).trim();
                    break;
                }
            }
            if (gradeId == null || !plugin.getGradeManager().hasGrade(gradeId)) return;

            GradeData grade = plugin.getGradeManager().getGrade(gradeId);

            // Vérifie la permission spécifique au grade
            String perm = "site23skin.grade." + gradeId;
            if (!player.hasPermission(perm)) {
                player.sendMessage(plugin.format(
                    "&cVous n'avez pas la permission d'accéder au grade &b"
                    + grade.getDisplayName() + "&c."
                ));
                return;
            }

            player.closeInventory();

            final String finalGradeId = gradeId;

            // 1) Sauvegarde le grade
            plugin.getPlayerDataManager().setGrade(player.getUniqueId(), finalGradeId);

            // 2) Applique la tenue immédiatement (sync)
            if (plugin.getOutfitManager().hasOutfit(finalGradeId)) {
                OutfitData outfit = plugin.getOutfitManager().getOutfit(finalGradeId);
                var eq = player.getEquipment();
                if (eq != null) {
                    eq.setHelmet(outfit.getHelmet().build());
                    eq.setChestplate(outfit.getChestplate().build());
                    eq.setLeggings(outfit.getLeggings().build());
                    eq.setBoots(outfit.getBoots().build());
                    eq.setItemInMainHand(outfit.getMainhand().build());
                    eq.setItemInOffHand(outfit.getOffhand().build());
                }
            }

            // 3) Applique le skin (async)
            player.sendMessage(plugin.format(
                "&7Application du grade &b" + grade.getDisplayName() + "&7..."
            ));

            plugin.getSkinManager().applyGradeSkin(player, grade, success -> {
                if (success) {
                    player.sendMessage(plugin.format(
                        "&a✔ Grade &b" + grade.getDisplayName() + " &aappliqué !"
                    ));
                } else {
                    player.sendMessage(plugin.format(
                        "&c✘ Skin indisponible pour &b" + grade.getDisplayName()
                        + "&c — tenue appliquée quand même."
                    ));
                }
            });
        }
    }
}
