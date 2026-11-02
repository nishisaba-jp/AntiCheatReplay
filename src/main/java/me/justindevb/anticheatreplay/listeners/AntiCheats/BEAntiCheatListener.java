package me.justindevb.anticheatreplay.listeners.AntiCheats;

import me.justindevb.anticheatreplay.AntiCheatReplay;
import me.justindevb.anticheatreplay.ListenerBase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * BEAntiCheat (Bedrock players joining through Geyser).
 *
 * BEAntiCheat is not published to a Maven repository, so its events are looked up at runtime.
 * A recording starts once a check reaches Min-VL and is kept when the check reaches Save-VL
 * or when BEAntiCheat kicks the player.
 */
public class BEAntiCheatListener extends ListenerBase implements Listener {

    private static final String VIOLATION_EVENT = "jp.nishisaba.beac.api.BeacViolationEvent";
    private static final String PUNISH_EVENT = "jp.nishisaba.beac.api.BeacPunishEvent";

    private List<String> disabledRecordings = new ArrayList<>();
    private double minVl;
    private double saveVl;
    private boolean saveOnKick;

    private Method getCheck;
    private Method getVl;
    private Method getAction;

    public BEAntiCheatListener(AntiCheatReplay acReplay) {
        super(acReplay);
        loadConfig();
        register();
    }

    private void loadConfig() {
        disabledRecordings = new ArrayList<>();
        for (String s : acReplay.getConfig().getStringList("BEAntiCheat.Disabled-Recordings")) {
            disabledRecordings.add(s.toLowerCase(Locale.ROOT));
        }
        minVl = acReplay.getConfig().getDouble("BEAntiCheat.Min-VL", 2.0);
        saveVl = acReplay.getConfig().getDouble("BEAntiCheat.Save-VL", 5.0);
        saveOnKick = acReplay.getConfig().getBoolean("BEAntiCheat.Save-On-Kick", true);
    }

    private void register() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BEAntiCheat");
        if (plugin == null) {
            return;
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<? extends Event> violation = Class.forName(VIOLATION_EVENT, true, loader).asSubclass(Event.class);
            Class<? extends Event> punish = Class.forName(PUNISH_EVENT, true, loader).asSubclass(Event.class);
            getCheck = violation.getMethod("getCheck");
            getVl = violation.getMethod("getVl");
            getAction = punish.getMethod("getAction");

            Bukkit.getPluginManager().registerEvent(violation, this, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (violation.isInstance(event)) {
                            onViolation(event);
                        }
                    }, acReplay, true);
            Bukkit.getPluginManager().registerEvent(punish, this, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (punish.isInstance(event)) {
                            onPunish(event);
                        }
                    }, acReplay, true);
        } catch (ReflectiveOperationException | ClassCastException e) {
            acReplay.log("This BEAntiCheat version does not provide the events AntiCheatReplay needs: " + e, true);
        }
    }

    private void onViolation(Event event) {
        final Player p = ((PlayerEvent) event).getPlayer();
        final String check;
        final double vl;
        try {
            check = (String) getCheck.invoke(event);
            vl = (double) getVl.invoke(event);
        } catch (ReflectiveOperationException e) {
            return;
        }
        if (disabledRecordings.contains(check.toLowerCase(Locale.ROOT)) || vl < minVl) {
            return;
        }

        final UUID uuid = p.getUniqueId();
        if (!alertList.contains(uuid)) {
            alertList.add(uuid);
            startRecording(p, getReplayName(p, check));
        }
        if (saveVl > 0 && vl >= saveVl && !punishList.contains(uuid)) {
            punishList.add(uuid);
        }
    }

    private void onPunish(Event event) {
        if (!saveOnKick) {
            return;
        }
        try {
            if (!"KICK".equals(String.valueOf(getAction.invoke(event)))) {
                return;
            }
        } catch (ReflectiveOperationException e) {
            return;
        }
        final UUID uuid = ((PlayerEvent) event).getPlayer().getUniqueId();
        if (!punishList.contains(uuid)) {
            punishList.add(uuid);
        }
    }

    @Override
    public void disinit() {
        HandlerList.unregisterAll(this);
    }
}
