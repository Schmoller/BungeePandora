package au.com.addstar.bpandora.modules;

import au.com.addstar.bpandora.MasterPlugin;
import au.com.addstar.bpandora.Module;
import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.YamlConfig;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class ChatControlMirror implements Module, Listener
{
	private Logger log;
	private Plugin plugin;
	private ChatControlMirrorConfig config;
	private ProxyServer proxy;
	private List<String> validServers;

	@Override
	public void onEnable()
	{
		proxy = ProxyServer.getInstance();
		config = new ChatControlMirrorConfig();
		try {
			File conffile = new File(plugin.getDataFolder(), "chatcontrolmirror.yml");
			config.init(conffile);
		} catch (Exception e) {
			log.warning("[ChatControlMirror] Unable to load config: " + e.getLocalizedMessage());
			e.printStackTrace();
			return;
		}
		validServers = Arrays.asList(config.mirrorServers.split(","));
		plugin.getProxy().registerChannel(config.inChannel);
	}

	@Override
	public void onDisable()
	{
		plugin.getProxy().unregisterChannel(config.inChannel);
	}

	@Override
	public void setPandoraInstance( MasterPlugin plugin )
	{
		this.plugin = plugin;
		log = plugin.getLogger();
	}

	@EventHandler
	public void onPluginMessage(PluginMessageEvent event) {
		if (config.inChannel.equals(event.getTag())) {
			Server server = null;
			// Search for a valid server with a player online
			for (ProxiedPlayer p : plugin.getProxy().getPlayers()) {
				if (validServers.contains(p.getServer().getInfo().getName())) {
					// Found one!
					server = p.getServer();
					break;
				}
			}

			if (server != null) {
				server.sendData(config.outChannel, event.getData());
			} else {
				log.warning("[ChatControlMirror] No valid server found to relay mirror message");
				return;
			}
		}
	}

	public static class ChatControlMirrorConfig extends YamlConfig {
		@Comment("Plugin message channel to listen on for mirror events")
		public String inChannel = "bpandora:chatcontrolmirror";

		@Comment("Relay messages to a server on this channel")
		public String outChannel = "pandora:chatcontrolmirror";

		@Comment("Relay messages to a server on this channel")
		public String mirrorServers = "hub,survival,skyblock2,limbo";
	}
}
