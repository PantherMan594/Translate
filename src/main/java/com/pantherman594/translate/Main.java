package com.pantherman594.translate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.massivecraft.factions.chat.tag.ChatTagRelcolor;
import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
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
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {
    private HashMap<UUID, String> playerLang = new HashMap<>();
    private HashMap<String, String> transCache = new HashMap<>();
    private Integer keyId = 0;
    private HashMap<Integer, String> ids = new HashMap<>();
    private HashMap<Integer, String> secrets = new HashMap<>();
    private String defaultLang = "";
    private String defaultLangFull = "";
    private Language[] langs;
    private Inventory langInv;
    private boolean factions;
    private boolean ignoreCommands;

    public void onEnable() {
        this.saveDefaultConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
        boolean pLib = Bukkit.getServer().getPluginManager().getPlugin("ProtocolLib") != null;
        if (pLib) {
            getLogger().info("Successfully hooked onto ProtocolLib!");
        }
        factions = Bukkit.getServer().getPluginManager().getPlugin("Factions") != null;
        if (factions) {
            getLogger().info("Successfully hooked onto Factions!");
        }
        if (pLib) {
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
            } catch (Exception ignored) {
            }
            if (Boolean.valueOf(this.getConfig().getString("translateall"))) {
                try {
                    protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.CHAT) {
                        @Override
                        public void onPacketSending(PacketEvent event) {
                            if (event.getPacketType() == PacketType.Play.Server.CHAT && !getLanguage(event.getPlayer()).equals(defaultLang)) {
                                PacketContainer packet = event.getPacket();
                                String initialJsonS = packet.getChatComponents().read(0).getJson();
                                final JsonObject initialJson = new Gson().fromJson(initialJsonS, JsonObject.class);
                                if (initialJson.get("extra") != null) {
                                    final JsonArray jsonArray = initialJson.get("extra").getAsJsonArray();
                                    for (JsonElement element : jsonArray) {
                                        final String initialMsg = element.getAsJsonObject().get("text").getAsString();

                                        final String tMsg = translateMessage(initialMsg, "", getLanguage(event.getPlayer()), 0);
                                        initialJsonS = initialJsonS.replace(initialMsg, tMsg);
                                    }
                                } else {
                                    final String initialMsg = initialJson.get("text").getAsString();

                                    final String tMsg = translateMessage(initialMsg, "", getLanguage(event.getPlayer()), 0);
                                    initialJsonS = initialJsonS.replace(initialMsg, tMsg);
                                }
                                packet.getChatComponents().write(0, WrappedChatComponent.fromJson(initialJsonS));
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }
        int i = 0;
        while (this.getConfig().contains("keys." + i + ".id")) {
            ids.put(i, this.getConfig().getString("keys." + i + ".id"));
            secrets.put(i, this.getConfig().getString("keys." + i + ".secret"));
            i++;
        }
        ignoreCommands = Boolean.valueOf(this.getConfig().getString("ignorecommands"));
        setKeys();
        final Configuration config = this.getConfig();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                langs = Language.values();
                langInv = open();
                String defaultConfig = config.getString("defaultlang");
                for (Language lang : langs) {
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
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        if (!event.isCancelled()) {
            if (!event.getMessage().startsWith(this.getConfig().getString("bypassprefix"))) {
                final String initialMsg = event.getMessage();
                final String initialFmt = event.getFormat();
                final String s = this.getLanguage(event.getPlayer());
                if (!s.equals(defaultLang)) {
                    event.setMessage(translateMessage(event.getMessage(), s, defaultLang, 0));
                }
                for (Player p : event.getRecipients()) {
                    String tMsg = initialMsg;
                    if (factions) {
                        String rC = ChatTagRelcolor.get().getReplacement(event.getPlayer(), p);
                        event.setFormat(initialFmt.replaceAll("\\{[^_]+_relcolor\\}", rC));
                    }
                    if (!getLanguage(p).equals(s)) {
                        tMsg = translateMessage(initialMsg, s, getLanguage(p), 0);
                        try {
                            Class.forName("net.md_5.bungee.api.chat.TextComponent");
                            String format = event.getFormat().replace("%1$s", event.getPlayer().getDisplayName());
                            TextComponent finalMsg = new TextComponent(TextComponent.fromLegacyText(format.replaceAll("%2\\$s.*", "")));
                            TextComponent msg = new TextComponent(TextComponent.fromLegacyText(tMsg));
                            msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(initialMsg).create()));
                            TextComponent msg2 = new TextComponent(TextComponent.fromLegacyText(format.replaceAll(".*(?=%2\\$s.*)%2\\$s", "")));
                            finalMsg.addExtra(msg);
                            finalMsg.addExtra(msg2);
                            p.spigot().sendMessage(finalMsg);
                        } catch (Exception e) {
                            p.sendMessage(event.getFormat().replace("%1$s", event.getPlayer().getDisplayName()).replace("%2$s", tMsg));
                        }
                    } else {
                        p.sendMessage(event.getFormat().replace("%1$s", event.getPlayer().getDisplayName()).replace("%2$s", tMsg));
                    }
                }
                if (factions) {
                    String rC = ChatTagRelcolor.get().getReplacement(event.getPlayer(), Bukkit.getConsoleSender());
                    event.setFormat(initialFmt.replaceAll("\\{[^_]+_relcolor\\}", rC));
                }
                Bukkit.getConsoleSender().sendMessage(event.getFormat().replace("%1$s", event.getPlayer().getDisplayName()).replace("%2$s", event.getMessage()));
                event.setCancelled(true);
            } else {
                event.setMessage(event.getMessage().substring(1));
            }
        }
    }

    @EventHandler
    public void interact(InventoryClickEvent event) {
        if (event.getClickedInventory().getName().equals("Languages") && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BOOK) {
            final Player p = (Player) event.getWhoClicked();
            final String name = event.getCurrentItem().getItemMeta().getDisplayName();
            event.setCancelled(true);
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    for (Language lang : langs) {
                        if (getLangName(lang).equals(name)) {
                            playerLang.put(p.getUniqueId(), lang.toString());
                            String msg = translateMessage("Language successfully changed to " + name, "en", lang.toString(), 0);
                            p.sendMessage(msg);
                            return;
                        }
                    }
                }
            });
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

    public String translateMessage(String message, final String from, final String to, final Integer index) {
        if (message.replaceAll("\\W", "").equals("")) {
            return message;
        }
        setKeys();
        try {
            HashMap<Integer, String> commandCache = new HashMap<>();
            if (ignoreCommands && message.contains("/")) {
                String[] words = message.split(" ");
                int i = 0;
                for (String word : words) {
                    if (word.startsWith("/")) {
                        Integer rand = 573 + i++;
                        commandCache.put(rand, word);
                        message = message.replace(word, "" + rand);
                    }
                }
            }
            String key = from + "-" + to + ">" + message;
            String msg;
            if (transCache.containsKey(key)) {
                msg = transCache.get(key);
            } else {
                msg = from.equals("") ? Translate.execute(message, Language.fromString(to)) : Translate.execute(message, from, to);
                transCache.put(key, msg);
            }
            for (Integer rand : commandCache.keySet()) {
                msg = msg.replace("" + rand, commandCache.get(rand));
            }
            return msg;
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
        for (Language lang : langs) {
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
                    Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                        @Override
                        public void run() {
                            String langList = "";
                            for (Language lang : langs) {
                                langList += ", " + getLangName(lang);
                            }
                            sender.sendMessage(langList.substring(2));
                        }
                    });
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /lang [reset|set <lang>]");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("set")) {
                    Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                        @Override
                        public void run() {
                            boolean success = false;
                            for (Language lang : langs) {
                                if (!success && getLangName(lang).equalsIgnoreCase(args[1])) {
                                    playerLang.put(((Player) sender).getUniqueId(), lang.toString());
                                    sender.sendMessage(ChatColor.GREEN + "Language changed to " + getLangName(lang));
                                    success = true;
                                }
                            }
                            if (!success) {
                                sender.sendMessage(ChatColor.RED + "Invalid language. Possible choices: ");
                                String langList = "";
                                for (Language lang : langs) {
                                    langList += ", " + getLangName(lang);
                                }
                                sender.sendMessage(langList.substring(2));
                            }
                        }
                    });
                }
            }
        }
        return false;
    }
}
