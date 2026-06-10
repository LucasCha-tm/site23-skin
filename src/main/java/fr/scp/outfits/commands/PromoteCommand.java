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
 * /promote <joueur> <grade>
 * Retire l'ancienne tenue et applique la nouvelle.
 */
public class PromoteCommand implements CommandExecutor, TabCompleter {

    private final SCPOutfitsPlugin plugin;

    public PromoteCommand(SCPOutfitsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scpoutfits.promote")) {
            sender.sendMessage(plugin.format("&cVous n'avez pas la permission d'utiliser cette commande."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.format("&cUsage : /promote <joueur> <grade>"));
            return true;
        }

        String playerName = args[0];
        String gradeId = args[1].toLowerCase();

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(plugin.format("&cJoueur &e" + playerName + " &cintrouvable ou hors ligne."));
            return true;
        }

        OutfitManager outfitManager = plugin.getOutfitManager();
        PlayerDataManager dataManager = plugin.getPlayerDataManager();

        if (!outfitManager.hasOutfit(gradeId)) {
            sender.sendMessage(plugin.format("&cGrade inconnu : &e" + gradeId
                    + "&c. Vérifiez le dossier &eoutfits/&c."));
            return true;
        }

        // Retire l'ancienne tenue (slots armure seulement)
        String oldGrade = dataManager.getGrade(target.getUniqueId());
        outfitManager.removeOutfit(target);

        // Applique la nouvelle tenue
        outfitManager.applyOutfit(target, gradeId);
        dataManager.setGrade(target.getUniqueId(), gradeId);

        OutfitData newOutfit = outfitManager.getOutfit(gradeId);
        String displayName = newOutfit != null ? newOutfit.getDisplayName() : gradeId;

        // Messages
        String oldDisplay = oldGrade != null && outfitManager.getOutfit(oldGrade) != null
                ? outfitManager.getOutfit(oldGrade).getDisplayName() : (oldGrade != null ? oldGrade : "aucun");

        sender.sendMessage(plugin.format(
                "&aJoueur &e" + target.getName()
                + " &apromut de &c" + oldDisplay
                + " &avers &b" + displayName + "&a."));

        target.sendMessage(plugin.format(
                "&aVous avez été promu au grade &b" + displayName + "&a !"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complétion des joueurs en ligne
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Complétion des grades
            String partial = args[1].toLowerCase();
            for (String gradeId : plugin.getOutfitManager().getAllGradeIds()) {
                if (gradeId.startsWith(partial)) {
                    completions.add(gradeId);
                }
            }
        }

        return completions;
    }
}
