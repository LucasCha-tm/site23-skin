package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.models.OutfitData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /tenue [joueur]
 *
 * Sans argument  → équipe le joueur qui tape la commande avec sa tenue de grade.
 * Avec [joueur]  → équipe un autre joueur (permission site23skin.tenue.other).
 *
 * La tenue est lue depuis outfits/<gradeId>.yml.
 * Elle remplace l'équipement actuel (helmet/chestplate/leggings/boots/mainhand/offhand).
 */
public class TenueCommand implements CommandExecutor, TabCompleter {

    private final Site23SkinPlugin plugin;

    public TenueCommand(Site23SkinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Détermine la cible
        Player target;

        if (args.length == 0) {
            // Soi-même
            if (!(sender instanceof Player p)) {
                sender.sendMessage(plugin.format("&cUsage (console) : /tenue <joueur>"));
                return true;
            }
            if (!p.hasPermission("site23skin.tenue")) {
                p.sendMessage(plugin.format("&cVous n'avez pas la permission."));
                return true;
            }
            target = p;

        } else {
            // Autre joueur
            if (!sender.hasPermission("site23skin.tenue.other")) {
                sender.sendMessage(plugin.format("&cVous n'avez pas la permission d'équiper un autre joueur."));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.format("&cJoueur &e" + args[0] + " &cintrouvable ou hors ligne."));
                return true;
            }
        }

        // Récupère le grade du joueur
        String gradeId = plugin.getPlayerDataManager().getGrade(target.getUniqueId());
        if (gradeId == null) {
            sender.sendMessage(plugin.format("&e" + target.getName() + " &cn'a pas de grade assigné."));
            return true;
        }

        // Récupère la tenue
        if (!plugin.getOutfitManager().hasOutfit(gradeId)) {
            sender.sendMessage(plugin.format(
                "&cAucune tenue trouvée pour le grade &e" + gradeId
                + "&c. Placez &eoutfits/" + gradeId + ".yml &cdans le dossier du plugin."
            ));
            return true;
        }

        OutfitData outfit = plugin.getOutfitManager().getOutfit(gradeId);
        applyOutfit(target, outfit);

        // Messages
        String gradeName = plugin.getGradeManager().hasGrade(gradeId)
            ? plugin.getGradeManager().getGrade(gradeId).getDisplayName()
            : gradeId;

        target.sendMessage(plugin.format("&aTenue &b" + gradeName + " &aéquipée !"));
        if (!target.equals(sender)) {
            sender.sendMessage(plugin.format(
                "&aTenue &b" + gradeName + " &aéquipée sur &e" + target.getName() + "&a."
            ));
        }

        return true;
    }

    private void applyOutfit(Player player, OutfitData outfit) {
        EntityEquipment eq = player.getEquipment();
        if (eq == null) return;

        ItemStack helmet     = outfit.getHelmet()    .build();
        ItemStack chestplate = outfit.getChestplate().build();
        ItemStack leggings   = outfit.getLeggings()  .build();
        ItemStack boots      = outfit.getBoots()     .build();
        ItemStack mainhand   = outfit.getMainhand()  .build();
        ItemStack offhand    = outfit.getOffhand()   .build();

        eq.setHelmet(helmet);
        eq.setChestplate(chestplate);
        eq.setLeggings(leggings);
        eq.setBoots(boots);
        eq.setItemInMainHand(mainhand);
        eq.setItemInOffHand(offhand);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("site23skin.tenue.other")) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(partial))
                    completions.add(p.getName());
        }
        return completions;
    }
}
