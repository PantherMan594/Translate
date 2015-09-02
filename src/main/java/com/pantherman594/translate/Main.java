package com.pantherman594.translate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.memetix.mst.translate.Translate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener
{
    private static HashMap<UUID, String> playerLang = new HashMap<>();
    private static Integer keyId = 0;
    private static HashMap<Integer, String> ids = new HashMap<>();
    private static HashMap<Integer, String> secrets = new HashMap<>();

    public void onEnable() {
        this.saveDefaultConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        try {
            protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.SETTINGS) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.getPacketType() == PacketType.Play.Client.SETTINGS) {
                        PacketContainer packet = event.getPacket();
                        playerLang.put(event.getPlayer().getUniqueId(), packet.getStrings().read(0).replaceAll("_\\w+", ""));
                    }
                }
            });
        } catch (Exception ignored) {}
        int i = 0;
        while (this.getConfig().contains("keys." + i + ".id")) {
            ids.put(i, this.getConfig().getString("keys." + i + ".id"));
            secrets.put(i, this.getConfig().getString("keys." + i + ".secret"));
            i++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        if (!event.getMessage().startsWith(">")) {
            final String s = this.getLanguage(event.getPlayer());
            for (Player p : getServer().getOnlinePlayers()) {
                if (!getLanguage(p).equals(s)) {
                    String tMsg = translateMessage(event.getMessage(), s, getLanguage(p), event.getPlayer().getDisplayName(), 0);
                    if (tMsg != null) {
                        p.sendMessage(tMsg);
                    }
                }
            }
        } else {
            event.setMessage(event.getMessage().substring(1));
        }
    }

    public String getLanguage(final Player player) {
        if (playerLang.containsKey(player.getUniqueId())) {
            return playerLang.get(player.getUniqueId());
        }
        return "en";
    }

    public void setKeys() {
        Translate.setClientId(ids.get(keyId));
        Translate.setClientSecret(secrets.get(keyId));
        keyId++;
        if (!ids.containsKey(keyId)) {
            keyId = 0;
        }
    }

    public String translateMessage(final String message, final String from, final String to, final String name, final Integer index) {
        setKeys();
        try {
            return "> " + name + ": " + Translate.execute(message, from, to);
        } catch (Exception e) {
            if (index < 10) {
                translateMessage(message, from, to, name, index);
            } else {
                getLogger().warning("Translation failed. Original message: " + message);
                getLogger().warning("Error: ");
                e.printStackTrace();
            }
        }
        return null;
    }
}
