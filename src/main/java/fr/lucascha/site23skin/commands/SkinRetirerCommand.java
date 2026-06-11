package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.managers.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /skinretirer <joueur>
 * Retire le skin de grade et restaure le skin Mojang original du joueur.
 */
public class SkinRetirerCommand implements CommandExecutor, TabCompleter {

    private final Site23SkinPlugin plugin;

    public SkinRetirerCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("site23skin.retirer")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.format("&cUsage : /skinretirer <joueur>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable ou hors ligne."));
            return true;
        }

        PlayerDataManager dm = plugin.getPlayerDataManager();

        if (!dm.hasGrade(target.getUniqueId())) {
            sender.sendMessage(plugin.format("&e" + target.getName() + " &cn'a aucun skin de grade actif."));
            return true;
        }

        sender.sendMessage(plugin.format("&7Restauration du skin original de &e" + target.getName() + "&7..."));

        plugin.getSkinManager().restoreOriginalSkin(target, () -> {
            dm.removeGrade(target.getUniqueId());
            dm.clearOriginalSkin(target.getUniqueId());

            sender.sendMessage(plugin.format("&aSkin original restauré pour &e" + target.getName() + "&a."));
            target.sendMessage(plugin.format("&aVotre skin original a été restauré."));
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(partial))
                    completions.add(p.getName());
        }
        return completions;
    }
}
