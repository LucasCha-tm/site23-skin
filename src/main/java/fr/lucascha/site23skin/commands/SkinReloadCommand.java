package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /skinreload
 * Recharge la config.yml et la liste des grades sans redémarrer.
 */
public class SkinReloadCommand implements CommandExecutor {

    private final Site23SkinPlugin plugin;

    public SkinReloadCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("site23skin.reload")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }

        plugin.reloadConfig();
        plugin.getGradeManager().loadGrades();

        sender.sendMessage(plugin.format(
                "&aConfiguration rechargée ! &e"
                + plugin.getGradeManager().getGradeCount()
                + " &agrades disponibles."));
        return true;
    }
}
