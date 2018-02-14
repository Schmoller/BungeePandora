package au.com.addstar.bpandora.modules;

import au.com.addstar.bc.BungeeChat;
import au.com.addstar.bpandora.MasterPlugin;
import au.com.addstar.bpandora.Module;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.TabCompleteResponseEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Iterator;

public class VanishedPlayerHider implements Module, Listener
{
	@Override
	public void onEnable()
	{
	}

	@Override
	public void onDisable()
	{
	}

	@Override
	public void setPandoraInstance( MasterPlugin plugin )
	{
	}

	private ProxiedPlayer getPlayer(String name)
	{
		for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers())
		{
			if (player.getName().equalsIgnoreCase(name))
				return player;
			if (ChatColor.stripColor(player.getDisplayName()).equalsIgnoreCase(name))
				return player;
		}
		
		return null;
	}
	
	private boolean isVanished(ProxiedPlayer player)
	{
		return BungeeChat.instance.getSyncManager().getPropertyBoolean(player, "VNP:vanished", false);
	}
	
	@EventHandler
	public void onServerTabComplete(TabCompleteResponseEvent event)
	{
		ProxiedPlayer player = (ProxiedPlayer)event.getReceiver();
		if (player.hasPermission("vanish.see"))
			return;
		
		Iterator<String> it = event.getSuggestions().iterator();
		while(it.hasNext())
		{
			String value = it.next();
			ProxiedPlayer other = getPlayer(value);
			if (other != null)
			{
				if (isVanished(other))
					it.remove();
			}
		}
	}
}
