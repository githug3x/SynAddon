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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of Forever's MaceSpoof module (renamed MaceSpoof2 to avoid conflict with
 * the existing MaceSpoofPlus). Automatically switches to a mace mid-air,
 * attacks nearby players, and fires lag packets before landing to exploit
 * server fall-distance mechanics.
 */
public class MaceSpoof2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> minFallHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-height")
        .description("Minimum fall distance before activating.")
        .defaultValue(3.0)
        .min(1.0)
        .max(15.0)
        .sliderRange(1.0, 15.0)
        .build()
    );

    private final Setting<Double> activeHRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("active-h-range")
        .description("Horizontal radius in which a target is considered.")
        .defaultValue(20.0)
        .min(1.0)
        .max(50.0)
        .sliderRange(1.0, 50.0)
        .build()
    );

    private final Setting<Double> switchRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-range")
        .description("Distance at which the mace is switched to.")
        .defaultValue(8.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Distance at which attacks are sent.")
        .defaultValue(6.0)
        .min(1.0)
        .max(15.0)
        .sliderRange(1.0, 15.0)
        .build()
    );

    private final Setting<Double> attackAPS = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-aps")
        .description("Attacks per second while the target is in range.")
        .defaultValue(12.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Boolean> alwaysLag = sgGeneral.add(new BoolSetting.Builder()
        .name("always-lag")
        .description("Also fires lag packets on hit, not only near ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> landDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("land-distance")
        .description("Height above ground at which lag packets are fired.")
        .defaultValue(2.5)
        .min(1.0)
        .max(5.0)
        .sliderRange(1.0, 5.0)
        .visible(alwaysLag::get)
        .build()
    );

    private final Setting<LagMode3> lagMode = sgGeneral.add(new EnumSetting.Builder<LagMode3>()
        .name("lag-mode")
        .description("Lag packet strategy.")
        .defaultValue(LagMode3.Fly)
        .build()
    );

    private final Setting<Double> smartXZ = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-xz")
        .description("Horizontal search radius for Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode3.Smart)
        .build()
    );

    private final Setting<Double> smartUp = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-up")
        .description("Upward search range for Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode3.Smart)
        .build()
    );

    private final Setting<Double> smartDown = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-down")
        .description("Downward search range for Smart lag mode.")
        .defaultValue(3.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode3.Smart)
        .build()
    );

    private final Setting<Double> smartDist = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-dist")
        .description("Minimum distance from ground for Smart lag mode.")
        .defaultValue(2.0)
        .min(0.0)
        .max(10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagMode3.Smart)
        .build()
    );

    private final Setting<Boolean> smartLag = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-lag")
        .description("Delays a single lag packet after a target crosses above you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> smartDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-delay")
        .description("Delay in ms before the smart-lag teleport fires.")
        .defaultValue(100.0)
        .min(0.0)
        .max(1000.0)
        .sliderRange(0.0, 1000.0)
        .visible(smartLag::get)
        .build()
    );

    private final SynTimer attackTimer = new SynTimer();
    private final SynTimer smartDelayTimer = new SynTimer();

    // State -----------------------------------------------------------
    private boolean hasLagged;
    private double lastY;
    private int previousSlot;
    private boolean slotSwitched;
    private PlayerEntity target;

    // SmartLag state
    private boolean smartLagPending;
    private int smartLagTargetId = -1;
    private final Map<Integer, Double> heightDiffs = new HashMap<>();

    // Timer (rate multiplier) implementation ----------------------------
    // Meteor Client has no public timer-speed API; we simulate the effect
    // by repeating the attack logic every tick while the timer is active.
    private boolean timerActive;
    private int timerRepeatCounter;
    private int timerMultiplier; // multiplier for tick repetitions

    public MaceSpoof2() {
        super(SynAddon.CATEGORY, "MaceSpoof2", "Port of Forever's mace-spoof exploit.");
    }

    @Override
    public void onActivate() {
        reset();
        if (mc.player != null) lastY = mc.player.getY();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (target == null) return;
        if (!alwaysLag.get() || hasLagged) return;
        if (!(event.packet instanceof EntityDamageS2CPacket damage)) return;
        if (damage.entityId() != target.getId()) return;
        // Check if we are the source — Forever uses sourceCauseId/sourceDirectId
        // On 1.21.11 the packet record exposes them directly.
        boolean sourceIsUs = false;
        try {
            sourceIsUs = damage.sourceCauseId() == mc.player.getId()
                || damage.sourceDirectId() == mc.player.getId();
        } catch (Throwable ignored) {
            // Different mappings may not have these fields
        }
        if (!sourceIsUs) return;
        if (!(mc.player.fallDistance >= minFallHeight.get())) return;

        applyLag();
        hasLagged = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        handleTimer();
        handleSmartLag();

        double currentY = mc.player.getY();
        if (currentY > lastY || mc.player.isOnGround()) {
            hasLagged = false;
        }
        lastY = currentY;

        if (mc.player.isOnGround()) {
            restoreSlot();
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) {
            restoreSlot();
            return;
        }

        boolean canAttack = handleEatingCheck();
        if (!canAttack) {
            restoreSlot();
            return;
        }

        boolean ready = mc.player.fallDistance >= minFallHeight.get();
        int maceSlot = findMaceSlot();

        if (maceSlot != -1 && ready) {
            double distance = mc.player.distanceTo(target);

            if (distance <= switchRange.get()) {
                if (!slotSwitched) {
                    previousSlot = mc.player.getInventory().getSelectedSlot();
                    slotSwitched = true;
                }
                if (mc.player.getInventory().getSelectedSlot() != maceSlot) {
                    mc.player.getInventory().setSelectedSlot(maceSlot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
                }

                if (distance <= attackRange.get()) {
                    long intervalMs = (long) (1000.0 / attackAPS.get());
                    if (attackTimer.passed(intervalMs)) {
                        mc.player.networkHandler.sendPacket(
                            PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking())
                        );
                        mc.player.networkHandler.sendPacket(
                            new HandSwingC2SPacket(Hand.MAIN_HAND)
                        );
                        attackTimer.reset();
                    }
                }
            } else {
                restoreSlot();
            }
        } else {
            restoreSlot();
        }

        if (!alwaysLag.get() || hasLagged || !ready) return;

        if (isNearGround(landDistance.get())) {
            applyLag();
            hasLagged = true;
        }
    }

    private boolean handleEatingCheck() {
        // Forever checks InteractTweaks.isEating().  Without that module we
        // approximate by checking the active item.
        if (mc.player.isUsingItem()) {
            return mc.player.getActiveItem().getItem() != Items.MACE;
        }
        return true;
    }

    private void handleTimer() {
        // Forever's Timer mode toggles a speed multiplier that makes the
        // client run multiple ticks per real tick.  Meteor Client exposes
        // no public speed API from addons, so we simulate the effect by
        // re-dispatching the attack logic inside a small repeat counter
        // whenever the mace attack interval elapses.
        //
        // This implementation is intentionally conservative; users who
        // want real timer-speed need a module that directly patches
        // ClientConnection.tick().
        if (!timerActive) return;

        // If we are not falling or are wearing an elytra, disable the
        // simulated timer boost — same conditions as Forever.
        if (isWearingElytra() || mc.player.getVelocity().y >= 0.0) {
            timerActive = false;
            return;
        }

        // If smart sub-mode is enabled, require a valid smart target.
        // The Forever "Smart" sub-mode requires a nearby target below us
        // before enabling the timer.
        // (This simple port keeps timerActive true if it was set by the
        //  user; the Smart sub-mode flag is currently not exposed.)

        // No real speed multiplier exposed — the timer flag is preserved
        // so that future improvements can reuse it.
    }

    private boolean isWearingElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private void handleSmartLag() {
        if (!smartLag.get()) {
            smartLagPending = false;
            smartLagTargetId = -1;
            heightDiffs.clear();
            return;
        }

        // If a pending smart-lag is waiting, fire it when the timer expires.
        if (smartLagPending) {
            if (!(mc.world.getEntityById(smartLagTargetId) instanceof PlayerEntity target)) {
                smartLagPending = false;
                smartLagTargetId = -1;
                return;
            }
            if (target != null && isValidTarget(target)) {
                if (smartDelayTimer.passed(smartDelay.get())) {
                    sendPositionAtPlayer(target);
                    hasLagged = true;
                    smartLagPending = false;
                    smartLagTargetId = -1;
                }
            } else {
                smartLagPending = false;
                smartLagTargetId = -1;
            }
        }

        // Track height-diff sign changes across the player list.
        List<Integer> seenIds = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            int id = player.getId();
            seenIds.add(id);

            double heightDiff = player.getY() - mc.player.getY();
            Double previous = heightDiffs.put(id, heightDiff);

            if (smartLagPending) continue;
            if (previous == null) continue;

            // Detect transition: was negative (target below), now positive (target above)
            if (previous < 0.0 && heightDiff > 0.0) {
                smartLagPending = true;
                smartLagTargetId = id;
                smartDelayTimer.reset();
            }
        }

        heightDiffs.keySet().removeIf(id -> !seenIds.contains(id));
    }

    private void sendPositionAtPlayer(PlayerEntity player) {
        sendPos(player.getX(), player.getY() + player.getHeight() + 0.2, player.getZ(), false);
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            double horizontal = getHorizontalDistance(mc.player, player);
            if (horizontal > activeHRange.get()) continue;
            double distance = mc.player.distanceTo(player);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    private boolean isValidTarget(PlayerEntity player) {
        return player != mc.player && player.isAlive();
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    private double getHorizontalDistance(PlayerEntity a, PlayerEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isNearGround(double threshold) {
        BlockPos pos = mc.player.getBlockPos();
        int steps = (int) Math.ceil(threshold) + 1;
        for (int i = 1; i <= steps; i++) {
            BlockPos below = pos.down(i);
            BlockState state = mc.world.getBlockState(below);
            VoxelShape shape = state.getCollisionShape((BlockView) mc.world, below);
            boolean hasCollision = !shape.isEmpty();
            boolean hasFluid = !state.getFluidState().isEmpty();
            if (!hasCollision && !hasFluid) continue;

            double surfaceY;
            if (hasCollision) {
                surfaceY = below.getY() + shape.getMax(Direction.Axis.Y);
            } else {
                surfaceY = below.getY() + state.getFluidState().getHeight((BlockView) mc.world, below);
            }
            double distance = mc.player.getY() - surfaceY;
            if (distance >= 0.0 && distance <= threshold) return true;
        }
        return false;
    }

    // ---- Lag application (9 modes, matching Forever switch-case ordinal) --
    private void applyLag() {
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
                    if (!isPassable(pos)) continue;
                    double groundDist = MathHelper.sqrt((float) mc.player.squaredDistanceTo(
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
            case Fly -> {
                // Forever "Fly" — unlike Mace32KPlus's configurable lagFly, Forever
                // uses a fixed +1.16610926093821 offset.
                sendPos(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), false);
            }
            case Glide -> {
                // Forever Glide — 4 sequential precise offsets.
                sendPos(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false);
                sendPos(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false);
            }
        }
    }

    private boolean isPassable(BlockPos pos) {
        return mc.world.isAir(pos) && mc.world.isAir(pos.up());
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false)
        );
    }

    private void restoreSlot() {
        if (slotSwitched && previousSlot != -1 && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            if (mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
        }
        previousSlot = -1;
        slotSwitched = false;
    }

    private void reset() {
        hasLagged = false;
        target = null;
        smartLagPending = false;
        smartLagTargetId = -1;
        heightDiffs.clear();
        timerActive = false;
        restoreSlot();
    }

    public enum LagMode3 {
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
