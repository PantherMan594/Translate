package net.cubexmc.translate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.java.*;

import org.bukkit.event.player.*;

import com.memetix.mst.translate.Translate;

import org.bukkit.event.*;
import org.bukkit.entity.*;

import java.util.*;

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
                        getLogger().info(event.getPlayer().getName() + "'s language: " + packet.getStrings().read(0).replaceAll("_\\w+", ""));
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
        final String s = this.getLanguage(event.getPlayer());
        for (Player p : getServer().getOnlinePlayers()) {
            if (!getLanguage(p).equals(s)) {
                String tMsg = translateMessage(event.getMessage(), s, getLanguage(p), 0);
                if (tMsg != null) {
                    p.sendMessage(tMsg);
                }
            }
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

    public String translateMessage(final String message, final String from, final String to, final Integer index) {
        setKeys();
        try {
            return "Translated: " + Translate.execute(message, from, to);
        } catch (Exception e) {
            if (index < 10) {
                translateMessage(message, from, to, index);
            } else {
                getLogger().warning("Translation failed. Original message: " + message);
                getLogger().warning("Error: ");
                e.printStackTrace();
            }
        }
        return null;
    }
}
