package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

public class MacePlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Wind charge direction.")
        .defaultValue(Mode.WindJump)
        .build()
    );

    private final Setting<Double> upwardVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("upward-velocity")
        .description("Vertical boost.")
        .defaultValue(2.5)
        .range(0.5, 5.0)
        .sliderRange(0.5, 5.0)
        .build()
    );

    private final Setting<Double> forwardVelocity = sgGeneral.add(new DoubleSetting.Builder()
        .name("forward-velocity")
        .description("Forward boost.")
        .defaultValue(2.5)
        .range(0.5, 5.0)
        .sliderRange(0.5, 5.0)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Seconds between boosts.")
        .defaultValue(1.0)
        .range(0.1, 5.0)
        .sliderRange(0.1, 5.0)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Switches only server-side.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-ground")
        .description("Only boosts while on the ground.")
        .defaultValue(true)
        .build()
    );

    private final SynTimer timer = new SynTimer();

    public MacePlus() {
        super(SynAddon.CATEGORY, "MacePlus", "Boosts the player using wind charges.");
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!timer.passed(delay.get() * 1000.0)) return;
        if (onlyGround.get() && !mc.player.isOnGround()) return;

        int slot = SynUtils.findItem(Items.WIND_CHARGE);
        if (slot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        if (silentSwitch.get()) {
            sendSlotPacket(slot);
        } else {
            SynUtils.switchToSlot(slot);
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        switch (mode.get()) {
            case WindJump -> mc.player.addVelocity(0.0, upwardVelocity.get(), 0.0);
            case Forward -> {
                double yaw = Math.toRadians(mc.player.getYaw());
                double velocity = forwardVelocity.get();
                double x = -Math.sin(yaw) * velocity;
                double z = Math.cos(yaw) * velocity;
                mc.player.addVelocity(x, 0.0, z);
            }
            case Smash -> mc.player.addVelocity(0.0, -upwardVelocity.get(), 0.0);
        }

        if (silentSwitch.get()) {
            sendSlotPacket(prevSlot);
        } else {
            SynUtils.switchToSlot(prevSlot);
        }
        timer.reset();
    }

    private static void sendSlotPacket(int slot) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public enum Mode {
        WindJump,
        Forward,
        Smash
    }
}
