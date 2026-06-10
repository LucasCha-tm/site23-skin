package fr.scp.outfits.commands;

import fr.scp.outfits.SCPOutfitsPlugin;
import fr.scp.outfits.managers.OutfitManager;
import fr.scp.outfits.managers.PlayerDataManager;
import fr.scp.outfits.models.OutfitData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /setgrade <joueur> <grade>
 * Définit directement le grade sans notion de promotion/rétrogradation.
 */
public class SetGradeCommand implements CommandExecutor, TabCompleter {

    private final SCPOutfitsPlugin plugin;

    public SetGradeCommand(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scpoutfits.setgrade")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.format("&cUsage : /setgrade <joueur> <grade>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable."));
            return true;
        }

        String gradeId = args[1].toLowerCase();
        OutfitManager outfitManager = plugin.getOutfitManager();
        if (!outfitManager.hasOutfit(gradeId)) {
            sender.sendMessage(plugin.format("&cGrade inconnu : &e" + gradeId));
            return true;
        }

        outfitManager.removeOutfit(target);
        outfitManager.applyOutfit(target, gradeId);
        plugin.getPlayerDataManager().setGrade(target.getUniqueId(), gradeId);

        OutfitData outfit = outfitManager.getOutfit(gradeId);
        String display = outfit != null ? outfit.getDisplayName() : gradeId;

        sender.sendMessage(plugin.format("&aGrade de &e" + target.getName() + " &adéfini sur &b" + display + "&a."));
        target.sendMessage(plugin.format("&aVotre grade a été défini sur &b" + display + "&a."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (Player pl : Bukkit.getOnlinePlayers())
                if (pl.getName().toLowerCase().startsWith(p)) c.add(pl.getName());
        } else if (args.length == 2) {
            String p = args[1].toLowerCase();
            for (String g : plugin.getOutfitManager().getAllGradeIds())
                if (g.startsWith(p)) c.add(g);
        }
        return c;
    }
}
