package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of the original 32KMace exploit: wind charge down, lag boost,
 * then a delayed mace attack.
 */
public class Mace32K extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> groundSpoof = sgGeneral.add(new BoolSetting.Builder()
        .name("ground-spoof")
        .description("Sends every movement packet as airborne.")
        .defaultValue(true)
        .build()
    );

    private final Setting<LagMode> lagMode = sgGeneral.add(new EnumSetting.Builder<LagMode>()
        .name("lag-mode")
        .description("Lag method used before the attack.")
        .defaultValue(LagMode.Fly)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between two attempts in milliseconds.")
        .defaultValue(400)
        .range(100, 3000)
        .sliderRange(100, 3000)
        .build()
    );

    private final Setting<Integer> delayUntilSwitch = sgGeneral.add(new IntSetting.Builder()
        .name("delay-until-switch")
        .description("Delay until the mace slot is selected in milliseconds.")
        .defaultValue(50)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> lagDelay = sgGeneral.add(new IntSetting.Builder()
        .name("lag-delay")
        .description("Delay until the lag boost is applied in milliseconds.")
        .defaultValue(80)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> delayUntilAttack = sgGeneral.add(new IntSetting.Builder()
        .name("delay-until-attack")
        .description("Delay until the attack is sent in milliseconds.")
        .defaultValue(80)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Maximum distance to the target.")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Double> lagFly = sgGeneral.add(new DoubleSetting.Builder()
        .name("lag-fly")
        .description("Fly height used by the Fly lag mode.")
        .defaultValue(4.0)
        .min(0.1)
        .max(10.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> lagMode.get() == LagMode.Fly)
        .build()
    );

    private final Setting<Double> smartXZ = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-xz")
        .description("Horizontal search radius of the Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartUp = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-up")
        .description("Upward search range of the Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartDown = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-down")
        .description("Downward search range of the Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartDist = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-dist")
        .description("Minimum distance of a Smart lag position.")
        .defaultValue(2.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode.Smart)
        .build()
    );

    private final SynTimer loopTimer = new SynTimer();
    private final SynTimer delayTimer = new SynTimer();

    private int stage = 0;
    private boolean hasFlown = false;
    private boolean hasLagged = false;
    private PlayerEntity currentTarget = null;
    private boolean resending = false;

    public Mace32K() {
        super(SynAddon.CATEGORY, "32KMace", "Wind charge plus lag exploit for long range mace hits.");
    }

    @Override
    public void onActivate() {
        resetStates();
    }

    @Override
    public void onDeactivate() {
        resetStates();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            resetStates();
            return;
        }

        PlayerEntity target = findTarget(attackRange.get());
        if (target == null) {
            resetStates();
            return;
        }

        if (stage == 0) {
            if (loopTimer.passed(delay.get())) {
                currentTarget = target;
                throwWindChargeDown();
                delayTimer.reset();
                hasFlown = false;
                hasLagged = false;
                stage = 1;
            }
        } else if (stage == 1) {
            if (!hasFlown && delayTimer.passed(delayUntilSwitch.get())) {
                int slot = findMaceSlot();
                if (slot != -1 && mc.player.getInventory().getSelectedSlot() != slot) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                }
                hasFlown = true;
            }

            if (!hasLagged && delayTimer.passed(lagDelay.get())) {
                applyLagBoost();
                hasLagged = true;
            }

            if (delayTimer.passed(delayUntilAttack.get())) {
                if (currentTarget != null && currentTarget.isAlive()
                    && mc.player.distanceTo(currentTarget) <= attackRange.get()) {
                    mc.player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking())
                    );
                    mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
                resetStates();
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!groundSpoof.get() || resending) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket packet) || !packet.isOnGround()) return;

        event.cancel();

        double x = packet.getX(mc.player.getX());
        double y = packet.getY(mc.player.getY());
        double z = packet.getZ(mc.player.getZ());
        float yaw = packet.getYaw(mc.player.getYaw());
        float pitch = packet.getPitch(mc.player.getPitch());
        boolean collision = packet.horizontalCollision();

        resending = true;
        try {
            if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround) {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, collision)
                );
            } else if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround) {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, false, collision)
                );
            } else if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly) {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(false, collision)
                );
            } else {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, collision)
                );
            }
        } finally {
            resending = false;
        }
    }

    private void applyLagBoost() {
        switch (lagMode.get()) {
            case Smart -> {
                List<BlockPos> positions = new ArrayList<>();
                double baseX = mc.player.getX();
                double baseY = mc.player.getY();
                double baseZ = mc.player.getZ();
                for (double dx = baseX - smartXZ.get(); dx < baseX + smartXZ.get(); dx += 1.0) {
                    for (double dz = baseZ - smartXZ.get(); dz < baseZ + smartXZ.get(); dz += 1.0) {
                        for (double dy = baseY - smartDown.get(); dy < baseY + smartUp.get(); dy += 1.0) {
                            positions.add(BlockPos.ofFloored(dx, dy, dz));
                        }
                    }
                }

                double bestSq = Double.MAX_VALUE;
                BlockPos best = null;
                for (BlockPos pos : positions) {
                    if (!canMove(pos)) continue;
                    double groundDist = Math.sqrt(mc.player.squaredDistanceTo(
                        pos.toCenterPos().add(0.0, -0.5, 0.0)
                    ));
                    if (groundDist < smartDist.get()) continue;
                    double distSq = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (distSq < bestSq) {
                        bestSq = distSq;
                        best = pos;
                    }
                }

                if (best != null) {
                    sendPos(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, false);
                }
            }
            case Invalid -> {
                for (int i = 0; i < 20; i++) {
                    sendPos(mc.player.getX(), mc.player.getY() + 1337.0, mc.player.getZ(), false);
                }
            }
            case TrollHack -> sendPos(
                mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false
            );
            case ToVoid -> sendPos(mc.player.getX(), -70.0, mc.player.getZ(), false);
            case ToVoid2 -> sendPos(mc.player.getX(), -7.0, mc.player.getZ(), false);
            case Normal -> sendPos(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ(), false);
            case Rotation -> {
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(-180.0f, -90.0f, false, false)
                );
                mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(180.0f, 90.0f, false, false)
                );
            }
            case Fly -> sendPos(mc.player.getX(), mc.player.getY() + lagFly.get(), mc.player.getZ(), false);
            case Glide -> {
                sendPos(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false);
            }
        }
    }

    private void throwWindChargeDown() {
        int slot = -1;
        boolean fromInventory = false;

        if (mc.player.getMainHandStack().getItem() == Items.WIND_CHARGE) {
            slot = mc.player.getInventory().getSelectedSlot();
        } else {
            slot = SynUtils.findItem(Items.WIND_CHARGE);
            if (slot == -1) {
                slot = SynUtils.findItemInventorySlotFromZero(Items.WIND_CHARGE);
                if (slot != -1) fromInventory = true;
            }
        }

        if (slot == -1) return;

        float yaw = mc.player.getYaw();
        float pitch = 90.0f;
        float prevYaw = mc.player.getYaw();
        float prevPitch = mc.player.getPitch();

        // snapAt
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, false, false)
        );

        if (mc.player.getMainHandStack().getItem() == Items.WIND_CHARGE) {
            mc.player.networkHandler.sendPacket(
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, pitch)
            );
            mc.player.swingHand(Hand.MAIN_HAND);
        } else if (fromInventory) {
            int selected = mc.player.getInventory().getSelectedSlot();
            SynUtils.inventorySwap(slot, selected);
            mc.player.networkHandler.sendPacket(
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, pitch)
            );
            mc.player.swingHand(Hand.MAIN_HAND);
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
            SynUtils.syncInventory();
        } else {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            SynUtils.switchToSlot(slot);
            mc.player.networkHandler.sendPacket(
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, pitch)
            );
            mc.player.swingHand(Hand.MAIN_HAND);
            SynUtils.switchToSlot(prevSlot);
        }

        // snapBack
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.LookAndOnGround(prevYaw, prevPitch, false, false)
        );
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    private boolean canMove(BlockPos pos) {
        return mc.world.isAir(pos) && mc.world.isAir(pos.up());
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false)
        );
    }

    private void resetStates() {
        stage = 0;
        hasFlown = false;
        hasLagged = false;
        currentTarget = null;
        loopTimer.reset();
    }

    private PlayerEntity findTarget(double range) {
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            double distance = mc.player.distanceTo(player);
            if (distance > range || distance >= bestDistance) continue;
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    public enum LagMode {
        Smart,
        Invalid,
        TrollHack,
        ToVoid,
        ToVoid2,
        Normal,
        Rotation,
        Fly,
        Glide
    }
}
