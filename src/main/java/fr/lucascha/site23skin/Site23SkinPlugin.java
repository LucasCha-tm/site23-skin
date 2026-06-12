package fr.lucascha.site23skin;

import fr.lucascha.site23skin.commands.*;
import fr.lucascha.site23skin.listeners.PlayerListener;
import fr.lucascha.site23skin.managers.GradeManager;
import fr.lucascha.site23skin.managers.PlayerDataManager;
import fr.lucascha.site23skin.managers.SkinManager;
import net.skinsrestorer.api.SkinsRestorerProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public class Site23SkinPlugin extends JavaPlugin {

    private static Site23SkinPlugin instance;

    private GradeManager gradeManager;
    private PlayerDataManager playerDataManager;
    private SkinManager skinManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Crée le dossier skins/ si absent
        File skinsFolder = new File(getDataFolder(), "skins");
        if (!skinsFolder.exists()) {
            skinsFolder.mkdirs();
            getLogger().info("Dossier skins/ créé. Place tes PNG dedans puis /skinreload.");
        }

        // Vérifie que SkinsRestorer est bien disponible
        try {
            SkinsRestorerProvider.get();
            getLogger().info("SkinsRestorer API connectée.");
        } catch (Exception e) {
            getLogger().severe("Impossible de connecter SkinsRestorer API : " + e.getMessage());
            getLogger().severe("Vérifie que SkinsRestorer est bien installé et activé !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        gradeManager      = new GradeManager(this);
        playerDataManager = new PlayerDataManager(this);
        skinManager       = new SkinManager(this);

        gradeManager.loadGrades();

        // Commandes
        Objects.requireNonNull(getCommand("skingrade"))  .setExecutor(new SkinGradeCommand(this));
        Objects.requireNonNull(getCommand("skingrade"))  .setTabCompleter(new SkinGradeCommand(this));
        Objects.requireNonNull(getCommand("skinretirer")).setExecutor(new SkinRetirerCommand(this));
        Objects.requireNonNull(getCommand("skinretirer")).setTabCompleter(new SkinRetirerCommand(this));
        Objects.requireNonNull(getCommand("skinreload")) .setExecutor(new SkinReloadCommand(this));
        Objects.requireNonNull(getCommand("skininfo"))   .setExecutor(new SkinInfoCommand(this));
        Objects.requireNonNull(getCommand("skininfo"))   .setTabCompleter(new SkinInfoCommand(this));
        Objects.requireNonNull(getCommand("espawn"))     .setExecutor(new EspawnCommand(this));
        Objects.requireNonNull(getCommand("espawn"))     .setTabCompleter(new EspawnCommand(this));

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("Site23Skin activé ! " + gradeManager.getGradeCount() + " grades chargés.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveAll();
        getLogger().info("Site23Skin désactivé.");
    }

    // ── Getters ──────────────────────────────────────────────────

    public static Site23SkinPlugin getInstance() { return instance; }
    public GradeManager getGradeManager()         { return gradeManager; }
    public PlayerDataManager getPlayerDataManager(){ return playerDataManager; }
    public SkinManager getSkinManager()            { return skinManager; }

    public String format(String msg) {
        String prefix = getConfig().getString("settings.prefix", "&8[&cSite-23&8] &r");
        return colorize(prefix + msg);
    }

    public static String colorize(String s) {
        return s.replace("&", "§");
    }
}
