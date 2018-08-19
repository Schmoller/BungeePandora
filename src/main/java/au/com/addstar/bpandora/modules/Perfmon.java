package au.com.addstar.bpandora.modules;

import au.com.addstar.bpandora.MasterPlugin;
import au.com.addstar.bpandora.Module;
import com.google.common.collect.Maps;
import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.cubespace.Yamler.Config.YamlConfig;
import net.md_5.bungee.api.PerformanceStatistics;
import net.md_5.bungee.api.PerformanceStatistics.DownstreamStatistics;
import net.md_5.bungee.api.PerformanceStatistics.PacketInfo;
import net.md_5.bungee.api.PerformanceStatistics.UpstreamStatistics;
import net.md_5.bungee.api.PerformanceTracker;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Perfmon implements Module {
    private Plugin plugin;
    private ProxyServer proxy;
    
    private PerfmonConfig config;
    
    private PerformanceTracker tracker;
    private ScheduledTask outTask;
    
    private long slotLength;
    private long updateInterval;
    
    private long lastUpdateTime;
    private long lastTimeSlot;
    
    // Database stuff
    private Connection con;
    
    private PreparedStatement insertUpstream;
    private PreparedStatement insertDownstream;
    
    @Override
    public void onEnable() {
        // Load the config
        try {
            config.init();
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
        
        slotLength = TimeUnit.MINUTES.toMillis(config.timeslotLength);
        updateInterval = TimeUnit.MINUTES.toMillis(config.updateInterval);
        
        // Initialize the database
        if (!initializeDatabase())
            return;
        
        lastUpdateTime = System.currentTimeMillis();
        lastTimeSlot = getTimeSlot(lastUpdateTime);
        
        tracker = proxy.getPerformanceTracker();
        tracker.setEnabled(true);
        
        outTask = proxy.getScheduler().schedule(plugin, this::process, 1, 1, TimeUnit.MINUTES);
    }
    
    @Override
    public void onDisable() {
        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        outTask.cancel();
        
        tracker.setEnabled(false);
        tracker.resetStatistics();
    }
    
    @Override
    public void setPandoraInstance(MasterPlugin plugin) {
        this.plugin = plugin;
        proxy = plugin.getProxy();
        
        config = new PerfmonConfig(new File(plugin.getDataFolder(), "perfmon.yml"));
    }
    
    private boolean initializeDatabase() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Mysql jdbc driver missing. This is should not happen");
        }
        
        try {
            Properties props = new Properties();
            props.setProperty("user", config.databaseUsername);
            props.setProperty("password", config.databasePassword);
            props.setProperty("useSSL", config.databaseSSL.toString());
            con = DriverManager.getConnection(String.format("jdbc:mysql://%s:%s/%s", config.databaseHost, config
                    .databasePort, config.database), props);
            insertUpstream = con.prepareStatement("INSERT INTO `UpstreamStats` VALUES (?, ?, ?, NOW(), ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=NOW(),`count`=VALUES(`count`),`size`=VALUES(`size`),`active_connections`=VALUES(`active_connections`);");
            insertDownstream = con.prepareStatement("INSERT INTO `DownstreamStats` VALUES (?, ?, ?, NOW(), ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=NOW(),`count`=VALUES(`count`),`size`=VALUES(`size`);");
            
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private long getTimeSlot(long time) {
        long diff = time % slotLength;
        time -= diff;
        return time;
    }
    
    private void process() {
        long currentTimeSlot = getTimeSlot(System.currentTimeMillis());
        
        // do database update only when the timeslot rolls over, or the update time elapses
        if (currentTimeSlot != lastTimeSlot || System.currentTimeMillis() - lastUpdateTime >= updateInterval) {
            // Get stats
            PerformanceStatistics stats;
            if (currentTimeSlot != lastTimeSlot)
                stats = tracker.getAndResetStatistics();
            else
                stats = tracker.getStatistics();
            
            plugin.getProxy().getScheduler().runAsync(plugin, () -> recordStats(stats, currentTimeSlot));
            
            lastUpdateTime = System.currentTimeMillis();
            lastTimeSlot = currentTimeSlot;
        }
    }
    
    private void addUpPackets(List<PacketInfo> packets, Map<Integer, Map<Integer, StoredPacketInfo>> dest) {
        for (PacketInfo packet : packets) {
            Map<Integer, StoredPacketInfo> idMap = dest.computeIfAbsent(packet.getProtocol(), k -> Maps.newHashMap());
            
            StoredPacketInfo info = idMap.get(packet.getId());
            if (info == null) {
                info = new StoredPacketInfo();
                idMap.put(packet.getId(), info);
            }
            
            info.add(packet);
        }
    }
    
    private void recordStats(PerformanceStatistics stats, long timeSlot) {
        Timestamp timeSlotDate = new Timestamp(timeSlot);
        
        recordUpstream(stats.getUpstreamStats(), timeSlotDate);
        recordDownstream(stats.getDownstreamStats(), timeSlotDate);
    }
    
    private void recordUpstream(List<UpstreamStatistics> stats, Timestamp timeSlot) {
        Map<Integer, Map<Integer, StoredPacketInfo>> totalUpstream = Maps.newHashMap();
        
        // Record upstream stats
        for (UpstreamStatistics upstream : stats) {
            addUpPackets(upstream.getPackets(), totalUpstream);
        }
        
        try {
            for (int protocol : totalUpstream.keySet()) {
                Map<Integer, StoredPacketInfo> idMap = totalUpstream.get(protocol);
                for (int packetId : idMap.keySet()) {
                    StoredPacketInfo info = idMap.get(packetId);
                    
                    if (info.inboundCount != 0) {
                        insertUpstream.setInt(1, protocol);
                        insertUpstream.setInt(2, packetId);
                        insertUpstream.setTimestamp(3, timeSlot);
                        insertUpstream.setString(4, "INBOUND");
                        insertUpstream.setInt(5, info.inboundCount);
                        insertUpstream.setLong(6, info.inboundSize);
                        insertUpstream.setInt(7, info.connectionCount);
                        
                        insertUpstream.addBatch();
                    }
                    
                    if (info.outboundCount != 0) {
                        insertUpstream.setInt(1, protocol);
                        insertUpstream.setInt(2, packetId);
                        insertUpstream.setTimestamp(3, timeSlot);
                        insertUpstream.setString(4, "OUTBOUND");
                        insertUpstream.setInt(5, info.outboundCount);
                        insertUpstream.setLong(6, info.outboundSize);
                        insertUpstream.setInt(7, info.connectionCount);
                        
                        insertUpstream.addBatch();
                    }
                }
            }
            if (con.isValid(10)) {
                insertUpstream.executeBatch();
            } else {
                plugin.getLogger().warning("Perfmon has no valid db connection.....");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void recordDownstream(List<DownstreamStatistics> stats, Timestamp timeSlot) {
        try {
            for (DownstreamStatistics downstream : stats) {
                for (PacketInfo info : downstream.getPackets()) {
                    if (info.getInboundCount() != 0) {
                        insertDownstream.setInt(1, info.getProtocol());
                        insertDownstream.setInt(2, info.getId());
                        insertDownstream.setTimestamp(3, timeSlot);
                        insertDownstream.setString(4, downstream.getServerId().getName());
                        insertDownstream.setString(5, "INBOUND");
                        insertDownstream.setInt(6, info.getInboundCount());
                        insertDownstream.setLong(7, info.getInboundSize());
                        
                        insertDownstream.addBatch();
                    }
                    
                    if (info.getOutboundCount() != 0) {
                        insertDownstream.setInt(1, info.getProtocol());
                        insertDownstream.setInt(2, info.getId());
                        insertDownstream.setTimestamp(3, timeSlot);
                        insertDownstream.setString(4, downstream.getServerId().getName());
                        insertDownstream.setString(5, "OUTBOUND");
                        insertDownstream.setInt(6, info.getOutboundCount());
                        insertDownstream.setLong(7, info.getOutboundSize());
                        
                        insertDownstream.addBatch();
                    }
                }
            }
            if (con.isValid(10)) {
                insertDownstream.executeBatch();
            } else {
                plugin.getLogger().warning("Perfmon has no valid db connection.....");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    static class PerfmonConfig extends YamlConfig {
        @Comment("The interval in minutes between recording stats in the database")
        final
        int updateInterval = 5;
        @Comment("The recorded timeslot size in minutes.")
        final
        int timeslotLength = 60;
        final String databaseHost = "localhost";
        final int databasePort = 3306;
        final String database = "packetstats";
        final String databaseUsername = "packetstats";
        final String databasePassword = "packetstats";
        final Boolean databaseSSL = false;
        PerfmonConfig(File file) {
            CONFIG_FILE = file;
        }
        
    }
    
    private static class StoredPacketInfo {
        private int connectionCount;
        
        private int inboundCount;
        private int outboundCount;
        
        private long inboundSize;
        private long outboundSize;
        
        void add(PacketInfo packet) {
            inboundCount += packet.getInboundCount();
            inboundSize += packet.getInboundSize();
            outboundCount += packet.getOutboundCount();
            outboundSize += packet.getOutboundSize();
            
            ++connectionCount;
        }
    }
}
