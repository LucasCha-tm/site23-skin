package fr.scp.outfits.commands;

import fr.scp.outfits.SCPOutfitsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /removeoutfit <joueur>
 * Retire la tenue du joueur et efface son grade enregistré.
 */
public class RemoveOutfitCommand implements CommandExecutor, TabCompleter {

    private final SCPOutfitsPlugin plugin;

    public RemoveOutfitCommand(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scpoutfits.removeoutfit")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.format("&cUsage : /removeoutfit <joueur>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable."));
            return true;
        }

        plugin.getOutfitManager().removeOutfit(target);
        plugin.getPlayerDataManager().removeGrade(target.getUniqueId());

        sender.sendMessage(plugin.format("&aTenue retirée de &e" + target.getName() + "&a."));
        target.sendMessage(plugin.format("&eVotre tenue a été retirée."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers())
                if (pl.getName().toLowerCase().startsWith(p)) c.add(pl.getName());
        }
        return c;
    }
}
