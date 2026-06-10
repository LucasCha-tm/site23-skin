package fr.scp.outfits;

import fr.scp.outfits.commands.*;
import fr.scp.outfits.listeners.PlayerListener;
import fr.scp.outfits.managers.OutfitManager;
import fr.scp.outfits.managers.PlayerDataManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public class SCPOutfitsPlugin extends JavaPlugin {

    private static SCPOutfitsPlugin instance;
    private OutfitManager outfitManager;
    private PlayerDataManager playerDataManager;
    private Logger log;

    @Override
    public void onEnable() {
        instance = this;
        log = getLogger();

        // Sauvegarde config par défaut
        saveDefaultConfig();

        // Crée le dossier outfits s'il n'existe pas
        File outfitsFolder = new File(getDataFolder(), "outfits");
        if (!outfitsFolder.exists()) {
            outfitsFolder.mkdirs();
            // Copie les tenues par défaut depuis les ressources
            copyDefaultOutfits();
        }

        // Initialise les managers
        outfitManager = new OutfitManager(this);
        playerDataManager = new PlayerDataManager(this);

        // Charge les tenues
        outfitManager.loadAllOutfits();

        // Enregistre les commandes
        Objects.requireNonNull(getCommand("promote")).setExecutor(new PromoteCommand(this));
        Objects.requireNonNull(getCommand("promote")).setTabCompleter(new PromoteCommand(this));
        Objects.requireNonNull(getCommand("demote")).setExecutor(new DemoteCommand(this));
        Objects.requireNonNull(getCommand("demote")).setTabCompleter(new DemoteCommand(this));
        Objects.requireNonNull(getCommand("setgrade")).setExecutor(new SetGradeCommand(this));
        Objects.requireNonNull(getCommand("setgrade")).setTabCompleter(new SetGradeCommand(this));
        Objects.requireNonNull(getCommand("removeoutfit")).setExecutor(new RemoveOutfitCommand(this));
        Objects.requireNonNull(getCommand("removeoutfit")).setTabCompleter(new RemoveOutfitCommand(this));
        Objects.requireNonNull(getCommand("reloadoutfits")).setExecutor(new ReloadCommand(this));

        // Enregistre les listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        log.info("SCPOutfits activé ! " + outfitManager.getOutfitCount() + " tenues chargées.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        log.info("SCPOutfits désactivé.");
    }

    /**
     * Copie toutes les tenues par défaut du jar vers le dossier du plugin
     */
    private void copyDefaultOutfits() {
        String[] defaultOutfits = {
            // Sécurité
            "recrue", "os", "oss", "ods", "major", "lieutenant", "capitaine", "general",
            // Scientifique
            "stagiaire_sc", "scientifique", "scientifique_experimente", "administrateur_scientifique",
            // Administration
            "junior_manager", "manager", "site_assistant_manager", "site_manager",
            "site_senior_manager", "site_director",
            // DIST
            "technicien", "technicien_confirme",
            // Médical
            "stagiaire_med", "docteur", "medecin", "chirurgien", "responsable_medical",
            // Chaos Insurgency
            "ic_recrue", "soldat", "caporal", "sergeant", "ic_major",
            "ic_lieutenant", "ic_capitaine", "ic_general"
        };

        for (String outfit : defaultOutfits) {
            String resourcePath = "outfits/" + outfit + ".yml";
            File dest = new File(getDataFolder(), resourcePath);
            if (!dest.exists() && getResource(resourcePath) != null) {
                saveResource(resourcePath, false);
                log.info("Tenue copiée : " + outfit + ".yml");
            }
        }
    }

    public static SCPOutfitsPlugin getInstance() {
        return instance;
    }

    public OutfitManager getOutfitManager() {
        return outfitManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    /**
     * Formate un message avec le préfixe configuré
     */
    public String format(String message) {
        String prefix = getConfig().getString("settings.prefix", "&8[&cSCP&8] &r");
        return colorize(prefix + message);
    }

    public static String colorize(String text) {
        return text.replace("&", "§");
    }
}
