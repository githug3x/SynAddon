package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public class FlatGroundMace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Flat-ground mace mode.")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Target range.")
        .defaultValue(4.0)
        .range(1.0, 8.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Double> fallHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-height")
        .description("Fake fall height.")
        .defaultValue(4.0)
        .range(0.5, 15.0)
        .sliderRange(0.5, 15.0)
        .build()
    );

    private final Setting<Double> resetDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("reset-distance")
        .description("Distance used while resetting.")
        .defaultValue(0.1)
        .range(0.05, 0.5)
        .sliderRange(0.05, 0.5)
        .build()
    );

    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mace")
        .description("Requires a mace to be held or in the hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lockView = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-view")
        .description("Locks the view onto the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> splitStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("split-step")
        .description("Step size for split modes.")
        .defaultValue(0.42)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .visible(() -> mode.get() == Mode.VerticalSplit || mode.get() == Mode.Hybrid)
        .build()
    );

    private final Setting<Integer> blinkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("blink-delay")
        .description("Ticks buffered before flushing.")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(() -> mode.get() == Mode.BlinkBuffer)
        .build()
    );

    private final Setting<Double> spoofHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("spoof-height")
        .description("Height used for motion spoofing.")
        .defaultValue(12.0)
        .range(2.0, 70.0)
        .sliderRange(2.0, 70.0)
        .visible(() -> mode.get() == Mode.MotionSpoof)
        .build()
    );

    private final Setting<Boolean> spoof = sgGeneral.add(new BoolSetting.Builder()
        .name("spoof")
        .description("Spoofs the fall.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.TheVoid)
        .build()
    );

    private final Setting<VMMode> vmMode = sgGeneral.add(new EnumSetting.Builder<VMMode>()
        .name("vm-mode")
        .description("Void mace packet mode.")
        .defaultValue(VMMode.DoublePacket)
        .visible(() -> mode.get() == Mode.VoidMace)
        .build()
    );

    private final Setting<Double> voidY = sgGeneral.add(new DoubleSetting.Builder()
        .name("void-y")
        .description("Y used in void mace mode.")
        .defaultValue(-60.0)
        .range(-100.0, -50.0)
        .sliderRange(-100.0, -50.0)
        .visible(() -> mode.get() == Mode.VoidMace)
        .build()
    );

    private final Setting<Double> vmFallHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("vm-fall-height")
        .description("Fall distance used in void mace mode.")
        .defaultValue(100.0)
        .range(20.0, 500.0)
        .sliderRange(20.0, 500.0)
        .visible(() -> mode.get() == Mode.VoidMace)
        .build()
    );

    private final Setting<Integer> tpCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("tp-cooldown")
        .description("Ticks between teleports.")
        .defaultValue(5)
        .range(1, 30)
        .sliderRange(1, 30)
        .visible(() -> mode.get() == Mode.TP)
        .build()
    );

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players")
        .description("Targets players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> villagers = sgGeneral.add(new BoolSetting.Builder()
        .name("villagers")
        .description("Targets villagers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> animals = sgGeneral.add(new BoolSetting.Builder()
        .name("animals")
        .description("Targets animals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> slimes = sgGeneral.add(new BoolSetting.Builder()
        .name("slimes")
        .description("Targets slimes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> mobs = sgGeneral.add(new BoolSetting.Builder()
        .name("mobs")
        .description("Targets mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepVelocity = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-velocity")
        .description("Keeps horizontal velocity.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.VoidMace)
        .build()
    );

    private final Setting<Boolean> antiAir = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-air")
        .description("Searches downward for ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attackPackets = sgGeneral.add(new IntSetting.Builder()
        .name("attack-packets")
        .description("Attack packets sent per tick.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );

    private final SynTimer packetTimer = new SynTimer();
    private final SynTimer tpTimer = new SynTimer();
    private final SynTimer stateTimer = new SynTimer();

    private Entity target;
    private final ArrayList<PlayerMoveC2SPacket> bufferedPackets = new ArrayList<>();
    private int blinkTicks;
    private int tpCd;
    private double spoofY;
    private boolean spoofRising;
    private boolean blinkActive;
    private boolean attacking;
    private State state = State.IDLE;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private int stepTick;
    private int attackCd;

    public FlatGroundMace() {
        super(SynAddon.CATEGORY, "FlatGroundMace", "Simulates fall damage for mace attacks on flat ground.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        flushBuffer();
        resetState();
    }

    private void resetState() {
        target = null;
        bufferedPackets.clear();
        blinkTicks = 0;
        tpCd = 0;
        spoofY = 0.0;
        spoofRising = false;
        blinkActive = false;
        attacking = false;
        state = State.IDLE;
        stepTick = 0;
        attackCd = 0;
        packetTimer.reset();
        tpTimer.reset();
        stateTimer.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (tpCd > 0) --tpCd;
        if (attackCd > 0) --attackCd;

        target = findTarget();
        if (target == null) {
            flushBuffer();
            return;
        }
        if (requireMace.get() && mc.player.getMainHandStack().getItem() != Items.MACE && findMace() == -1) return;

        double fallY = getGroundHeight();
        double currentY = mc.player.getY();
        double d = currentY - fallY;
        double d2 = fallHeight.get();

        switch (mode.get()) {
            case TP -> doTp(d, d2);
            case VoidMace -> doVoidMace(d, d2);
            case MotionSpoof -> doMotionSpoof(d, d2);
            case BlinkBuffer -> doBlinkBuffer(d, d2);
            case Hybrid -> doHybrid();
            case TheVoid -> doTheVoid(d, d2);
            case VerticalSplit -> doVerticalSplit(d, d2);
            case DoublePacket -> doStateMachine();
            default -> doVoidMaceMode();
        }

        if (lockView.get() && target != null) faceTarget(target);
    }

    private void doTp(double d, double d2) {
        if (d <= d2 && tpCd <= 0) {
            double targetY = mc.player.getY() + fallHeight.get();
            mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            tpCd = tpCooldown.get();
        }
    }

    private void doVoidMace(double d, double d2) {
        if (d <= d2 && tpCd <= 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY() + fallHeight.get(), mc.player.getZ(), false, false
            ));
            tpCd = tpCooldown.get();
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + fallHeight.get(), mc.player.getZ());
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
        }
    }

    private void doMotionSpoof(double d, double d2) {
        if (!spoofRising && d <= d2) {
            spoofRising = true;
            spoofY = mc.player.getY();
        }
        if (spoofRising) {
            if (spoofY < mc.player.getY() + fallHeight.get()) {
                spoofY += 0.42;
                if (spoofY > mc.player.getY() + fallHeight.get()) spoofY = mc.player.getY() + fallHeight.get();
                mc.player.setPosition(mc.player.getX(), spoofY, mc.player.getZ());
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            } else {
                spoofRising = false;
            }
        }
    }

    private void doBlinkBuffer(double d, double d2) {
        if (!blinkActive && d <= d2) {
            blinkActive = true;
            blinkTicks = 0;
        }
        if (blinkActive) {
            ++blinkTicks;
            if (blinkTicks >= blinkDelay.get()) {
                flushBuffer();
                blinkActive = false;
            }
        }
    }

    private void doHybrid() {
        if (attackCd > 0) return;
        Entity entity = target;
        if (entity == null) return;

        bufferedPackets.clear();
        attacking = true;
        double startY = mc.player.getY();
        for (int i = 1; i <= 6; ++i) {
            double y = startY + (double) i * (fallHeight.get() / 6.0);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), y, mc.player.getZ(), false, false
            ));
        }
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(), startY, mc.player.getZ(), false, false
        ));
        attackTarget(entity);
        mc.player.setPosition(mc.player.getX(), startY, mc.player.getZ());
        mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
        mc.player.fallDistance = 0.0f;
        attacking = false;
        attackCd = 3;
    }

    private void doTheVoid(double d, double d2) {
        if (d <= d2 && attackCd <= 0) {
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + fallHeight.get(), mc.player.getZ());
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            attackTarget(target);
            attackCd = 3;
        }
    }

    private void doVerticalSplit(double d, double d2) {
        if (!spoofRising && d <= d2) {
            spoofRising = true;
            spoofY = mc.player.getY();
        }
        if (spoofRising) {
            if (spoofY < mc.player.getY() + fallHeight.get()) {
                spoofY += splitStep.get();
                if (spoofY > mc.player.getY() + fallHeight.get()) spoofY = mc.player.getY() + fallHeight.get();
                mc.player.setPosition(mc.player.getX(), spoofY, mc.player.getZ());
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            } else {
                spoofRising = false;
            }
        }
    }

    private void doStateMachine() {
        if (state == State.IDLE) {
            if (target == null) return;
            anchorX = mc.player.getX();
            anchorY = mc.player.getY();
            anchorZ = mc.player.getZ();
            stepTick = 0;
            attackCd = 0;
            state = State.FALLING;
            stateTimer.reset();
            return;
        }
        if (state != State.FALLING) return;

        ++stepTick;
        if (stepTick <= 1) {
            sendAnchorPacket(anchorX, anchorY, anchorZ);
            sendAnchorPacket(anchorX, anchorY - resetDistance.get(), anchorZ);
            sendAnchorPacket(anchorX, anchorY - resetDistance.get() * 2.0, anchorZ);
            sendAnchorPacket(anchorX, anchorY + 1.0, anchorZ);
            attackCd = attackPackets.get();
            return;
        }
        if (attackCd > 0) {
            --attackCd;
            return;
        }
        if (target != null && target.isAlive() && isValid(target)) attackTarget(target);
        state = State.IDLE;
        target = null;
    }

    private void sendAnchorPacket(double x, double y, double z) {
        if (keepVelocity.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
        } else {
            mc.player.setPosition(x, y, z);
            mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
        }
    }

    private void doVoidMaceMode() {
        if (!packetTimer.passed(500.0)) return;
        packetTimer.reset();

        Entity entity = target;
        if (entity == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double baseY = y;

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, baseY + spoofHeight.get(), z, false, false));
        mc.player.setPosition(x, baseY + spoofHeight.get(), z);
        baseY += spoofHeight.get();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, baseY + 1.1, z, false, false));
        mc.player.setPosition(x, baseY + 1.1, z);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, voidY.get(), z, false, false));
        baseY += 1.1;
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, baseY + 1.0, z, false, false));
        mc.player.setPosition(x, baseY + 1.0, z);
        mc.player.fallDistance = (float) (double) vmFallHeight.get();
        attackTarget(entity);
        if (keepVelocity.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
            mc.player.setPosition(x, y, z);
        }
    }

    private void attackTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) return;
        int count = attackPackets.get();
        for (int i = 0; i < count; ++i) {
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        }
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void faceTarget(Entity entity) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = entity.getX() - eyes.x;
        double dy = entity.getY() + (double) entity.getHeight() * 0.5 - eyes.y;
        double dz = entity.getZ() - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957763671875) - 90.0f;
        float pitch = (float) (-(MathHelper.atan2(dy, dist) * 57.2957763671875));
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    private double getGroundHeight() {
        BlockPos pos = mc.player.getBlockPos();
        if (antiAir.get()) {
            for (int i = 0; i < 6; ++i) {
                BlockPos check = pos.down(i);
                if (!mc.world.getBlockState(check).isSolidBlock(mc.world, check)) continue;
                return check.getY() + 1;
            }
        }
        return mc.world.getBottomY();
    }

    private Entity findTarget() {
        Entity best = null;
        double bestDist = range.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || entity == mc.player || !entity.isAlive() || !isValid(entity)) continue;
            if (entity instanceof PlayerEntity) {
                if (!players.get()) continue;
            } else if (entity instanceof MerchantEntity) {
                if (!villagers.get()) continue;
            } else if (entity instanceof SlimeEntity) {
                if (!slimes.get()) continue;
            } else if (!(entity instanceof AnimalEntity) ? !(entity instanceof MobEntity) || !mobs.get() : !animals.get()) {
                continue;
            }
            double dist = mc.player.getEyePos().distanceTo(entity.getEntityPos());
            if (!(dist <= bestDist)) continue;
            bestDist = dist;
            best = entity;
        }
        return best;
    }

    private static boolean isValid(Entity entity) {
        return entity.isAlive() && entity.isAttackable() && !entity.isInvulnerable();
    }

    private void flushBuffer() {
        if (bufferedPackets.isEmpty()) return;
        for (PlayerMoveC2SPacket packet : bufferedPackets) {
            mc.player.networkHandler.sendPacket(packet);
        }
        bufferedPackets.clear();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (!blinkActive) return;
        if (event.packet instanceof PlayerMoveC2SPacket move) {
            event.cancel();
            bufferedPackets.add(move);
        }
    }

    private static int findMace() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    public enum Mode {
        Vanilla,
        DoublePacket,
        VerticalSplit,
        Hybrid,
        BlinkBuffer,
        MotionSpoof,
        TheVoid,
        VoidMace,
        TP
    }

    public enum VMMode {
        DoublePacket,
        Legacy
    }

    public enum State {
        IDLE,
        FALLING
    }
}
