package fr.lucascha.site23skin.commands;

import fr.lucascha.site23skin.Site23SkinPlugin;
import fr.lucascha.site23skin.managers.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /espawn [joueur]
 * Restaure le skin Mojang original du compte Minecraft ET téléporte au lobby.
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

        // 1) Téléporter au lobby (sur le thread principal)
        teleportToLobby(finalTarget, isSelf, sender);

        // 2) Restaurer le skin Mojang original (async)
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

    /**
     * Téléporte le joueur au lobby configuré dans config.yml.
     * Si le monde ou les coordonnées ne sont pas définis, avertit l'admin.
     */
    private void teleportToLobby(Player target, boolean isSelf, CommandSender sender) {
        String worldName = plugin.getConfig().getString("settings.lobby.world", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Monde de lobby introuvable : \"" + worldName + "\". Configurez settings.lobby.world dans config.yml");
            if (!isSelf) {
                sender.sendMessage(plugin.format("&eAvertissement : le monde de lobby \"" + worldName + "\" est introuvable. Téléportation annulée."));
            }
            target.sendMessage(plugin.format("&eImpossible de vous téléporter au lobby : monde introuvable."));
            return;
        }

        double x    = plugin.getConfig().getDouble("settings.lobby.x",   0.5);
        double y    = plugin.getConfig().getDouble("settings.lobby.y",   64.0);
        double z    = plugin.getConfig().getDouble("settings.lobby.z",   0.5);
        float  yaw  = (float) plugin.getConfig().getDouble("settings.lobby.yaw",   0.0);
        float  pitch= (float) plugin.getConfig().getDouble("settings.lobby.pitch", 0.0);

        Location lobbyLoc = new Location(world, x, y, z, yaw, pitch);
        target.teleport(lobbyLoc);

        if (isSelf) {
            target.sendMessage(plugin.format("&aVous avez été téléporté au lobby."));
        } else {
            target.sendMessage(plugin.format("&aVous avez été téléporté au lobby par un admin."));
            sender.sendMessage(plugin.format("&e" + target.getName() + " &aa été téléporté au lobby."));
        }
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
