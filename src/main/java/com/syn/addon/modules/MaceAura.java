package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MaceAura extends Module {
    private static final double[] JUMP_HEIGHTS = new double[]{0.4199999868869781, 0.7531999805212017, 0.9999957640154541, 1.1661092609382138};
    private static final double STEP = 0.25;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Target search range.")
        .defaultValue(5.0)
        .range(1.0, 8.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Attack range.")
        .defaultValue(3.0)
        .range(1.0, 8.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Double> triggerDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("trigger-distance")
        .description("Distance at which the attack triggers.")
        .defaultValue(2.0)
        .range(0.1, 20.0)
        .sliderRange(0.1, 20.0)
        .build()
    );

    private final Setting<Double> lagbackDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("lagback-distance")
        .description("Lag-back distance.")
        .defaultValue(5.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> spoofHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("spoof-height")
        .description("Height spoofed before attacking.")
        .defaultValue(25.0)
        .range(0.0, 2000.0)
        .sliderRange(0.0, 2000.0)
        .build()
    );

    private final Setting<Integer> cooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Ticks between attacks.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> timer = sgGeneral.add(new BoolSetting.Builder()
        .name("timer")
        .description("Speeds up the client timer.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> timerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("timer-speed")
        .description("Client timer speed.")
        .defaultValue(2.0)
        .range(1.0, 100.0)
        .sliderRange(1.0, 100.0)
        .visible(timer::get)
        .build()
    );

    private final Setting<Boolean> maceOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("mace-only")
        .description("Only attacks while holding a mace.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Uses silent rotations.")
        .defaultValue(false)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("Target rendering mode.")
        .defaultValue(RenderMode.Normal)
        .build()
    );

    private final Setting<SettingColor> silentPlayerBox = sgGeneral.add(new ColorSetting.Builder()
        .name("silent-player-box")
        .description("Color of the silent player box.")
        .defaultValue(new SettingColor(255, 80, 80, 180))
        .visible(() -> renderMode.get() == RenderMode.Silent)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Allows finding a mace in the inventory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyFalling = sgGeneral.add(new BoolSetting.Builder()
        .name("only-falling")
        .description("Only attacks while falling.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyAir = sgGeneral.add(new BoolSetting.Builder()
        .name("only-air")
        .description("Only attacks while airborne.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack")
        .description("Automatically attacks targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> set = sgGeneral.add(new BoolSetting.Builder()
        .name("set")
        .description("Uses the set attack sequence.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> groundSpoof = sgGeneral.add(new BoolSetting.Builder()
        .name("ground-spoof")
        .description("Spoofs movement packets as airborne.")
        .defaultValue(true)
        .visible(set::get)
        .build()
    );

    private final Setting<Boolean> attackPull = sgGeneral.add(new BoolSetting.Builder()
        .name("attack-pull")
        .description("Pulls the attack position upward.")
        .defaultValue(false)
        .visible(set::get)
        .build()
    );

    private final Setting<LagMode> lagMode = sgGeneral.add(new EnumSetting.Builder<LagMode>()
        .name("lag-mode")
        .description("Lag packet mode.")
        .defaultValue(LagMode.Fly)
        .visible(set::get)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Milliseconds before arming.")
        .defaultValue(400)
        .range(100, 3000)
        .sliderRange(100, 3000)
        .visible(set::get)
        .build()
    );

    private final Setting<Integer> delayUntilSwitch = sgGeneral.add(new IntSetting.Builder()
        .name("delay-until-switch")
        .description("Milliseconds until switch.")
        .defaultValue(50)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .visible(set::get)
        .build()
    );

    private final Setting<Integer> lagDelay = sgGeneral.add(new IntSetting.Builder()
        .name("lag-delay")
        .description("Milliseconds before lag packets.")
        .defaultValue(80)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .visible(set::get)
        .build()
    );

    private final Setting<Integer> delayUntilAttack = sgGeneral.add(new IntSetting.Builder()
        .name("delay-until-attack")
        .description("Milliseconds until the attack.")
        .defaultValue(80)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .visible(set::get)
        .build()
    );

    private final Setting<Double> setRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("set-range")
        .description("Anchor range for the set sequence.")
        .defaultValue(5.0)
        .range(1.0, 10.0)
        .sliderRange(1.0, 10.0)
        .visible(set::get)
        .build()
    );

    private final Setting<Double> lagFly = sgGeneral.add(new DoubleSetting.Builder()
        .name("lag-fly")
        .description("Fly lag height.")
        .defaultValue(4.0)
        .range(0.1, 10.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> set.get() && lagMode.get() == LagMode.Fly)
        .build()
    );

    private final Setting<Double> smartXZ = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-xz")
        .description("Smart lag horizontal radius.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> set.get() && lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartUp = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-up")
        .description("Smart lag upward radius.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> set.get() && lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartDown = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-down")
        .description("Smart lag downward radius.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> set.get() && lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Double> smartDist = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-dist")
        .description("Smart lag distance.")
        .defaultValue(2.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> set.get() && lagMode.get() == LagMode.Smart)
        .build()
    );

    private final Setting<Boolean> noWait = sgGeneral.add(new BoolSetting.Builder()
        .name("no-wait")
        .description("Ignores the attack cooldown.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SwingMode> swing = sgGeneral.add(new EnumSetting.Builder<SwingMode>()
        .name("swing")
        .description("How the arm swings.")
        .defaultValue(SwingMode.All)
        .build()
    );

    private final SynTimer delayTimer = new SynTimer();
    private final SynTimer lagTimer = new SynTimer();
    private final SynTimer switchTimer = new SynTimer();
    private final SynTimer attackTimer = new SynTimer();

    private PlayerEntity target;
    private PlayerEntity setTarget;
    private Vec3d attackPos;
    private Vec3d renderPos;
    private Vec3d setAnchorPos;
    private int cooldown;
    private int airTicks;
    private boolean attacking;
    private boolean lagging;
    private boolean setArmed;
    private boolean maceSwapped;
    private boolean waitingLag;
    private float renderYaw;
    private float renderPitch;
    private float lastYaw;
    private float lastPitch;
    private boolean resending;
    private double timerAccumulatorPre;
    private double timerAccumulatorPost;

    public MaceAura() {
        super(SynAddon.CATEGORY, "MaceAura", "Attacks nearby players with the mace.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        restoreSlot();
        resetState();
    }

    private void resetState() {
        target = null;
        setTarget = null;
        attackPos = null;
        renderPos = null;
        setAnchorPos = null;
        cooldown = 0;
        airTicks = 0;
        attacking = false;
        lagging = false;
        setArmed = false;
        maceSwapped = false;
        waitingLag = false;
        resending = false;
        syncRenderRot();
    }

    private void syncRenderRot() {
        if (mc.player == null) return;
        renderYaw = mc.player.getYaw();
        renderPitch = mc.player.getPitch();
        lastYaw = renderYaw;
        lastPitch = renderPitch;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        int times = timerTicks(true);
        for (int i = 0; i < times; i++) {
            if (cooldown > 0) {
                --cooldown;
                handleSilentCamera();
                trackAirState();
                continue;
            }
            if (set.get()) setTick();
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        int times = timerTicks(false);
        for (int i = 0; i < times; i++) tickPre();
    }

    private int timerTicks(boolean post) {
        if (!timer.get()) {
            timerAccumulatorPre = 0.0;
            timerAccumulatorPost = 0.0;
            return 1;
        }

        double accumulator = (post ? timerAccumulatorPost : timerAccumulatorPre) + timerSpeed.get();
        int times = (int) accumulator;
        if (times < 1) times = 1;
        accumulator -= times;

        if (post) {
            timerAccumulatorPost = accumulator;
        } else {
            timerAccumulatorPre = accumulator;
        }

        return Math.min(times, 100);
    }

    private void tickPre() {
        if (cooldown > 0) return;
        if (!autoAttack.get()) return;
        if (set.get()) return;

        target = findPlayer(targetRange.get());
        if (target == null) {
            attackPos = null;
            return;
        }
        if (maceOnly.get() && !hasMace()) return;
        if (onlyAir.get() && mc.player.isOnGround()) return;
        if (onlyFalling.get() && mc.player.fallDistance <= 0.0f) return;

        double dist = mc.player.distanceTo(target);
        if (dist > attackRange.get()) return;

        attackPos = target.getEntityPos();
        renderPos = target.getEntityPos();
        if (!noWait.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        if (dist > triggerDistance.get()) return;

        doAttack(attackPos);
        cooldown = cooldownTicks.get();
    }

    private void setTick() {
        if (mc.player.isOnGround() && setAnchorPos == null) {
            setArmed = false;
            setAnchorPos = mc.player.getEntityPos();
        }
        if (setAnchorPos == null) return;
        if (setAnchorPos.distanceTo(mc.player.getEntityPos()) > setRange.get()) {
            setAnchorPos = null;
            setArmed = false;
            lagging = false;
            return;
        }

        setTarget = findPlayer(targetRange.get());
        if (setTarget == null) {
            attackPos = null;
            return;
        }
        if (maceOnly.get() && !hasMace()) return;

        if (!setArmed && delayTimer.passed(delay.get())) {
            setArmed = true;
            switchTimer.reset();
            lagTimer.reset();
            attackTimer.reset();
        }
        if (!setArmed) return;

        if (lagging) {
            if (attackTimer.passed(delayUntilAttack.get())) {
                Vec3d pos = setTarget.getEntityPos();
                if (attackPull.get()) pos = pos.add(0.0, 0.5, 0.0);
                doAttack(pos);
                cooldown = cooldownTicks.get();
                lagging = false;
            }
            return;
        }

        if (lagTimer.passed(lagDelay.get())) {
            doLagSequence(setTarget.getEntityPos());
            lagging = true;
            attackTimer.reset();
        }
    }

    private void doAttack(Vec3d pos) {
        attacking = true;
        sendAttack(pos);
        lagTo(pos);
        mc.player.setPosition(pos.x, pos.y, pos.z);
        mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
        mc.player.fallDistance = 0.0f;
        delayTimer.reset();
        if (set.get()) setAnchorPos = mc.player.getEntityPos();
        attacking = false;
    }

    private Entity attackEntity() {
        return set.get() && setTarget != null ? setTarget : target;
    }

    private void sendAttack(Vec3d pos) {
        Entity entity = attackEntity();
        if (entity == null) return;
        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void lagTo(Vec3d targetPos) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        for (double h : JUMP_HEIGHTS) sendPos(x, y + h, z, false);
        Vec3d top = new Vec3d(x, y + JUMP_HEIGHTS[JUMP_HEIGHTS.length - 1], z);
        sendSteps(top, targetPos);
        Vec3d high = new Vec3d(targetPos.x, targetPos.y + spoofHeight.get(), targetPos.z);
        sendOnGroundChain(top, high);
        sendFull(high.x, high.y, high.z, false);
        sendOnGroundChain(high, targetPos);
        sendFull(targetPos.x, targetPos.y, targetPos.z, true);
    }

    private void sendOnGroundChain(Vec3d from, Vec3d to) {
        int count = (int) Math.ceil(from.distanceTo(to) / 10.0) - 1;
        for (int i = 0; i < count; ++i) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        }
    }

    private void sendFull(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            x, y, z, mc.player.getYaw(), mc.player.getPitch(), onGround, false
        ));
    }

    private void doLagSequence(Vec3d targetPos) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        if (lagMode.get() == LagMode.Fly) {
            for (double h : JUMP_HEIGHTS) sendPos(x, y + h, z, false);
            sendPos(x, y + lagFly.get(), z, false);
            sendPos(x, y, z, true);
        } else {
            double dx = targetPos.x - x;
            double dz = targetPos.z - z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > smartDist.get()) {
                dx *= smartDist.get() / len;
                dz *= smartDist.get() / len;
            }
            for (double h : JUMP_HEIGHTS) sendPos(x, y + h, z, false);
            sendPos(x + dx, y + smartUp.get(), z + dz, false);
            sendPos(x + dx, y - smartDown.get(), z + dz, true);
        }
    }

    private void sendSteps(Vec3d from, Vec3d to) {
        double x = from.x;
        double z = from.z;
        double y = from.y;
        if (Math.abs(x - to.x) > 0.25) sendPos(x += x > to.x ? -0.25 : 0.25, y, z, false);
        if (Math.abs(z - to.z) > 0.25) sendPos(x, y, z += z > to.z ? -0.25 : 0.25, false);
        if (x != to.x || z != to.z) sendPos(to.x, y, to.z, false);
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
    }

    private void handleSilentCamera() {
        if (renderMode.get() != RenderMode.Silent) return;
        if (target == null) return;
        renderPos = target.getEntityPos().add(0.0, mc.player.getStandingEyeHeight(), 0.0);
        syncRenderRot();
    }

    private void trackAirState() {
        if (mc.player.isGliding() || mc.player.isClimbing() || mc.player.isTouchingWater()) {
            airTicks = 0;
            syncRenderRot();
            return;
        }
        if (!mc.player.isOnGround()) {
            ++airTicks;
        } else if (mc.player.getVelocity().y >= 0.0) {
            airTicks = 0;
            lagging = false;
        }
    }

    private PlayerEntity findPlayer(double range) {
        PlayerEntity best = null;
        double bestDist = range;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            double dist = mc.player.distanceTo(player);
            if (!(dist <= bestDist)) continue;
            bestDist = dist;
            best = player;
        }
        return best;
    }

    private boolean hasMace() {
        if (mc.player.getMainHandStack().getItem() == Items.MACE) return true;
        return inventorySwap.get() && findMaceSlot() != -1;
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    private void restoreSlot() {
    }

    private boolean isEnemyVisible(Entity entity) {
        return entity != null && mc.player.canSee(entity);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || resending) return;
        if (attacking) return;
        if (!set.get() || !groundSpoof.get()) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (!packet.changesPosition()) return;

        double x = packet.getX(mc.player.getX());
        double y = packet.getY(mc.player.getY());
        double z = packet.getZ(mc.player.getZ());
        float yaw = packet.getYaw(mc.player.getYaw());
        float pitch = packet.getPitch(mc.player.getPitch());

        event.cancel();
        resending = true;
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, false));
        resending = false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;
        SettingColor color = new SettingColor(255, 80, 80, 120);
        if (renderMode.get() == RenderMode.Normal) {
            PlayerEntity renderTarget = set.get() ? setTarget : target;
            if (renderTarget != null && renderTarget.isAlive()) {
                Box box = renderTarget.getBoundingBox().offset(-event.offsetX, -event.offsetY, -event.offsetZ);
                event.renderer.box(box,
                    new meteordevelopment.meteorclient.utils.render.color.Color(255, 80, 80, 120),
                    new meteordevelopment.meteorclient.utils.render.color.Color(255, 80, 80, 120),
                    ShapeMode.Both, 0);
            }
        } else if (renderMode.get() == RenderMode.Silent && renderPos != null && setTarget != null) {
            SettingColor silentColor = silentPlayerBox.get();
            Box box = new Box(
                renderPos.x - 0.3, renderPos.y, renderPos.z - 0.3,
                renderPos.x + 0.3, renderPos.y + 1.8, renderPos.z + 0.3
            ).offset(-event.offsetX, -event.offsetY, -event.offsetZ);
            event.renderer.box(box,
                new meteordevelopment.meteorclient.utils.render.color.Color(silentColor.r, silentColor.g, silentColor.b, silentColor.a / 2),
                silentColor,
                ShapeMode.Both, 0);
        }
    }

    public boolean isSilentRendering() {
        return isActive() && renderMode.get() == RenderMode.Silent && target != null && attacking;
    }

    public boolean shouldHideRotation() {
        return isActive() && renderMode.get() == RenderMode.Silent && target != null;
    }

    public Vec3d getSilentPos() {
        return renderPos;
    }

    public enum RenderMode {
        Normal,
        Silent
    }

    public enum LagMode {
        Fly,
        Smart
    }

    public enum SwingMode {
        All,
        MainHand
    }
}
