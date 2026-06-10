package fr.scp.outfits.commands;

import fr.scp.outfits.SCPOutfitsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /reloadoutfits
 * Recharge tous les fichiers de tenues depuis le disque.
 */
public class ReloadCommand implements CommandExecutor {

    private final SCPOutfitsPlugin plugin;

    public ReloadCommand(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scpoutfits.reload")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }

        plugin.reloadConfig();
        plugin.getOutfitManager().loadAllOutfits();

        sender.sendMessage(plugin.format("&aTenues rechargées ! &e"
                + plugin.getOutfitManager().getOutfitCount() + " &atenues disponibles."));
        return true;
    }
}
