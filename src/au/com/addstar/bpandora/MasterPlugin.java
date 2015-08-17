package au.com.addstar.bpandora;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

public class MasterPlugin extends Plugin
{
	private HashMap<String, Module> mLoadedModules;
	
	private HashMap<String, ModuleDefinition> mAvailableModules;
	private HashMap<String, ModuleDefinition> mAvailableModulesByName;
	private HashSet<String> mDisabledModules;
	
	private Config mConfig;
	
	private static MasterPlugin mInstance;
	public static MasterPlugin getInstance()
	{
		return mInstance;
	}
	
	public MasterPlugin()
	{
		mAvailableModules = new HashMap<String, ModuleDefinition>();
		mAvailableModulesByName = new HashMap<String, ModuleDefinition>();
		
		mLoadedModules = new HashMap<String, Module>();
	}
	
	private void registerModules()
	{
		registerModule("MultiLoginBlocker", "au.com.addstar.bpandora.modules.MultiLoginBlocker");
		registerModule("VanishedHider", "au.com.addstar.bpandora.modules.VanishedPlayerHider", "BungeeChat");
		registerModule("Restarting", "au.com.addstar.bpandora.modules.Restarting");
		registerModule("PerfMon", "au.com.addstar.bpandora.modules.Perfmon");
		
		//TODO: Register additional modules here
	}
	
	@Override
	public void onEnable()
	{
		mInstance = this;
		mConfig = new Config(new File(getDataFolder(), "config.yml"));
		
		getDataFolder().mkdir();
		
		try
		{
			mConfig.init();
		}
		catch ( InvalidConfigurationException e )
		{
			e.printStackTrace();
			mInstance = null;
			return;
		}
		
		mDisabledModules = new HashSet<String>(mConfig.disabledModules.size());
		
		for(String name : mConfig.disabledModules)
			mDisabledModules.add(name.toLowerCase());
		
		PandoraCommand cmd = new PandoraCommand(this);
		getProxy().getPluginManager().registerCommand(this, cmd);
		
		registerModules();
		loadModules();
	}
	
	@Override
	public void onDisable()
	{
		if(mInstance == null)
			return;
		
		for(Entry<String, Module> module : mLoadedModules.entrySet())
		{
			try
			{
				module.getValue().onDisable();
			}
			catch(Throwable e)
			{
				getLogger().severe("Error disabling module: " + module.getKey());
				e.printStackTrace();
			}
		}
		
		mLoadedModules.clear();
		mAvailableModules.clear();
		mAvailableModulesByName.clear();
		mInstance = null;
	}

	public final boolean isModuleLoaded(String module)
	{
		return mLoadedModules.containsKey(module);
	}
	
	public final Set<String> getAllModules()
	{
		return Collections.unmodifiableSet(mAvailableModulesByName.keySet());
	}
	
	public final boolean reloadModule(String module)
	{
		if(!isModuleLoaded(module))
			return loadModule(module);
		
		Module instance = mLoadedModules.get(module);
		
		mLoadedModules.remove(module);
		
		try
		{
			instance.onDisable();
		}
		catch(Throwable e)
		{
			getLogger().severe("Error disabling module: " + module);
			e.printStackTrace();
			return false;
		}
		
		try
		{
			instance.onEnable();
		}
		catch(Throwable e)
		{
			getLogger().severe("Error enabling module: " + module);
			e.printStackTrace();
			return false;
		}
		
		mLoadedModules.put(module, instance);
		return true;
	}
	
	public final boolean enableModule(String module)
	{
		if(isModuleLoaded(module))
			return false;
		
		return loadModule(module);
	}
	
	public final boolean disableModule(String module)
	{
		if(!isModuleLoaded(module))
			return false;
		
		Module instance = mLoadedModules.get(module);
		
		mLoadedModules.remove(module);
		
		try
		{
			instance.onDisable();
			if(instance instanceof Listener)
				getProxy().getPluginManager().unregisterListener((Listener)instance);
		}
		catch(Throwable e)
		{
			getLogger().severe("Error disabling module: " + module);
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	/**
	 * Registers a module for loading
	 * @param name Name of module
	 * @param moduleClass Class for the module
	 * @param dependencies Names of plugins needed for this module to load
	 */
	public void registerModule(String name, String moduleClass, String... dependencies)
	{
		ModuleDefinition def = new ModuleDefinition(name, moduleClass, dependencies);
		mAvailableModules.put(moduleClass, def);
		mAvailableModulesByName.put(name, def);
	}
	
	private void loadModules()
	{
		mLoadedModules.clear();
		
		for(String name : mAvailableModulesByName.keySet())
		{
			if(!mConfig.disabledModules.contains(name.toLowerCase()))
				loadModule(name);
			else
				getLogger().info(String.format("[%s] Not enabling, disabled from config", name));
		}
	}
	
	private boolean loadModule(String name)
	{
		ModuleDefinition module = mAvailableModulesByName.get(name);
		
		String missingDeps = "";
		
		for(String plugin : module.dependencies)
		{
			if(getProxy().getPluginManager().getPlugin(plugin) == null)
			{
				if(!missingDeps.isEmpty())
					missingDeps += ", ";
				missingDeps += plugin;
			}
		}
		
		if(!missingDeps.isEmpty())
		{
			getLogger().info(String.format("[%s] Not enabling, missing dependencies: %s", name, missingDeps));
			return false;
		}
		
		Module instance = createModule(module.name, module.moduleClass);
		
		if(instance == null)
			return false;
		
		mLoadedModules.put(module.name, instance);
		
		return true;
	}
	
	private Module createModule(String name, String moduleClass)
	{
		try
		{
			Class<?> rawClazz = Class.forName(moduleClass);
			if(!Module.class.isAssignableFrom(rawClazz))
			{
				getLogger().severe("Module class '" + moduleClass + "' is not an instance of Module!");
				return null;
			}
			
			Module module = rawClazz.asSubclass(Module.class).newInstance();
			module.setPandoraInstance(this);
			
			try
			{
				module.onEnable();
				if(module instanceof Listener)
					getProxy().getPluginManager().registerListener(this, (Listener)module);
				
				return module;
			}
			catch(Throwable e)
			{
				getLogger().severe("Failed to enable module: " + name);
				e.printStackTrace();
			}
		}
		catch(InstantiationException e)
		{
			getLogger().severe("Failed to instanciate " + name);
			e.printStackTrace();
		}
		catch(ExceptionInInitializerError e)
		{
			getLogger().severe("Failed to instanciate " + name);
			e.printStackTrace();
		}
		catch ( IllegalAccessException e )
		{
			getLogger().severe("Failed to instanciate " + name + ". No public default constructor available.");
			e.printStackTrace();
		}
		catch ( ClassNotFoundException e )
		{
			getLogger().severe("Failed to instanciate " + name + ". Class not found");
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static class Config extends net.cubespace.Yamler.Config.Config
	{
		public Config(File file)
		{
			CONFIG_FILE = file;
		}
		
		public ArrayList<String> disabledModules = new ArrayList<String>();
	}
	
	private static class ModuleDefinition
	{
		public final String name;
		public final String moduleClass;
		public final String[] dependencies;
		public ModuleDefinition(String name, String moduleClass, String... dependencies)
		{
			this.name = name;
			this.moduleClass = moduleClass;
			if(dependencies == null)
				this.dependencies = new String[0];
			else
				this.dependencies = dependencies;
		}
	}
}
