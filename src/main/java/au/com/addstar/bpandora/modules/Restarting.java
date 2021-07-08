package au.com.addstar.bpandora.modules;

import au.com.addstar.bpandora.MasterPlugin;
import au.com.addstar.bpandora.Misc;
import au.com.addstar.bpandora.Module;
import com.google.common.base.Joiner;
import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.cubespace.Yamler.Config.YamlConfig;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Restarting implements Module, Listener
{
	private Logger log;
	private Plugin plugin;
	private RestartConfig config;
	
	private String restartReason;
	
	// Restarting by player count
	private boolean isWaitingForPlayers;
	private ScheduledTask playerWaitTask;
	private int minPlayers;
	private int abortThreshold;
	private long playerWaitTime;
	
	// Restarting by countdown
	private boolean isShuttingDown;
	private long lastAnnounceTime;
	private ScheduledTask restartTask;
	private long countdownTime;
	private long targetTime;
	private long lockoutTime;
	
	private ProxyServer proxy;
	
	// Commands
	private Command commandRestartCancel;
	private Command commandRestartIn;
	private Command commandRestartWhen;
	
	@Override
	public void onEnable()
	{
		proxy = ProxyServer.getInstance();
		
		config = new RestartConfig();
		try
		{
			File conffile = new File(plugin.getDataFolder(), "restart.yml");
			config.init(conffile);

			playerWaitTime = Misc.parseDateDiff(config.playerWaitTime);
			if (config.enableLockout)
				lockoutTime = Misc.parseDateDiff(config.lockoutTime);
			else
				lockoutTime = 0;
		}
		catch ( InvalidConfigurationException e )
		{
			e.printStackTrace();
			return;
		}
		
		proxy.getPluginManager().registerCommand(plugin, commandRestartCancel = new RestartCancelCommand());
		proxy.getPluginManager().registerCommand(plugin, commandRestartIn = new RestartInCommand());
		proxy.getPluginManager().registerCommand(plugin, commandRestartWhen = new RestartWhenCommand());
	}

	@Override
	public void onDisable()
	{
		proxy.getPluginManager().unregisterCommand(commandRestartCancel);
		proxy.getPluginManager().unregisterCommand(commandRestartIn);
		proxy.getPluginManager().unregisterCommand(commandRestartWhen);
	}

	@Override
	public void setPandoraInstance( MasterPlugin plugin )
	{
		this.plugin = plugin;
		log = plugin.getLogger();
	}
	
	private String getRestartReason()
	{
		if (restartReason == null)
			return config.defaultReason;
		else
			return restartReason;
	}

	private void startRestartSequence()
	{
		// Do initial warning broadcast
		String message = config.countdownStartText
				.replace("{time}", Misc.timeDiffToString(countdownTime))
				.replace("{reason}", getRestartReason());
		message = ChatColor.translateAlternateColorCodes('&', message);
		proxy.broadcast(TextComponent.fromLegacyText(message));
		
		// Setup
		targetTime = System.currentTimeMillis() + countdownTime;
		lastAnnounceTime = countdownTime / 1000;
		isShuttingDown = true;
		
		log.warning(ChatColor.GOLD + "[Restart] Starting restart countdown of " + Misc.timeDiffToString(countdownTime));
		
		// Begin the countdown task
		restartTask = proxy.getScheduler().schedule(plugin, () -> {
			long remainingTime = targetTime - System.currentTimeMillis();
			long secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(remainingTime);

			if (remainingTime <= 0)
			{
				restartTask.cancel();
				restart();
				return;
			}

			// Handle messages
			if (secondsRemaining == 0)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownEndText)));
			else if (secondsRemaining < 10)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownShort.replace("{time}", String.valueOf(secondsRemaining)))));
			else if (secondsRemaining == 10 && lastAnnounceTime > 10)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownTime.replace("{time}", "10 seconds"))));
			else if (secondsRemaining <= 30 && lastAnnounceTime > 30)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownTime.replace("{time}", "30 seconds"))));
			else if (secondsRemaining <= 60 && lastAnnounceTime > 60)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownTime.replace("{time}", "60 seconds"))));
			else if (secondsRemaining / 60 < lastAnnounceTime / 60 && secondsRemaining > 60)
				proxy.broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.countdownTime.replace("{time}", ((int)Math.round(secondsRemaining / 60.0)) + " minutes"))));

			lastAnnounceTime = secondsRemaining;
		}, 1, 1, TimeUnit.SECONDS);
	}

	private void restart()
	{
		final String kickMessage = ChatColor.translateAlternateColorCodes('&', config.kick.replace("{reason}", getRestartReason()));
		// Disconnect all players nicely
		BaseComponent[] message = TextComponent.fromLegacyText(kickMessage);
		for (ProxiedPlayer player : proxy.getPlayers())
			player.disconnect(message);
		
		proxy.getScheduler().schedule(plugin, () -> {
			// Now stop the proxy
			proxy.stop(kickMessage);
		}, 1, TimeUnit.SECONDS);
	}
	
	private int getMaxPlayers()
	{
		return minPlayers + abortThreshold;
	}
	
	@EventHandler
	public void onPlayerDisconnect(PlayerDisconnectEvent event)
	{
		// + 1 because they have not yet been removed from the list
		if (isWaitingForPlayers && proxy.getPlayers().size() <= minPlayers + 1)
		{
			if (playerWaitTask == null)
			{
				playerWaitTask = proxy.getScheduler().schedule(plugin, () -> {
					isWaitingForPlayers = false;
					startRestartSequence();
				}, playerWaitTime, TimeUnit.MILLISECONDS);
				
				log.warning(ChatColor.GOLD + "[Restart] Min players goal has been reached. Now waiting " + TimeUnit.MILLISECONDS.toSeconds(playerWaitTime) + " seconds for players count to rise above " + getMaxPlayers());
			}
		}
	}
	
	@EventHandler
	public void onPlayerConnect(PostLoginEvent event)
	{
		// Has started counting down but not on the shutdown sequence yet
		if (isWaitingForPlayers && playerWaitTask != null)
		{
			if (proxy.getPlayers().size() >= getMaxPlayers())
			{
				playerWaitTask.cancel();
				playerWaitTask = null;
				log.warning(ChatColor.GOLD + "[Restart] Countdown aborted. Too many players joined.");
			}
		}
	}
	
	@EventHandler
	public void onPlayerPreConnect(PreLoginEvent event)
	{
		if (!isShuttingDown)
			return;

		// Prevent players from joining within the lockout time
		if (targetTime - System.currentTimeMillis() < lockoutTime)
		{
			event.setCancelReason(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', config.lockoutMessage.replace("{reason}", getRestartReason()))));
			event.setCancelled(true);
		}
	}
	
	private class RestartInCommand extends Command
	{
		RestartInCommand()
		{
			super("!restart", "bungeecord.end", "!end", "!endin", "!restartin");
		}

		@Override
		public void execute( CommandSender sender, String[] args )
		{
			if (sender instanceof ProxiedPlayer && !config.allowNonConsole)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are not allowed to use this command"));
				return;
			}
			
			if (args.length < 1)
			{
				sender.sendMessage(TextComponent.fromLegacyText("Usage: /!restart <countdown> [Message]"));
				return;
			}
			
			if (isWaitingForPlayers)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "A restart wait has already been started. Use !restartcancel to stop it"));
				return;
			}
			
			if (isShuttingDown)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "The restart count down has already started. Use !restartcancel to stop it"));
				return;
			}
			
			String reason = null;
			if (args.length > 1)
				reason = Joiner.on(' ').join(Arrays.copyOfRange(args, 1, args.length));
			
			// Parse the count down time
			long countDown = Misc.parseDateDiff(args[0]);
			
			if (countDown == 0)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Illegal count down value. Should be in the format of '5m' or '10m30s"));
				return;
			}
			
			// Start the wait
			countdownTime = countDown;
			restartReason = reason;
			startRestartSequence();
			
			sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "A restart count down has begun."));
		}
	}
	
	private class RestartWhenCommand extends Command
	{
		RestartWhenCommand()
		{
			super("!restartwhen", "bungeecord.end", "!endwhen");
		}
		
		@Override
		public void execute( CommandSender sender, String[] args )
		{
			if (sender instanceof ProxiedPlayer && !config.allowNonConsole)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are not allowed to use this command"));
				return;
			}
			
			if (args.length < 2)
			{
				sender.sendMessage(TextComponent.fromLegacyText("Usage: /!restartwhen <playercount> <countdown> [Message]"));
				return;
			}
			
			if (isWaitingForPlayers)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "A restart wait has already been started. Use !restartcancel to stop it"));
				return;
			}
			
			if (isShuttingDown)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "The restart count down has already started. Use !restartcancel to stop it"));
				return;
			}
			
			String reason = null;
			if (args.length > 2)
				reason = Joiner.on(' ').join(Arrays.copyOfRange(args, 2, args.length));
			
			// Parse the minimum player count
			int playerCount;
			
			try
			{
				playerCount = Integer.parseInt(args[0]);
				if (playerCount < 0)
				{
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Cannot have negative player count"));
					return;
				}
			}
			catch (NumberFormatException e)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Illegal value for min players"));
				return;
			}
			
			// Parse the count down time
			long countDown = Misc.parseDateDiff(args[1]);
			
			if (countDown == 0)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Illegal count down value. Should be in the format of '5m' or '10m30s"));
				return;
			}
			
			// Start the wait
			isWaitingForPlayers = true;
			restartReason = reason;
			minPlayers = playerCount;
			countdownTime = countDown;
			
			sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "A restart wait has begun. Waiting for player count to drop below " + playerCount + " players"));
		}
	}
	
	private class RestartCancelCommand extends Command
	{
		RestartCancelCommand()
		{
			super("!restartcancel", "bungeecord.end");
		}

		@Override
		public void execute( CommandSender sender, String[] args )
		{
			if (sender instanceof ProxiedPlayer && !config.allowNonConsole)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are not allowed to use this command"));
				return;
			}
			
			if (args.length != 0)
			{
				sender.sendMessage(TextComponent.fromLegacyText("Usage: /!restartcancel"));
				return;
			}
			
			if (!isWaitingForPlayers && !isShuttingDown)
			{
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "No restart is in progress"));
				return;
			}

			if (isWaitingForPlayers)
			{
				if (playerWaitTask != null)
					playerWaitTask.cancel();
				playerWaitTask = null;
				
				isWaitingForPlayers = false;
			}

			if (isShuttingDown)
			{
				if (restartTask != null)
					restartTask.cancel();
				restartTask = null;

				isShuttingDown = false;
			}

			sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "You have aborted a restart"));
			log.warning(ChatColor.GOLD + "[Restart] Restart aborted");
		}
	}

	public static class RestartConfig extends YamlConfig {
		public String defaultReason = "Unknown";

		public String countdownStartText = "&d[&cAttention&d] &eThe network gateway is restarting in {time}. &aYou will be disconnected.";
		public String countdownEndText = "&d[&cNotice&d] &eRestarting...";
		public String countdownTime = "&d[&cNotice&d] &aNetwork gateway restart in &e{time}";
		public String countdownShort = "&c{time}";

		public String kick = "The network gateway is restarting.";
		public String lockoutMessage = "This server will be restarting soon. Please come back later";
		
		@Comment("The time to wait to check if the player count increases above the limit")
		public String playerWaitTime = "2m";

		@Comment("If the player count goes above the set min players + this threshold, then the wait will be aborted")
		public int playerAbortThreshold = 3;

		public boolean enableLockout = true;
		public String lockoutTime = "10m";

		public boolean allowNonConsole = true;
	}
}
