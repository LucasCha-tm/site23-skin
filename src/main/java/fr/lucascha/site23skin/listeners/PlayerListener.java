package fr.lucascha.site23skin.listeners;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.GradeData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Réapplique le skin de grade à la connexion et remet le display name dans le tab.
 *
 * FIXES :
 *  - Priorité HIGHEST pour s'exécuter après SkinsRestorer (qui est en MONITOR ou LOW).
 *  - Délai configurable (apply-delay) pour laisser SR charger le skin Mojang d'abord.
 *  - PlayerQuitEvent : remet le nom de tab par défaut pour éviter glitch d'affichage.
 */
public class PlayerListener implements Listener {

    private final Site23SkinPlugin plugin;

    public PlayerListener(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("settings.persistent", true)) return;

        UUID uuid = event.getPlayer().getUniqueId();
        String gradeId = plugin.getPlayerDataManager().getGrade(uuid);
        if (gradeId == null || !plugin.getGradeManager().hasGrade(gradeId)) return;

        GradeData gradeData = plugin.getGradeManager().getGrade(gradeId);
        int delay = plugin.getConfig().getInt("settings.apply-delay", 60);

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
                                "Impossible de réappliquer le grade '"
                                + gradeId + "' pour " + event.getPlayer().getName()
                            );
                        }
                    }
                );
            }
        }.runTaskLater(plugin, delay);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remet le nom par défaut à la déconnexion pour éviter glitch dans le tab
        event.getPlayer().setPlayerListName(event.getPlayer().getName());
    }
}
