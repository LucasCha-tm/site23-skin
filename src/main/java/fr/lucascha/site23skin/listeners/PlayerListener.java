package fr.lucascha.site23skin.listeners;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Réapplique automatiquement le skin de grade quand un joueur se reconnecte,
 * si l'option persistent est activée dans config.yml.
 */
public class PlayerListener implements Listener {

    private final Site23SkinPlugin plugin;

    public PlayerListener(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Si la persistance est désactivée, on ne fait rien
        if (!plugin.getConfig().getBoolean("settings.persistent", true)) return;

        UUID uuid    = event.getPlayer().getUniqueId();
        String grade = plugin.getPlayerDataManager().getGrade(uuid);
        if (grade == null || !plugin.getGradeManager().hasGrade(grade)) return;

        GradeData gradeData = plugin.getGradeManager().getGrade(grade);
        int delay = plugin.getConfig().getInt("settings.apply-delay", 40);

        // Délai configurable (défaut 2s) pour que SkinsRestorer ait fini de charger
        // le skin Mojang original du joueur avant qu'on le fusionne
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!event.getPlayer().isOnline()) return;

                plugin.getSkinManager().applyGradeSkin(
                        event.getPlayer(),
                        gradeData,
                        success -> {
                            if (!success) {
                                plugin.getLogger().warning(
                                        "Impossible de réappliquer le skin de grade '"
                                        + grade + "' pour " + event.getPlayer().getName()
                                        + " à la connexion.");
                            }
                        }
                );
            }
        }.runTaskLater(plugin, delay);
    }
}
