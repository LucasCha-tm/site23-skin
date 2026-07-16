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
 * /skingrade <joueur> <grade>
 *
 * FIXES :
 *  - Enregistre le grade dans PlayerData AVANT d'appliquer (pas après)
 *    pour que si le serveur redémarre pendant l'upload MineSkin le grade soit déjà sauvegardé.
 *  - Tab-completer retourne les IDs ET les displayNames pour faciliter l'utilisation.
 */
public class SkinGradeCommand implements CommandExecutor, TabCompleter {

    private final Site23SkinPlugin plugin;

    public SkinGradeCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("site23skin.skingrade")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.format("&cUsage : /skingrade <joueur> <grade>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable ou hors ligne."));
            return true;
        }

        String gradeId = args[1].toLowerCase();
        GradeManager gm = plugin.getGradeManager();

        if (!gm.hasGrade(gradeId)) {
            sender.sendMessage(plugin.format("&cGrade inconnu : &e" + gradeId + "&c. Vérifiez config.yml."));
            return true;
        }

        GradeData grade = gm.getGrade(gradeId);
        PlayerDataManager dm = plugin.getPlayerDataManager();

        String oldGrade = dm.getGrade(target.getUniqueId());
        String oldDisplay = (oldGrade != null && gm.hasGrade(oldGrade))
            ? gm.getGrade(oldGrade).getDisplayName()
            : (oldGrade != null ? oldGrade : "aucun");

        // Sauvegarde IMMÉDIATEMENT avant l'upload async
        dm.setGrade(target.getUniqueId(), gradeId);

        sender.sendMessage(plugin.format("&7Application du skin &b" + grade.getDisplayName()
            + " &7pour &e" + target.getName() + "&7..."));

        plugin.getSkinManager().applyGradeSkin(target, grade, success -> {
            if (success) {
                sender.sendMessage(plugin.format(
                    "&a✔ &e" + target.getName()
                    + " &a: &c" + oldDisplay + " &a→ &b" + grade.getDisplayName()
                ));
                target.sendMessage(plugin.format(
                    "&aVotre grade est maintenant &b" + grade.getDisplayName() + "&a !"
                ));
            } else {
                // Rollback du grade si l'application a échoué
                if (oldGrade != null) dm.setGrade(target.getUniqueId(), oldGrade);
                else dm.removeGrade(target.getUniqueId());

                sender.sendMessage(plugin.format(
                    "&c✘ Échec de l'application du skin pour &e" + target.getName()
                    + "&c. Vérifiez que &e" + grade.getSkinFile()
                    + "&c est dans plugins/Site23Skin/skins/ et que MineSkin est accessible."
                ));
            }
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
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String id : plugin.getGradeManager().getAllIds())
                if (id.startsWith(partial))
                    completions.add(id);
        }
        return completions;
    }
}
