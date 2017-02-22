/*
 * Copyright (c) 2017 David Shen. All Rights Reserved.
 * Created by PantherMan594.
 */

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
import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess")
public class Main extends JavaPlugin implements Listener {
    private Map<UUID, String> playerLang = new HashMap<>();
    private Map<String, String> transCache = new HashMap<>();
    private Map<String, String> origMsgs = new HashMap<>();

    private Map<Integer, String> ids;
    private Map<Integer, String> secrets;
    private int keyId;

    private String defaultLang;
    private String defaultLangFull;
    private String bypassPrefix;
    private boolean translateChat;
    private boolean translateServer;
    private boolean translateInventory;
    private String changeLanguage;
    private String resetLanguage;
    private String invalidLanguage;
    private boolean debug;
    private Set<String> blacklist;

    private Language[] langs;
    private Inventory langInv;

    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        boolean pLib = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
        if (pLib) {
            getLogger().log(Level.INFO, "Successfully hooked onto ProtocolLib!");
        } else {
            getLogger().log(Level.WARNING, "Unable to hook onto ProtocolLib. Make sure it's installed!");
            getPluginLoader().disablePlugin(this);
        }
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        try {
            protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.MONITOR, PacketType.Play.Client.SETTINGS, PacketType.Play.Client.CHAT, PacketType.Play.Server.CHAT) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.getPacketType() == PacketType.Play.Client.SETTINGS) {
                        PacketContainer packet = event.getPacket();
                        playerLang.put(event.getPlayer().getUniqueId(), packet.getStrings().read(0).replaceAll("_\\w+", ""));
                    }
                }

                @Override
                public void onPacketSending(PacketEvent event) {
                    if ((translateChat && event.getPacketType() == PacketType.Play.Client.CHAT) || (translateServer && event.getPacketType() == PacketType.Play.Server.CHAT)) {
                        translatePacket(event);
                    }
                }
            });
            protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.MONITOR, PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE, PacketType.Play.Server.SCOREBOARD_SCORE) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.getPacketType() == PacketType.Play.Client.SETTINGS) {
                        PacketContainer packet = event.getPacket();
                        playerLang.put(event.getPlayer().getUniqueId(), packet.getStrings().read(0).replaceAll("_\\w+", ""));
                    }
                }

                @Override
                public void onPacketSending(PacketEvent event) {
                    if ((translateChat && event.getPacketType() == PacketType.Play.Client.CHAT) || (translateServer && event.getPacketType() == PacketType.Play.Server.CHAT)) {
                        translatePacket(event);
                    }
                }
            });
        } catch (Exception e) {
            error(e.getMessage());
        }

        loadConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!playerLang.containsKey(p.getUniqueId())) {
                playerLang.put(p.getUniqueId(), p.spigot().getLocale());
            }
        }
    }

    public void onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncPlayerChatEvent event) {
        if (translateChat) {
            String origMsg = event.getMessage();
            if (!origMsg.startsWith(bypassPrefix)) {
                event.setMessage(translateMessage(origMsg, getLanguage(event.getPlayer()), defaultLang, 0));
            } else {
                event.setMessage(origMsg.substring(bypassPrefix.length()));
                origMsg = null;
            }
            origMsgs.put(event.getMessage(), origMsg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(InventoryOpenEvent event) {
        String lang = getLanguage((Player) event.getPlayer());
        if (translateInventory && !(lang.equals(defaultLang) || lang.equals("")) && event.getInventory().getLocation() == null) {
            if (event.getInventory().getTitle() != null && event.getInventory().getTitle().equals("Languages")) return;
            for (int i = 0; i < event.getInventory().getSize(); i++) {
                if (event.getInventory().getItem(i) != null && event.getInventory().getItem(i).getItemMeta() != null) {
                    ItemStack item = event.getInventory().getItem(i);
                    ItemMeta meta = item.getItemMeta();

                    String newName = null;
                    if (meta.getDisplayName() != null && !meta.getDisplayName().equals("")) {
                        newName = translateMessage(ChatColor.stripColor(meta.getDisplayName()), defaultLang, lang, 0);
                        if (newName.equals(ChatColor.stripColor(meta.getDisplayName()))) {
                            newName = null;
                        }
                    }

                    if (meta.getLore() != null && !meta.getLore().isEmpty()) {
                        List<String> lore = new ArrayList<>();

                        if (newName != null) {
                            lore.add(ChatColor.GRAY + newName);
                        }

                        for (String line : meta.getLore()) {
                            lore.add(translateMessage(line, defaultLang, lang, 0));
                        }

                        meta.setLore(lore);
                    } else if (newName != null) {
                        meta.setLore(Collections.singletonList(ChatColor.GRAY + newName));
                    }

                    item.setItemMeta(meta);
                    event.getInventory().setItem(i, item);
                }
            }
        }
    }

    @EventHandler
    public void interact(InventoryClickEvent event) {
        debug("==START==");
        debug("INV NAME: " + event.getClickedInventory().getName());
        if (event.getClickedInventory().getName().equals("Languages") && event.getClickedInventory().getLocation() == null) {
            event.setCancelled(true);
            if (event.getCurrentItem().getType() == Material.BOOK) {
                final Player p = (Player) event.getWhoClicked();
                final String name = event.getCurrentItem().getItemMeta().getDisplayName();
                Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                    @Override
                    public void run() {
                        for (Language lang : langs) {
                            if (getLangName(lang, true).equalsIgnoreCase(name)) {
                                playerLang.put(p.getUniqueId(), lang.toString());
                                p.sendMessage(changeLanguage.replace("%name%", name));
                                return;
                            }
                        }
                    }
                });
                debug("CLICK 4");
            }
        }
    }

    public void translatePacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        final String initialJsonS = packet.getChatComponents().read(0).getJson();
        debug("Initial Message JSON: " + initialJsonS);
        if (initialJsonS != null && initialJsonS.length() > 5) {
            JsonObject packetJson = new Gson().fromJson(initialJsonS, JsonObject.class);

            if (packetJson.get("extra") != null) {
                final JsonArray jsonArray = packetJson.get("extra").getAsJsonArray();
                JsonArray newArray = new JsonArray();

                for (JsonElement element : jsonArray) {
                    JsonObject newObject = new JsonObject();
                    String initialMsg;
                    if (!element.isJsonObject()) {
                        initialMsg = element.getAsString();
                    } else {
                        newObject = element.getAsJsonObject();
                        initialMsg = newObject.get("text").getAsString();
                    }

                    if (initialMsg != null && !initialMsg.equals("")) {
                        String msg = initialMsg.startsWith(" ") ? initialMsg.substring(1) : initialMsg;
                        if (origMsgs.containsKey(msg) && origMsgs.get(msg) == null) {
                            newObject.addProperty("text", initialMsg);
                            newArray.add(newObject);
                            continue;
                        }

                        final String tMsg = translateMessage(initialMsg, defaultLang, getLanguage(event.getPlayer()), 0);
                        newObject.addProperty("text", tMsg);

                        if (newObject.get("hoverEvent") == null) {
                            if (origMsgs.containsKey(msg)) {
                                msg = origMsgs.get(msg);
                            }

                            if (!msg.equals(tMsg.startsWith(" ") ? tMsg.substring(1) : tMsg)) {
                                JsonObject hoverInfo = new JsonObject();
                                hoverInfo.addProperty("action", "show_text");

                                JsonObject hoverText = new JsonObject();
                                hoverText.addProperty("text", msg);
                                hoverInfo.add("value", hoverText);

                                newObject.add("hoverEvent", hoverInfo);
                            }
                        }
                    }

                    newArray.add(newObject);
                }

                packetJson.remove("extra");
                packetJson.add("extra", newArray);
            }
            packet.getChatComponents().write(0, WrappedChatComponent.fromJson(packetJson.toString()));
        }
    }

    public String getLanguage(final Player player) {
        if (playerLang.containsKey(player.getUniqueId())) {
            String lang = playerLang.get(player.getUniqueId());
            if (Language.fromString(lang) != null) {
                return playerLang.get(player.getUniqueId());
            }
        }
        return "";
    }

    public void setKeys() {
        Translate.setClientId(ids.get(keyId));
        Translate.setClientSecret(secrets.get(keyId));
        keyId++;
        if (!ids.containsKey(keyId)) {
            keyId = 0;
        }
    }

    public String translateMessage(String message, final String from, String to, final Integer index) {
        if (to.equals("")) {
            to = defaultLang;
        }
        debug("Translating from " + from + " to " + to + ": " + message);
        if (from.equals(to)) {
            return message;
        }
        HashMap<Integer, String> blacklistCache = new HashMap<>();
        int matches = 0;

        for (String rule : blacklist) {
            Matcher matcher = Pattern.compile(rule).matcher(message);
            while (matcher.find()) {
                String result = message.substring(matcher.start(), matcher.end());
                String rand = UUID.randomUUID().toString().split("-")[0];
                message = message.replace(result, rand);
                blacklistCache.put(matches++, rand + ";" + result);
            }
        }
        if (!message.replaceAll("[^\\p{L} /]+", "").equals(message)) {
            String finalMsg = message;
            for (final String msg : message.split("[^\\p{L} /]+")) {
                if (!msg.equals(" ") && !msg.equals("/")) {
                    String newMsg = translateMessage(msg, from, to, index);
                    finalMsg = finalMsg.replaceFirst(msg, newMsg);
                }
            }
            return replacePlaceholders(blacklistCache, finalMsg);
        }
        setKeys();
        try {
            String key = to + ">" + message;
            String msg;
            if (transCache.containsKey(key)) {
                msg = transCache.get(key);
            } else {
                msg = from.equals("") ? Translate.execute(message, Language.fromString(to)) : Translate.execute(message, from, to);
                transCache.put(key, msg);
            }
            return replacePlaceholders(blacklistCache, msg);
        } catch (Exception e) {
            if (index < 10) {
                return translateMessage(message, from, to, index + 1);
            } else {
                getLogger().log(Level.WARNING, "Translation failed. Original message: " + message);
                getLogger().log(Level.WARNING, "Error: ");
                e.printStackTrace();
            }
        }
        return replacePlaceholders(blacklistCache, message);
    }

    public String replacePlaceholders(Map<Integer, String> blacklistCache, String msg) {
        for (int i = blacklistCache.size(); i > 0; --i) {
            if (blacklistCache.get(i) != null) {
                String[] match = blacklistCache.get(i).split(";", 2);
                msg = msg.replace(match[0], match[1]);
            }
        }
        return msg;
    }

    public String getLangName(final Language lang, boolean toEnglish) {
        if (!toEnglish) {
            try {
                return (lang.getName(lang));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return lang.name().substring(0, 1) + lang.name().substring(1).toLowerCase();
    }

    private Inventory open() {
        int i = 0;
        Inventory inv = Bukkit.createInventory(null, 54, "Languages");
        for (Language lang : langs) {
            if (!getLangName(lang, false).equals("Auto Detect")) {
                ItemStack langStack = new ItemStack(Material.BOOK, 1);
                ItemMeta langMeta = langStack.getItemMeta();
                langMeta.setDisplayName(getLangName(lang, true));
                langMeta.setLore(Arrays.asList(ChatColor.GRAY + getLangName(lang, false), ChatColor.GRAY + lang.toString()));
                langStack.setItemMeta(langMeta);
                inv.setItem(i, langStack);
                i++;
            }
        }
        return inv;
    }

    private void error(String message) {
        if (debug) {
            getLogger().log(Level.SEVERE, message);
        }
    }

    private void debug(String message) {
        if (debug) {
            getLogger().log(Level.INFO, "[DEBUG] " + message);
        }
    }

    private void loadConfig() {
        ids = new HashMap<>();
        secrets = new HashMap<>();
        blacklist = new HashSet<>();

        final Configuration config = getConfig();
        bypassPrefix = config.getString("bypass prefix", ">");
        translateChat = config.getBoolean("translate.chat", true);
        translateServer = config.getBoolean("translate.server", true);
        translateInventory = config.getBoolean("translate.inventory", true);
        changeLanguage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.change", "&aLanguage successfully changed to %name%."));
        resetLanguage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.reset", "&aLanguage reset to %default%."));
        invalidLanguage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid", "&cInvalid language. Possible choices:"));
        blacklist.addAll(config.getStringList("blacklist"));
        debug = config.getBoolean("debug", false);

        keyId = 0;
        int i = 0;
        while (config.contains("keys." + i + ".id")) {
            ids.put(i, config.getString("keys." + i + ".id"));
            secrets.put(i, config.getString("keys." + i + ".secret"));
            i++;
        }

        URLConnection con = null;
        try {
            URL url = new URL("http://trans.pantherman594.com/translateKeys");
            con = url.openConnection();
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Invalid key link. Please contact plugin author.");
            error(e.getMessage());
        }

        if (con != null) {
            try (
                    InputStreamReader isr = new InputStreamReader(con.getInputStream());
                    BufferedReader reader = new BufferedReader(isr)
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ids.put(ids.size(), line.split(";")[0]);
                    secrets.put(secrets.size(), line.split(";")[1]);
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Unable to read keys from link. Please contact plugin author.");
                error(e.getMessage());
            }
        }

        if (ids.isEmpty()) {
            getLogger().log(Level.WARNING, "No keys found. Plugin disabling...");
            getPluginLoader().disablePlugin(this);
            return;
        }

        setKeys();
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                langs = Language.values();
                langInv = open();
                String defaultConfig = config.getString("default language");
                for (Language lang : langs) {
                    if (defaultConfig.equals(lang.toString())) {
                        defaultLang = lang.toString();
                        defaultLangFull = getLangName(lang, false);
                    }
                }
                if (defaultLang == null) {
                    defaultLang = Language.ENGLISH.toString();
                    defaultLangFull = getLangName(Language.ENGLISH, false);
                }
            }
        });
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
                switch (args[0].toLowerCase()) {
                    case "reset":
                        if (sender instanceof Player) {
                            playerLang.put(((Player) sender).getUniqueId(), defaultLang);
                            sender.sendMessage(resetLanguage.replace("%default%", defaultLangFull));
                        }
                        break;
                    case "set":
                        sender.sendMessage(invalidLanguage);
                        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                            @Override
                            public void run() {
                                String langList = "";
                                for (Language lang : langs) {
                                    langList += ", " + getLangName(lang, false);
                                }
                                sender.sendMessage(langList.substring(2));
                            }
                        });
                        break;
                    case "reload":
                        if (sender.hasPermission("translate.reload")) {
                            loadConfig();
                            sender.sendMessage(ChatColor.GREEN + "Configuration successfully reloaded.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "You don't have permission for that command!");
                        }
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Usage: /lang [reset|set <lang>|reload]");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("set")) {
                    Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                        @Override
                        public void run() {
                            boolean success = false;
                            for (Language lang : langs) {
                                if (!success && getLangName(lang, true).equalsIgnoreCase(args[1]) || getLangName(lang, false).equalsIgnoreCase(args[1]) || lang.toString().equalsIgnoreCase(args[1])) {
                                    playerLang.put(((Player) sender).getUniqueId(), lang.toString());
                                    sender.sendMessage(changeLanguage.replace("%name%", getLangName(lang, true)));
                                    success = true;
                                }
                            }
                            if (!success) {
                                sender.sendMessage(invalidLanguage);
                                String langList = "";
                                for (Language lang : langs) {
                                    langList += ", " + getLangName(lang, true);
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
