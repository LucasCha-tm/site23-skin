package fr.scp.outfits.listeners;

import fr.scp.outfits.SCPOutfitsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Réapplique la tenue sauvegardée quand un joueur se reconnecte.
 */
public class PlayerListener implements Listener {

    private final SCPOutfitsPlugin plugin;

    public PlayerListener(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("settings.persistent", true)) return;

        UUID uuid = event.getPlayer().getUniqueId();
        String grade = plugin.getPlayerDataManager().getGrade(uuid);

        if (grade != null && plugin.getOutfitManager().hasOutfit(grade)) {
            // Délai d'1 tick pour s'assurer que l'inventaire est bien chargé
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (event.getPlayer().isOnline()) {
                        plugin.getOutfitManager().applyOutfit(event.getPlayer(), grade);
                    }
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Sauvegarde déclenchée automatiquement dans saveOne() lors du setGrade.
        // Rien de plus à faire ici — le grade est déjà persisté.
    }
}
