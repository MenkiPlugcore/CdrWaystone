package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CdrWaystonePlugin extends JavaPlugin {
    private WaystoneRegistry registry;
    private KeyService keys;
    private CoreService cores;
    private VisualService visuals;
    private EconomyService economy;
    private TravelGuardService guards;
    private TeleportService teleports;
    private MaintenanceService maintenance;
    private DiscoveryService discovery;
    private AccessService access;
    private WaystoneGui gui;
    private NetworkGui networkGui;
    private Map<String, String> skins = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaults();
        saveResourceIfMissing("skins.yml");

        loadSkins();
        registry = new WaystoneRegistry(this);
        registry.load();
        keys = new KeyService(this);
        cores = new CoreService(this);
        visuals = new VisualService(this);
        access = new AccessService(this);
        economy = new EconomyService(this);
        guards = new TravelGuardService(this);
        teleports = new TeleportService(this);
        maintenance = new MaintenanceService(this);
        discovery = new DiscoveryService(this);
        discovery.load();
        gui = new WaystoneGui(this);
        networkGui = new NetworkGui(this);

        getServer().getPluginManager().registerEvents(new WaystoneListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(networkGui, this);
        CdrWaystoneCommand commandHandler = new CdrWaystoneCommand(this);
        PluginCommand command = getCommand("cdrwaystone");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        registerKeyRecipe();
        cores.registerRecipe();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            visuals.refreshAllLoaded();
            maintenance.start();
            discovery.start();
        }, 80L);
        getLogger().info("CdrWaystone v" + getPluginMeta().getVersion() + " enabled with " + skins.size() + " skin(s). Economy: " + economy.providerLabel());
    }

    @Override
    public void onDisable() {
        if (discovery != null) {
            discovery.stop();
            discovery.save();
        }
        if (maintenance != null) maintenance.stop();
        if (teleports != null) teleports.cancelAll();
        if (registry != null) registry.save();
        if (visuals != null) visuals.removeAllVisualEntities();
    }

    public void reloadPlugin() {
        reloadConfig();
        mergeConfigDefaults();
        loadSkins();
        if (economy != null) economy.reload();
        if (guards != null) guards.reload();
        registerKeyRecipe();
        if (cores != null) cores.registerRecipe();
        visuals.refreshAllLoaded();
        if (maintenance != null) maintenance.start();
        if (discovery != null) discovery.start();
    }

    private void mergeConfigDefaults() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void loadSkins() {
        File file = new File(getDataFolder(), "skins.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("skins");
        Map<String, String> loaded = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) loaded.put(key.toLowerCase(), value);
            }
        }
        skins = loaded;
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    private void registerKeyRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "waystone_key");
        Bukkit.removeRecipe(recipeKey);
        if (!getConfig().getBoolean("key.recipe-enabled", true)) return;

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, keys.createKey());
        recipe.shape(" I ", "IRI", " I ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        Bukkit.addRecipe(recipe);
    }

    public WaystoneRegistry registry() { return registry; }
    public KeyService keys() { return keys; }
    public CoreService cores() { return cores; }
    public VisualService visuals() { return visuals; }
    public EconomyService economy() { return economy; }
    public TravelGuardService guards() { return guards; }
    public TeleportService teleports() { return teleports; }
    public MaintenanceService maintenance() { return maintenance; }
    public DiscoveryService discovery() { return discovery; }
    public AccessService access() { return access; }
    public WaystoneGui gui() { return gui; }
    public NetworkGui networkGui() { return networkGui; }
    public Map<String, String> skins() { return skins; }
}
