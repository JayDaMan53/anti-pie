package jaydon.antipie.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiPiePlugin extends JavaPlugin implements Listener {
    private VisibilityTracker tracker;
    private AntiPiePacketListener packetListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        AntiPieSettings settings = AntiPieSettings.load(getConfig());
        tracker = new VisibilityTracker(settings);
        packetListener = new AntiPiePacketListener(tracker);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> tracker.tick(Bukkit.getOnlinePlayers()), 1L, 1L);
        getLogger().info("Protecting " + settings.protectedTypes.size()
                + " block entity types with PacketEvents.");
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracker.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        tracker.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("antipie")) return false;
        if (!sender.hasPermission("antipie.reload")) {
            sender.sendMessage("You do not have permission to reload Anti Pie.");
            return true;
        }
        reloadConfig();
        AntiPieSettings settings = AntiPieSettings.load(getConfig());
        tracker.updateSettings(settings);
        sender.sendMessage("Anti Pie reloaded; protecting " + settings.protectedTypes.size() + " block entity types.");
        return true;
    }
}
