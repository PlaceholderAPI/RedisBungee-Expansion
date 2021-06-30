package com.helpchat.redisbungeeexpansion;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisBungeeExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Cacheable, Configurable {

    private final Map<String, Integer> servers = new ConcurrentHashMap<>();

    private int total = 0;

    private BukkitTask task;

    private final String CHANNEL = "legacy:redisbungee";

    private int fetchInterval = 60;

    private boolean registered = false;

    public RedisBungeeExpansion() {
        if (!registered) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), CHANNEL, this);
            registered = true;
        }
    }

    @Override
    public boolean register() {

        List<String> srvs = getStringList("tracked_servers");

        if (srvs != null && !srvs.isEmpty()) {
            for (String s : srvs) {
                servers.put(s, 0);
            }
        }
        return super.register();
    }

    @Override
    public String getIdentifier() {
        return "redisbungee";
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "clip";
    }

    @Override
    public String getVersion() {
        return "2.0.1";
    }

    @Override
    public Map<String, Object> getDefaults() {
        final Map<String, Object> defaults = new HashMap<>();
        defaults.put("check_interval", 30);
        defaults.put("tracked_servers", Arrays.asList("Hub", "Survival"));
        return defaults;
    }

    private void getPlayers(String server) {

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        try {

            out.writeUTF("PlayerCount");

            out.writeUTF(server);

            Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(getPlaceholderAPI(), CHANNEL, out.toByteArray());

        } catch (Exception ignored) {
        }
    }

    @Override
    public String onPlaceholderRequest(Player p, String identifier) {


        if (identifier.equalsIgnoreCase("total") || identifier.equalsIgnoreCase("all")) {
            return String.valueOf(total);
        }

        if (servers.isEmpty()) {
            servers.put(identifier, 0);
            return "0";
        }

        for (Map.Entry<String, Integer> entry : servers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(identifier)) {
                return String.valueOf(entry.getValue());
            }
        }

        servers.put(identifier, 0);
        return null;

    }


    @Override
    public void start() {

        task = new BukkitRunnable() {

            @Override
            public void run() {

                if (servers.isEmpty()) {

                    getPlayers("ALL");

                    return;
                }

                for (String server : servers.keySet()) {
                    getPlayers(server);
                }

                getPlayers("ALL");
            }
        }.runTaskTimer(getPlaceholderAPI(), 100L, 20L * fetchInterval);
    }

    @Override
    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ex) {
            }
            task = null;
        }
    }

    @Override
    public void clear() {
        servers.clear();
        if (registered) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), CHANNEL);
            Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), CHANNEL, this);
            registered = false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {

        if (!channel.equals(CHANNEL)) {
            return;
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));

        try {

            String subChannel = in.readUTF();

            if (subChannel.equals("PlayerCount")) {

                String server = in.readUTF();

                if (in.available() > 0) {

                    int count = in.readInt();

                    if (server.equals("ALL")) {
                        total = count;
                    } else {
                        servers.put(server, count);
                    }
                }


            } else if (subChannel.equals("GetServers")) {

                String[] serverList = in.readUTF().split(", ");

                if (serverList.length == 0) {
                    return;
                }

                for (String server : serverList) {

                    if (!servers.containsKey(server)) {
                        servers.put(server, 0);
                    }
                }
            }

        } catch (Exception e) {
        }
    }
}
