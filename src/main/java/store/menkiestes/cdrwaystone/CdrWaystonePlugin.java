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
    private VisualService visuals;
    private TeleportService teleports;
    private MaintenanceService maintenance;
    private Map<String, String> skins = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("skins.yml");

        loadSkins();
        registry = new WaystoneRegistry(this);
        registry.load();
        keys = new KeyService(this);
        visuals = new VisualService(this);
        teleports = new TeleportService(this);
        maintenance = new MaintenanceService(this);

        getServer().getPluginManager().registerEvents(new WaystoneListener(this), this);
        CdrWaystoneCommand commandHandler = new CdrWaystoneCommand(this);
        PluginCommand command = getCommand("cdrwaystone");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        registerKeyRecipe();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            visuals.refreshAllLoaded();
            maintenance.start();
        }, 80L);
        getLogger().info("CdrWaystone v" + getPluginMeta().getVersion() + " enabled with " + skins.size() + " skin(s).");
    }

    @Override
    public void onDisable() {
        if (maintenance != null) maintenance.stop();
        if (registry != null) registry.save();
        // ItemDisplays are runtime-only. Remove only visuals on plugin unload;
        // collision Barriers remain managed so a hot reload cannot expose/alter
        // the structure between disable and enable.
        if (visuals != null) visuals.removeAllVisualEntities();
    }

    public void reloadPlugin() {
        reloadConfig();
        loadSkins();
        registerKeyRecipe();
        visuals.refreshAllLoaded();
        if (maintenance != null) maintenance.start();
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
    public VisualService visuals() { return visuals; }
    public TeleportService teleports() { return teleports; }
    public MaintenanceService maintenance() { return maintenance; }
    public Map<String, String> skins() { return skins; }
}
