package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of Forever's AutoEZ module with ALL offensive, sexual, and vulgar
 * content removed.  This module only sends safe, neutral English log
 * messages about totem pops, disconnects, and ender-pearl escapes.
 */
public class AutoEZ extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Page> page = sgGeneral.add(new EnumSetting.Builder<Page>()
        .name("page")
        .description("Which behaviour to configure.")
        .defaultValue(Page.Death)
        .build()
    );

    private final Setting<DeathType> deathType = sgGeneral.add(new EnumSetting.Builder<DeathType>()
        .name("death-type")
        .description("Message style when a player dies.")
        .defaultValue(DeathType.Bot)
        .visible(() -> page.get() == Page.Death)
        .build()
    );

    private final Setting<String> customDeathMessage = sgGeneral.add(new StringSetting.Builder()
        .name("custom-death-message")
        .description("Custom death message. Use %player% and %totems% as placeholders.")
        .defaultValue("EZ %player% popped %totems% totems")
        .visible(() -> page.get() == Page.Death && deathType.get() == DeathType.Custom)
        .build()
    );

    private final Setting<Boolean> totemAnnounce = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-announce")
        .description("Announce totem pops for tracked players.")
        .defaultValue(true)
        .visible(() -> page.get() == Page.Totem)
        .build()
    );

    private final Setting<Integer> totemDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("totem-delay-ms")
        .description("Delay before a totem announcement is sent.")
        .defaultValue(1500)
        .range(0, 5000)
        .sliderRange(0, 5000)
        .visible(() -> page.get() == Page.Totem && totemAnnounce.get())
        .build()
    );

    private final Setting<Boolean> logAnnounce = sgGeneral.add(new BoolSetting.Builder()
        .name("log-announce")
        .description("Announce when a tracked player disconnects.")
        .defaultValue(true)
        .visible(() -> page.get() == Page.Disconnect)
        .build()
    );

    private final Setting<Boolean> pearlAnnounce = sgGeneral.add(new BoolSetting.Builder()
        .name("pearl-announce")
        .description("Announce when a tracked player teleports away.")
        .defaultValue(true)
        .visible(() -> page.get() == Page.Pearl)
        .build()
    );

    private final Setting<SendMode> sendMode = sgGeneral.add(new EnumSetting.Builder<SendMode>()
        .name("send-mode")
        .description("How the message is sent.")
        .defaultValue(SendMode.Public)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance at which players are tracked. 0 = unlimited.")
        .defaultValue(10.0)
        .min(0.0)
        .max(20.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Integer> randomLength = sgGeneral.add(new IntSetting.Builder()
        .name("random-length")
        .description("Random alphabetical suffix appended to each message.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> streakTimeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("streak-timeout")
        .description("Seconds before the kill-streak counter resets.")
        .defaultValue(300.0)
        .min(5.0)
        .max(600.0)
        .sliderRange(5.0, 600.0)
        .build()
    );

    private Long lastKillTime;
    private int killStreak;

    private final Map<String, Integer> totemCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastTotemTime = new ConcurrentHashMap<>();
    private final Map<String, Boolean> announceScheduled = new ConcurrentHashMap<>();

    private final Random random = new Random();

    private static final List<String> SAFE_BOT_MESSAGES = List.of(
        "Bot detected:",
        "Probably a bot:",
        "I am not a bot",
        "Nice aim",
        "Skill issue",
        "Mouse aim is better"
    );

    private static final List<String> SAFE_TOTEM_MESSAGES = List.of(
        "%player% popped %totems% totems",
        "%player% used %totems% totems and is still alive",
        "%totems% totems and counting for %player%",
        "%player% — totem count: %totems%"
    );

    private static final List<String> SAFE_DISCONNECT_MESSAGES = List.of(
        "%player% disconnected with %totems% totems used",
        "%player% rage quit after %totems% totems",
        "%player% bailed with %totems% totems on the board",
        "%player% left — %totems% totems gone"
    );

    private static final List<String> SAFE_PEARL_MESSAGES = List.of(
        "%player% pearled away with %totems% totems",
        "%player% teleported out after %totems% totems",
        "%player% escaped via pearl (%totems% totems)",
        "Peacefully departed: %player% with %totems% totems"
    );

    private static final String KILL_STREAK_FORMAT = "@%s, %d kills in a row!";

    public AutoEZ() {
        super(SynAddon.CATEGORY, "AutoEZ", "Announces totem pops, disconnects, and escapes.");
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (lastKillTime != null
            && System.currentTimeMillis() - lastKillTime > (long) (streakTimeout.get() * 1000.0)) {
            killStreak = 0;
            lastKillTime = null;
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (mc.player == null || mc.world == null) return;
        Entity entity = event.entity;
        if (!(entity instanceof PlayerEntity player)) return;
        if (player == mc.player) return;
        if (player.isDead() || player.getHealth() <= 0.0f) return;

        String name = player.getName().getString();
        int totems = totemCounts.getOrDefault(name, 0);

        boolean stillOnList = false;
        if (mc.getNetworkHandler() != null) {
            stillOnList = mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) != null;
        }

        if (stillOnList) {
            if (pearlAnnounce.get()) sendPearlMessage(name, totems);
        } else {
            if (logAnnounce.get()) sendDisconnectMessage(name, totems);
        }

        totemCounts.remove(name);
        lastTotemTime.remove(name);
        announceScheduled.remove(name);
    }

    public void onTotemPopped(PlayerEntity player) {
        if (mc.player == null || player == mc.player) return;
        if (player.isDead()) return;
        if (range.get() > 0.0 && mc.player.distanceTo(player) > range.get()) return;

        String name = player.getName().getString();
        int count = totemCounts.getOrDefault(name, 0) + 1;
        totemCounts.put(name, count);
        lastTotemTime.put(name, System.currentTimeMillis());

        if (!totemAnnounce.get()) return;
        if (Boolean.TRUE.equals(announceScheduled.get(name))) return;

        announceScheduled.put(name, true);
        int finalCount = count;
        long delayMs = totemDelayMs.get();

        Thread.ofVirtual().start(() -> {
            try {
                while (System.currentTimeMillis() - lastTotemTime.getOrDefault(name, 0L) < delayMs) {
                    Thread.sleep(delayMs - (System.currentTimeMillis() - lastTotemTime.getOrDefault(name, 0L)));
                }
                if (totemCounts.containsKey(name) && Boolean.TRUE.equals(announceScheduled.get(name))) {
                    sendTotemMessage(name, finalCount);
                }
            } catch (InterruptedException ignored) {
            } finally {
                announceScheduled.put(name, false);
            }
        });
    }

    public void onPlayerKilled(PlayerEntity player) {
        if (mc.player == null || player == mc.player) return;
        if (range.get() > 0.0 && mc.player.distanceTo(player) > range.get()) return;

        String name = player.getName().getString();
        int totems = totemCounts.getOrDefault(name, 0);

        totemCounts.remove(name);
        lastTotemTime.remove(name);
        announceScheduled.remove(name);

        int streak = advanceStreak();
        String message;
        switch (deathType.get()) {
            case Bot -> {
                String prefix = SAFE_BOT_MESSAGES.get(random.nextInt(SAFE_BOT_MESSAGES.size()));
                message = prefix + " " + name;
            }
            case Custom -> message = customDeathMessage.get();
            case KillStreak -> {
                if (streak <= 6) {
                    String[] phrases = {"First blood!", "Double kill!", "Triple!", "Quadra!", "Penta!", "Hexa kill!"};
                    message = String.format("@%s — %s", name, phrases[Math.min(streak - 1, phrases.length - 1)]);
                } else {
                    message = String.format(KILL_STREAK_FORMAT, name, streak);
                }
            }
            default -> message = "EZ " + name;
        }

        formatAndSend(message, name, totems);
    }

    private void sendTotemMessage(String playerName, int totems) {
        String template = SAFE_TOTEM_MESSAGES.get(random.nextInt(SAFE_TOTEM_MESSAGES.size()));
        formatAndSend(template, playerName, totems);
    }

    private void sendDisconnectMessage(String playerName, int totems) {
        String template = SAFE_DISCONNECT_MESSAGES.get(random.nextInt(SAFE_DISCONNECT_MESSAGES.size()));
        formatAndSend(template, playerName, totems);
    }

    private void sendPearlMessage(String playerName, int totems) {
        String template = SAFE_PEARL_MESSAGES.get(random.nextInt(SAFE_PEARL_MESSAGES.size()));
        formatAndSend(template, playerName, totems);
    }

    private void formatAndSend(String template, String playerName, int totems) {
        String msg = template
            .replace("%player%", playerName)
            .replace("%s", playerName)
            .replace("%totems%", String.valueOf(totems))
            .replace("%totem%", String.valueOf(totems))
            .replace("%t", String.valueOf(totems));

        int extraLen = randomLength.get();
        if (extraLen > 0) msg = msg + " " + randomString(extraLen);

        sendChat(msg, playerName);
    }

    private void sendChat(String message, String targetName) {
        if (mc.getNetworkHandler() == null) return;
        switch (sendMode.get()) {
            case Public -> mc.getNetworkHandler().sendChatMessage(message);
            case Tell -> mc.getNetworkHandler().sendChatCommand("tell " + targetName + " " + message);
            case Both -> {
                mc.getNetworkHandler().sendChatMessage(message);
                mc.getNetworkHandler().sendChatCommand("tell " + targetName + " " + message);
            }
        }
    }

    private int advanceStreak() {
        long now = System.currentTimeMillis();
        if (lastKillTime != null && now - lastKillTime > (long) (streakTimeout.get() * 1000.0)) {
            killStreak = 0;
        }
        killStreak++;
        lastKillTime = now;
        return killStreak;
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }

    public void reset() {
        killStreak = 0;
        lastKillTime = null;
        totemCounts.clear();
        lastTotemTime.clear();
        announceScheduled.clear();
    }

    public enum Page { Death, Totem, Disconnect, Pearl }
    public enum DeathType { Bot, Custom, KillStreak }
    public enum SendMode { Public, Tell, Both }
}
