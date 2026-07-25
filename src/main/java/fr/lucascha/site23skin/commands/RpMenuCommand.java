package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.gui.RpMenuGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /rpmenu — Ouvre le menu de choix de camp/grade.
 * Page 1 : liste des départements.
 * Page 2 : grades du département choisi.
 * Clic sur un grade → applique skin + tenue.
 */
public class RpMenuCommand implements CommandExecutor {

    private final Site23SkinPlugin plugin;
    private final RpMenuGui gui;

    public RpMenuCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
        this.gui    = new RpMenuGui(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.format("&cCette commande est réservée aux joueurs."));
            return true;
        }
        if (!player.hasPermission("site23skin.rpmenu")) {
            player.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }
        gui.openMain(player);
        return true;
    }
}
