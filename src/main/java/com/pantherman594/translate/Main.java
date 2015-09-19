package com.pantherman594.translate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {
    private static HashMap<UUID, String> playerLang = new HashMap<>();
    private static Integer keyId = 0;
    private static HashMap<Integer, String> ids = new HashMap<>();
    private static HashMap<Integer, String> secrets = new HashMap<>();
    private static String defaultLang = "";
    private static String defaultLangFull = "";
    private static Inventory langInv;

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
        setKeys();
        langInv = open();
        String defaultConfig = this.getConfig().getString("defaultlang");
        for (Language lang : Language.values()) {
            if (defaultConfig.equals(lang.toString())) {
                defaultLang = lang.toString();
                defaultLangFull = getLangName(lang);
            }
        }
        if (defaultLang == null) {
            defaultLang = Language.ENGLISH.toString();
            defaultLangFull = getLangName(Language.ENGLISH);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        if (!event.getMessage().startsWith(this.getConfig().getString("bypassprefix"))) {
            final String initialMsg = event.getMessage();
            final String s = this.getLanguage(event.getPlayer());
            if (!s.equals(defaultLang)) {
                event.setMessage(translateMessage(event.getMessage(), s, defaultLang, 0));
            }
            final Iterator<Player> recip = event.getRecipients().iterator();
            while (recip.hasNext()) {
                final Player p = recip.next();
                if (!getLanguage(p).equals(defaultLang)) {
                    String tMsg = initialMsg;
                    if (!getLanguage(p).equals(s)) {
                        tMsg = translateMessage(initialMsg, s, getLanguage(p), 0);
                    }
                    p.sendMessage(event.getFormat());
                    p.sendMessage(event.getFormat().replace("%1$s", event.getPlayer().getDisplayName()).replace("%2$s", tMsg));
                    recip.remove();
                }
            }
        } else {
            event.setMessage(event.getMessage().substring(1));
        }
    }

    @EventHandler
    public void interact(InventoryClickEvent event) {
        if (event.getClickedInventory().getName().equals("Languages") && event.getCurrentItem() != null && event.getCurrentItem().getItemMeta() != null && event.getCurrentItem().getItemMeta().getDisplayName() != null) {
            for (Language lang : Language.values()) {
                if (getLangName(lang).equals(event.getCurrentItem().getItemMeta().getDisplayName())) {
                    playerLang.put(event.getWhoClicked().getUniqueId(), lang.toString());
                    String msg = translateMessage("Language successfully changed to " + event.getCurrentItem().getItemMeta().getDisplayName(), "en", lang.toString(), 0);
                    event.getWhoClicked().sendMessage(msg);
                    event.setCancelled(true);
                    return;
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
            return Translate.execute(message, from, to);
        } catch (Exception e) {
            if (index < 10) {
                return translateMessage(message, from, to, index + 1);
            } else {
                getLogger().warning("Translation failed. Original message: " + message);
                getLogger().warning("Error: ");
                e.printStackTrace();
            }
        }
        return message;
    }

    public String getLangName(final Language lang) {
        try {
            return (lang.getName(lang));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Inventory open() {
        int i = 0;
        Inventory inv = Bukkit.createInventory(null, 54, "Languages");
        for (Language lang : Language.values()) {
            if (!getLangName(lang).equals("Auto Detect")) {
                ItemStack langStack = new ItemStack(Material.BOOK, 1);
                ItemMeta langMeta = langStack.getItemMeta();
                langMeta.setDisplayName(getLangName(lang));
                langStack.setItemMeta(langMeta);
                inv.setItem(i, langStack);
                i++;
            }
        }
        return inv;
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.openInventory(langInv);
            } else {
                sender.sendMessage("Console can't open the language GUI!");
            }
        } else {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reset")) {
                    if (sender instanceof Player) {
                        playerLang.put(((Player) sender).getUniqueId(), defaultLang);
                        sender.sendMessage(ChatColor.GREEN + "Language reset to " + defaultLangFull);
                    }
                } else if (args[0].equalsIgnoreCase("set")) {
                    sender.sendMessage(ChatColor.RED + "Invalid language. Possible choices: ");
                    String langList = "";
                    for (Language lang : Language.values()) {
                        langList += ", " + getLangName(lang);
                    }
                    sender.sendMessage(langList.substring(2));
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /lang [reset|set <lang>]");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("set")) {
                    boolean success = false;
                    for (Language lang : Language.values()) {
                        if (!success && getLangName(lang).equalsIgnoreCase(args[1])) {
                            playerLang.put(((Player) sender).getUniqueId(), lang.toString());
                            sender.sendMessage(ChatColor.GREEN + "Language changed to " + getLangName(lang));
                            success = true;
                        }
                    }
                    if (!success) {
                        sender.sendMessage(ChatColor.RED + "Invalid language. Possible choices: ");
                        String langList = "";
                        for (Language lang : Language.values()) {
                            langList += ", " + getLangName(lang);
                        }
                        sender.sendMessage(langList.substring(2));
                    }
                }
            }
        }
        return false;
    }
}
