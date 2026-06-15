package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /setlobby
 * Enregistre la position actuelle du joueur comme point de lobby pour /espawn.
 * Permission : site23skin.setlobby (OP par défaut)
 */
public class SetLobbyCommand implements CommandExecutor {

    private final Site23SkinPlugin plugin;

    public SetLobbyCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.format("&cCette commande est réservée aux joueurs."));
            return true;
        }

        if (!player.hasPermission("site23skin.setlobby")) {
            player.sendMessage(plugin.format("&cVous n'avez pas la permission d'utiliser cette commande."));
            return true;
        }

        var loc = player.getLocation();

        plugin.getConfig().set("settings.lobby.world", loc.getWorld().getName());
        plugin.getConfig().set("settings.lobby.x",     Math.round(loc.getX() * 100.0) / 100.0);
        plugin.getConfig().set("settings.lobby.y",     Math.round(loc.getY() * 100.0) / 100.0);
        plugin.getConfig().set("settings.lobby.z",     Math.round(loc.getZ() * 100.0) / 100.0);
        plugin.getConfig().set("settings.lobby.yaw",   Math.round(loc.getYaw() * 10.0) / 10.0);
        plugin.getConfig().set("settings.lobby.pitch", Math.round(loc.getPitch() * 10.0) / 10.0);
        plugin.saveConfig();

        player.sendMessage(plugin.format("&aPoint de lobby enregistré ici : &e"
                + loc.getWorld().getName()
                + " &7(" + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()) + ")"));

        return true;
    }
}
