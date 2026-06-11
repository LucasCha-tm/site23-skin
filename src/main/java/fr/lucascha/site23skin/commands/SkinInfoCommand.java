package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.managers.GradeManager;
import fr.lucascha.site23skin.managers.PlayerDataManager;
import fr.lucascha.site23skin.models.GradeData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /skininfo [joueur]
 * Affiche le grade actuel d'un joueur (ou le sien si aucun argument).
 */
public class SkinInfoCommand implements CommandExecutor, TabCompleter {

    private final Site23SkinPlugin plugin;

    public SkinInfoCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("site23skin.info")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable ou hors ligne."));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.format("&cPrécise un joueur : /skininfo <joueur>"));
                return true;
            }
            target = (Player) sender;
        }

        PlayerDataManager dm = plugin.getPlayerDataManager();
        GradeManager gm      = plugin.getGradeManager();

        String gradeId = dm.getGrade(target.getUniqueId());

        if (gradeId == null) {
            sender.sendMessage(plugin.format("&e" + target.getName() + " &7n'a aucun skin de grade actif."));
            return true;
        }

        GradeData grade = gm.getGrade(gradeId);
        String display  = grade != null ? grade.getDisplayName() : gradeId;
        String dept     = grade != null ? grade.getDepartment()  : "inconnu";
        String file     = grade != null ? grade.getSkinFile()    : "?";

        sender.sendMessage(plugin.format("&7Informations skin de &e" + target.getName() + "&7 :"));
        sender.sendMessage(plugin.format("  &7Grade    : &b" + display));
        sender.sendMessage(plugin.format("  &7ID       : &f" + gradeId));
        sender.sendMessage(plugin.format("  &7Dép.     : &a" + dept));
        sender.sendMessage(plugin.format("  &7Fichier  : &f" + file));
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
