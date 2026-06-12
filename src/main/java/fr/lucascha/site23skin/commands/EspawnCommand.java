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
 * /espawn [joueur]
 * Restaure le skin Mojang original du compte Minecraft.
 * Sans argument = s'applique au joueur qui tape la commande.
 * Avec argument = s'applique à un autre joueur (permission admin requise).
 */
public class EspawnCommand implements CommandExecutor, TabCompleter {

    private final Site23SkinPlugin plugin;

    public EspawnCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;

        if (args.length >= 1) {
            // Cibler un autre joueur → permission admin
            if (!sender.hasPermission("site23skin.espawn.other")) {
                sender.sendMessage(plugin.format("&cVous n'avez pas la permission de retirer le skin d'un autre joueur."));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable ou hors ligne."));
                return true;
            }
        } else {
            // Pas d'argument → s'applique à soi-même
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.format("&cLa console doit préciser un joueur : /espawn <joueur>"));
                return true;
            }
            if (!sender.hasPermission("site23skin.espawn")) {
                sender.sendMessage(plugin.format("&cVous n'avez pas la permission d'utiliser cette commande."));
                return true;
            }
            target = (Player) sender;
        }

        final Player finalTarget = target;
        final boolean isSelf = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId());

        sender.sendMessage(plugin.format("&7Restauration du skin original de &e" + finalTarget.getName() + "&7..."));

        plugin.getSkinManager().restoreOriginalSkin(finalTarget, () -> {
            // Efface le grade enregistré
            PlayerDataManager dm = plugin.getPlayerDataManager();
            dm.removeGrade(finalTarget.getUniqueId());
            dm.clearOriginalSkin(finalTarget.getUniqueId());

            if (isSelf) {
                finalTarget.sendMessage(plugin.format("&aVotre skin de compte Minecraft a été restauré."));
            } else {
                sender.sendMessage(plugin.format("&aSkin original restauré pour &e" + finalTarget.getName() + "&a."));
                finalTarget.sendMessage(plugin.format("&aVotre skin de compte Minecraft a été restauré par un admin."));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        // Autocomplétion des joueurs seulement si on a la permission admin
        if (args.length == 1 && sender.hasPermission("site23skin.espawn.other")) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(partial))
                    completions.add(p.getName());
        }
        return completions;
    }
}
