package au.com.addstar.bpandora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class PandoraCommand extends Command implements TabExecutor
{
	private MasterPlugin mPlugin;
	
	public PandoraCommand(MasterPlugin plugin)
	{
		super("bpandora", "pandora.command");
		mPlugin = plugin;
	}
	
	@Override
	public void execute( CommandSender sender, String[] args )
	{
		if(args.length == 0)
		{
			sender.sendMessage(TextComponent.fromLegacyText(String.format(ChatColor.GRAY + "Bungee Pandora version %s:", ChatColor.YELLOW + mPlugin.getDescription().getVersion() + ChatColor.GRAY)));
			
			Set<String> modules = mPlugin.getAllModules();
			
			ArrayList<String> all = new ArrayList<>(modules.size());
			for(String module : modules)
			{
				if(mPlugin.isModuleLoaded(module))
					all.add(ChatColor.GREEN + module);
				else
					all.add(ChatColor.RED + module);
			}

			Collections.sort(all, new Comparator<String>()
			{
				@Override
				public int compare( String o1, String o2 )
				{
					return ChatColor.stripColor(o1).compareTo(ChatColor.stripColor(o2));
				}
			});
			
			String moduleList = "";
			for(String mod : all)
			{
				if(!moduleList.isEmpty())
					moduleList += ", ";
				
				moduleList += mod + ChatColor.GRAY;
			}
			
			sender.sendMessage(TextComponent.fromLegacyText(moduleList));
		}
		else if((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("reload"))
		{
			if(args.length == 1)
			{
				int count = 0;
				int total = 0;
				Set<String> modules = mPlugin.getAllModules();
				for(String module : modules)
				{
					if(!mPlugin.isModuleLoaded(module))
						continue;
					if(mPlugin.reloadModule(module))
						++count;
					
					++total;
				}
				
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "Reloaded ALL Pandora modules."));
				if(count < total)
					sender.sendMessage(TextComponent.fromLegacyText("" + ChatColor.RED + (total - count) + " Modules failed. See Console."));
			}
			else
			{
				if(mPlugin.reloadModule(args[1]))
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "Reloaded " + args[1]));
				else
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Failed to reload " + args[1]));
			}
		}
		else if(args.length == 2 && args[0].equalsIgnoreCase("disable"))
		{
			if(!mPlugin.isModuleLoaded(args[1]))
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "That module is not loaded."));
			else
			{
				if(mPlugin.disableModule(args[1]))
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + args[1] + " was disabled."));
				else
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Unable to disable " + args[1] + ". See console for details."));
			}
		}
		else if(args.length == 2 && args[0].equalsIgnoreCase("enable"))
		{
			if(mPlugin.isModuleLoaded(args[1]))
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "That module is already loaded."));
			else
			{
				if(mPlugin.enableModule(args[1]))
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + args[1] + " was enabled."));
				else
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Unable to enable " + args[1] + ". See console for details."));
			}
		}
	}
	
	private List<String> matchModules(String module)
	{
		ArrayList<String> matching = new ArrayList<>();
		for(String name : mPlugin.getAllModules())
		{
			if(module.isEmpty() || name.startsWith(module))
				matching.add(name);
		}
		
		return matching;
	}
	
	@Override
	public Iterable<String> onTabComplete( CommandSender sender, String[] args )
	{
		if(args.length == 1)
		{
			if(args[0].isEmpty())
				return Arrays.asList("enable", "disable", "reload");
			if("enable".startsWith(args[0].toLowerCase()))
				return Arrays.asList("enable");
			if("disable".startsWith(args[0].toLowerCase()))
				return Arrays.asList("disable");
			if("reload".startsWith(args[0].toLowerCase()))
				return Arrays.asList("reload");
		}
		else if(args.length == 2 && args[0].equalsIgnoreCase("reload"))
			return matchModules(args[1]);
		else if(args.length == 2 && args[0].equalsIgnoreCase("enable"))
			return matchModules(args[1]);
		else if(args.length == 2 && args[0].equalsIgnoreCase("disable"))
			return matchModules(args[1]);
		return null;
	}
	

}
