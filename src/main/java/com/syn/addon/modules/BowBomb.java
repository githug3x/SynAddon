package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Port of Forever's BowBomb exploit. When the player releases a bow,
 * sends a spoofed sequence of movement packets to lag the server
 * and detonate the charged arrow at the target's feet.
 */
public class BowBomb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ExploitMode> exploit = sgGeneral.add(new EnumSetting.Builder<ExploitMode>()
        .name("exploit")
        .description("The spoof sequence used to lag the server.")
        .defaultValue(ExploitMode.Strong)
        .build()
    );

    private final Setting<Boolean> aimbot = sgGeneral.add(new BoolSetting.Builder()
        .name("aimbot")
        .description("Automatically aims at a valid target while charging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("predict-ticks")
        .description("Ticks to predict the target's position ahead.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> rotation = sgGeneral.add(new BoolSetting.Builder()
        .name("rotation")
        .description("Includes yaw/pitch in each spoofed packet.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> spoofs = sgGeneral.add(new IntSetting.Builder()
        .name("spoofs")
        .description("Number of spoofed packet pairs sent on trigger.")
        .defaultValue(50)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> minimize = sgGeneral.add(new BoolSetting.Builder()
        .name("minimize")
        .description("Uses a minimal Y offset instead of the standard epsilon.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> message = sgGeneral.add(new BoolSetting.Builder()
        .name("message")
        .description("Announces when the exploit triggers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Cooldown between triggers in seconds.")
        .defaultValue(5.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> activeTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("active-time")
        .description("How long the exploit remains armed after detecting a release, in seconds.")
        .defaultValue(0.4)
        .min(0.0)
        .max(3.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    private final SynTimer exploitCooldown = new SynTimer();
    private final SynTimer messageTimer = new SynTimer();
    private final Random random = new Random();

    private boolean usingBow;

    public BowBomb() {
        super(SynAddon.CATEGORY, "BowBomb", "Spoofs movement packets to detonate a charged bow shot.");
    }

    @Override
    public void onDeactivate() {
        usingBow = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.BOW) {
            usingBow = true;
        } else {
            messageTimer.reset();
            usingBow = false;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (!exploitCooldown.passed(delay.get() * 1000.0)) return;

        if (!usingBow) return;
        if (!messageTimer.passed(activeTime.get() * 1000.0)) return;

        if (!(event.packet instanceof PlayerActionC2SPacket action)) return;
        if (action.getAction() != PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) return;

        if (message.get()) info("BowBomb trigger.");

        mc.player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING)
        );

        int count = spoofs.get();
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        double eps = minimize.get() ? 0.0 : 1.0E-10;

        switch (exploit.get()) {
            case Fast -> {
                for (int i = 0; i < count; i++) {
                    sendMove(x, y + 1.0E-10, z, true, yaw, pitch);
                    sendMove(x, y + eps, z, false, yaw, pitch);
                }
            }
            case Strong -> {
                for (int i = 0; i < count; i++) {
                    sendMove(x, y + 1.0E-10, z, false, yaw, pitch);
                    sendMove(x, y - eps, z, true, yaw, pitch);
                }
            }
            case Phobos -> {
                for (int i = 0; i < count; i++) {
                    sendMove(x, y + 1.3E-13, z, true, yaw, pitch);
                    sendMove(x, y + 2.7E-13, z, false, yaw, pitch);
                }
            }
            case Strict -> {
                double[] dir = new double[]{
                    100.0 * -Math.sin(Math.toRadians(yaw)),
                    100.0 * Math.cos(Math.toRadians(yaw))
                };
                for (int i = 0; i < count; i++) {
                    if (random.nextBoolean()) {
                        sendMove(x - dir[0], y, z - dir[1], false, yaw, pitch);
                    } else {
                        sendMove(x + dir[0], y, z + dir[1], true, yaw, pitch);
                    }
                }
            }
        }

        exploitCooldown.reset();
    }

    @EventHandler
    private void onAimbotTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!aimbot.get()) return;
        if (!mc.player.isUsingItem()) return;
        if (mc.player.getActiveItem().getItem() != Items.BOW) return;

        PlayerEntity target = findTarget();
        if (target == null) return;

        int ticks = predictTicks.get();
        double tx = target.getX() + target.getVelocity().x * ticks;
        double ty = target.getY() + target.getVelocity().y * ticks + target.getEyeHeight(target.getPose());
        double tz = target.getZ() + target.getVelocity().z * ticks;

        Vec3d predicted = new Vec3d(tx, ty, tz);
        Rotations.rotate(Rotations.getYaw(predicted), Rotations.getPitch(predicted), 100);
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (Math.abs(player.getY() - mc.player.getY()) > 4.0) continue;
            double distance = mc.player.distanceTo(player);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    private void sendMove(double x, double y, double z, boolean onGround, float yaw, float pitch) {
        if (rotation.get()) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, onGround, false)
            );
        } else {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false)
            );
        }
    }

    public enum ExploitMode {
        Strong,
        Fast,
        Strict,
        Phobos
    }
}
