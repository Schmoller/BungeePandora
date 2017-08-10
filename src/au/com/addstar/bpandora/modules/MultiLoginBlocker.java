package au.com.addstar.bpandora.modules;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import au.com.addstar.bpandora.MasterPlugin;
import au.com.addstar.bpandora.Module;

public class MultiLoginBlocker implements Module, Listener
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
	
	@EventHandler
	public void onPreLogin(PreLoginEvent event)
	{
		if(ProxyServer.getInstance().getPlayer(event.getConnection().getName()) != null)
		{
			event.setCancelled(true);
			event.setCancelReason(TextComponent.fromLegacyText("You are already connected to this server"));
		}
	}

}
