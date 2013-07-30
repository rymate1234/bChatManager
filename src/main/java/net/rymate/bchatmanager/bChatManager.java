package net.rymate.bchatmanager;

import net.milkbowl.vault.chat.Chat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import net.rymate.bchatmanager.metrics.Metrics;

import java.io.IOException;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

// lel factions
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.UPlayer;

/**
 * Main class
 *
 * @author rymate
 */
public class bChatManager extends JavaPlugin {

    public static Chat chat = null;
    private bChatListener listener;
    public YamlConfiguration config;
    boolean factions = false;

    public void onEnable() {
        //setup the config
        setupConfig();

        //Chatlistener - can you hear me?
        this.listener = new bChatListener(this);
        this.getServer().getPluginManager().registerEvents(listener, this);

        //Vault chat hooks
        setupChat();


        //setup the Metrics
        Metrics metrics;
        try {
            metrics = new Metrics(this);
            metrics.start();
        } catch (IOException ex) {
            Logger.getLogger(bChatManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        //check if factions is installed
        if (this.getServer().getPluginManager().isPluginEnabled("Factions")) {
            factions = true;
        }

        System.out.println("[bChatManager] Enabled!");
    }

    private void setupConfig() {
        File configFile = new File(this.getDataFolder() + File.separator + "config.yml");
        try {
            if (!configFile.exists()) {
                this.saveDefaultConfig();
            }
        } catch (Exception ex) {
            Logger.getLogger(bChatManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        config = new YamlConfiguration();
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /*
     * Code to setup the Chat variable in Vault. Allows me to hook to all the prefix plugins.
     */
    private boolean setupChat() {
        RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }

        return (chat != null);
    }

    //
    //  Begin methods from Functions.java
    //
    public String replacePlayerPlaceholders(Player player, String format) {
        String worldName = player.getWorld().getName();
        if (factions) {
            format = format.replace("%faction", this.getFaction(player));
        }
        return format.replace("%prefix", chat.getPlayerPrefix(player))
                .replace("%suffix", chat.getPlayerSuffix(player))
                .replace("%world", worldName)
                .replace("%player", player.getName())
                .replace("%displayname", player.getDisplayName())
                .replace("%group", chat.getPrimaryGroup(player));
    }

    public String colorize(String string) {
        if (string == null) {
            return "";
        }
        return string.replaceAll("&([a-z0-9])", "\u00A7$1");
    }

    public List<Player> getLocalRecipients(Player sender, String message, double range) {
        Location playerLocation = sender.getLocation();
        List<Player> recipients = new LinkedList<Player>();
        double squaredDistance = Math.pow(range, 2);
        for (Player recipient : getServer().getOnlinePlayers()) {
            // Recipient are not from same world
            if (!recipient.getWorld().equals(sender.getWorld())) {
                continue;
            }
            if (playerLocation.distanceSquared(recipient.getLocation()) > squaredDistance) {
                continue;
            }
            recipients.add(recipient);
        }
        return recipients;
    }

    private String getFaction(Player player) {
        String factionString = "";
        try {
            Player p = this.getServer().getPlayerExact(player.getName());
            UPlayer uplayer = UPlayer.get(p);
            Faction faction = uplayer.getFaction();
            factionString = faction.getName();

        } catch (Exception e) {
            System.out.println("Factions support failed! Disabling factions support.");
            factions = false;
        }
        return factionString;
    }

    public List<Player> getSpies() {
        List<Player> recipients = new LinkedList<Player>();
        for (Player recipient : this.getServer().getOnlinePlayers()) {
            if (recipient.hasPermission("bchatmanager.spy")) {
                recipients.add(recipient);
            }
        }
        return recipients;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ((command.getName().equals("me")) && (config.getBoolean("toggles.control-me", true))) {
            String meFormat = config.getString("formats.me-format", "* %player %message");
            Double chatRange = config.getDouble("other.chat-range", 100);
            boolean rangedMode = config.getBoolean("toggles.ranged-mode", false);
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Ya need to type something after it :P");
                return false;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "You are not an in-game player!");
                return true;
            }
            Player player = (Player) sender;
            int i;
            StringBuilder me = new StringBuilder();
            for (i = 0; i < args.length; i++) {
                me.append(args[i]);
                me.append(" ");
            }
            String meMessage = me.toString();
            String message = meFormat;
            message = colorize(message);

            if (sender.hasPermission("bchatmanager.chat.color")) {
                meMessage = colorize(meMessage);
            }

            message = message.replace("%message", meMessage).replace("%displayname", "%1$s");
            message = replacePlayerPlaceholders(player, message);

            if (rangedMode) {
                List<Player> pl = getLocalRecipients(player, message, chatRange);
                for (int j = 0; j < pl.size(); j++) {
                    pl.get(j).sendMessage(message);
                }
                sender.sendMessage(message);
                System.out.println(message);
            } else {
                getServer().broadcastMessage(message);
            }
            return true;
        }

        if ((command.getName().equals("bchatreload"))) {
            if (!(sender instanceof Player)) {
                getServer().getPluginManager().disablePlugin(this);
                getServer().getPluginManager().enablePlugin(this);
                sender.sendMessage(ChatColor.AQUA + "[bChatManager] Plugin reloaded!");
                return true;
            }

            if (sender.hasPermission("bchatmanager.reload")) {
                sender.sendMessage(ChatColor.AQUA + "[bChatManager] Wtf, you can't do this!");
                return true;
            }

            getServer().getPluginManager().disablePlugin(this);
            getServer().getPluginManager().enablePlugin(this);
            sender.sendMessage(ChatColor.AQUA + "[bChatManager] Plugin reloaded!");
            return true;
        }
        return true;
    }
}
