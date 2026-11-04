package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class CrystalHurtPeople extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cps = sgGeneral.add(new IntSetting.Builder()
        .name("cps")
        .description("Crystals placed per second.")
        .defaultValue(16)
        .range(1, 32)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Crystal placement range.")
        .defaultValue(5.0)
        .range(0.0, 6.0)
        .sliderRange(0.0, 6.0)
        .build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Crystal break range.")
        .defaultValue(4.0)
        .range(0.0, 6.0)
        .sliderRange(0.0, 6.0)
        .build()
    );

    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Minimum damage required.")
        .defaultValue(4.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 36.0)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum damage allowed to self.")
        .defaultValue(10.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 36.0)
        .build()
    );

    private final Setting<SwapMode> autoSwap = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
        .name("auto-swap")
        .description("How crystals are selected.")
        .defaultValue(SwapMode.None)
        .build()
    );

    private final Setting<Boolean> fastLayer = sgGeneral.add(new BoolSetting.Builder()
        .name("fast-layer")
        .description("Sends extra attack packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the target placement block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> eatingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("eating-pause")
        .description("Pauses while eating.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> reserve = sgGeneral.add(new DoubleSetting.Builder()
        .name("reserve")
        .description("Health kept in reserve.")
        .defaultValue(2.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Target search range.")
        .defaultValue(12.0)
        .range(0.0, 20.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Double> wallRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("wall-range")
        .description("Range through walls.")
        .defaultValue(6.0)
        .range(0.0, 6.0)
        .sliderRange(0.0, 6.0)
        .build()
    );

    private final Setting<Boolean> strongLoop = sgGeneral.add(new BoolSetting.Builder()
        .name("strong-loop")
        .description("Places immediately after breaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-break")
        .description("Breaks crystals that spawn in the target block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attackFactor = sgGeneral.add(new IntSetting.Builder()
        .name("attack-factor")
        .description("Extra attack packets per break.")
        .defaultValue(1)
        .range(1, 8)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> placeRate = sgGeneral.add(new IntSetting.Builder()
        .name("place-rate")
        .description("Ticks between placements.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> onlyBestDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("only-best-damage")
        .description("Only places when placement damage beats break damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Uses silent hotbar selection.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> remove = sgGeneral.add(new BoolSetting.Builder()
        .name("remove")
        .description("Removes crystals client-side after attacking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("update-delay")
        .description("Milliseconds between target updates.")
        .defaultValue(30)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> rotations = sgGeneral.add(new BoolSetting.Builder()
        .name("rotations")
        .description("Rotates toward targets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> predict = sgGeneral.add(new BoolSetting.Builder()
        .name("predict")
        .description("Predicts target movement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("predict-ticks")
        .description("Movement prediction ticks.")
        .defaultValue(4)
        .range(0, 20)
        .sliderRange(0, 20)
        .visible(predict::get)
        .build()
    );

    private final Setting<Double> maxMotionY = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-motion-y")
        .description("Maximum predicted vertical motion.")
        .defaultValue(0.34)
        .range(0.0, 2.0)
        .sliderRange(0.0, 2.0)
        .visible(predict::get)
        .build()
    );

    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("using-pause")
        .description("Pauses while using items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> guiPause = sgGeneral.add(new BoolSetting.Builder()
        .name("gui-pause")
        .description("Pauses while a container screen is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shrink = sgGeneral.add(new BoolSetting.Builder()
        .name("shrink")
        .description("Shrinks the render box over time.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fillEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-enabled")
        .description("Enables the render fill.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> fill = sgGeneral.add(new ColorSetting.Builder()
        .name("fill")
        .description("Render fill color.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );

    private final Setting<Boolean> boxEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("box-enabled")
        .description("Enables the render outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> box = sgGeneral.add(new ColorSetting.Builder()
        .name("box")
        .description("Render outline color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-width")
        .description("Render line width.")
        .defaultValue(1.5)
        .range(0.01, 3.0)
        .sliderRange(0.01, 3.0)
        .build()
    );

    private final Setting<Double> sliderSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("slider-speed")
        .description("Render position animation speed.")
        .defaultValue(0.2)
        .range(0.01, 1.0)
        .sliderRange(0.01, 1.0)
        .build()
    );

    private final Setting<Double> startFade = sgGeneral.add(new DoubleSetting.Builder()
        .name("start-fade")
        .description("Seconds before the render starts fading.")
        .defaultValue(0.3)
        .range(0.0, 2.0)
        .sliderRange(0.0, 2.0)
        .build()
    );

    private final Setting<Double> fadeSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-speed")
        .description("Render fade speed.")
        .defaultValue(0.2)
        .range(0.01, 1.0)
        .sliderRange(0.01, 1.0)
        .build()
    );

    private final Setting<Boolean> damageText = sgGeneral.add(new BoolSetting.Builder()
        .name("damage-text")
        .description("Renders the damage value above the render position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Damage text color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private static Constructor<?> ATTACK_CTOR;
    private static Object ATTACK_TYPE;
    private static Field ATTACK_ID_FIELD;
    private static boolean ATTACK_REFLECT_READY;

    private final SynTimer updateTimer = new SynTimer();
    private final SynTimer placeTimer = new SynTimer();
    private final SynTimer breakTimer = new SynTimer();
    private final SynTimer renderTimer = new SynTimer();
    private final SynTimer rotationTimer = new SynTimer();
    private final DecimalFormat damageFormat = new DecimalFormat("0.0");

    private int placeTickCount;
    private Vec3d rotationTarget;
    private BlockPos bestPlacePos;
    private EndCrystalEntity bestBreakCrystal;
    private EndCrystalEntity targetCrystal;
    private PlayerEntity bestTarget;
    private float bestPlaceDamage;
    private float bestBreakDamage;
    private float targetDamage;
    private Vec3d renderTargetPos;
    private Vec3d renderPos;
    private double renderScale;
    private boolean renderActive;

    public CrystalHurtPeople() {
        super(SynAddon.CATEGORY, "CrystalHurtPeople", "Automatically places and breaks crystals.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @Override
    public String getInfoString() {
        if (bestTarget != null && targetDamage > 0.0f) return damageFormat.format(targetDamage);
        return null;
    }

    private void resetState() {
        bestPlacePos = null;
        bestBreakCrystal = null;
        targetCrystal = null;
        bestTarget = null;
        bestPlaceDamage = 0.0f;
        bestBreakDamage = 0.0f;
        targetDamage = 0.0f;
        rotationTarget = null;
        renderTargetPos = null;
        renderPos = null;
        renderScale = 0.0;
    }

    private long crystalPeriod() {
        int v = Math.max(1, cps.get());
        return (long) (1000.0 / (double) v);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        applyRotation();
        ++placeTickCount;
        if (shouldPause()) {
            placePosReset();
            return;
        }
        long period = crystalPeriod();
        if (updateTimer.passed(updateDelay.get())) {
            updateTimer.reset();
            findBestTargets();
            targetCrystal = bestBreakCrystal;
            targetDamage = Math.max(bestPlaceDamage, bestBreakDamage);
        }
        processTargets(period);
    }

    /**
     * 对应原版的实体生成事件逻辑：水晶一旦出现在目标放置格内，立即攻击一次。
     * （此前改为每 tick 扫描会导致同一水晶被重复攻击、每 tick 疯狂发包）
     */
    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!fastLayer.get() || !instantBreak.get()) return;
        if (shouldPause()) return;
        if (bestPlacePos == null) return;
        if (!(event.entity instanceof EndCrystalEntity crystal)) return;

        Box box = new Box(
            bestPlacePos.getX(), bestPlacePos.getY(), bestPlacePos.getZ(),
            bestPlacePos.getX() + 1, bestPlacePos.getY() + 2, bestPlacePos.getZ() + 1
        );
        if (!crystal.getBoundingBox().intersects(box)) return;

        attackCrystal(crystal);
    }

    private void applyRotation() {
        if (rotationTimer.passed(300.0)) rotationTarget = null;
        if (rotationTarget != null) {
            Rotations.rotate(Rotations.getYaw(rotationTarget), Rotations.getPitch(rotationTarget), 100);
        }
    }

    private void setRotationTarget(Vec3d vec) {
        if (!rotations.get() || vec == null) return;
        rotationTarget = vec;
        rotationTimer.reset();
    }

    private Vec3d getPlaceRotationVec(BlockPos pos) {
        BlockPos obsPos = pos.down();
        Direction side = SynUtils.getClickSide(obsPos);
        if (side == null) return obsPos.toCenterPos().add(0.0, 0.5, 0.0);
        return obsPos.toCenterPos().add(
            side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5
        );
    }

    private void placePosReset() {
        bestPlacePos = null;
        bestBreakCrystal = null;
        targetCrystal = null;
        bestTarget = null;
        rotationTarget = null;
    }

    private void processTargets(long period) {
        if (targetCrystal != null) breakCrystal(targetCrystal, period);
        BlockPos pos = bestPlacePos;
        if (pos == null) return;
        if (hasCrystalAt(pos)) {
            breakNearestCrystal(pos);
            return;
        }
        if (placeRate.get() > 1 && placeTickCount % placeRate.get() != 0) return;
        if (onlyBestDamage.get() && bestBreakDamage > 0 && bestPlaceDamage < bestBreakDamage) return;
        if (rotations.get()) setRotationTarget(getPlaceRotationVec(pos));
        placeCrystal(pos, period, false);
    }

    private void findBestTargets() {
        bestBreakCrystal = null;
        bestBreakDamage = 0.0f;
        bestPlacePos = null;
        bestPlaceDamage = 0.0f;
        bestTarget = null;

        List<PlayerEntity> enemies = SynUtils.getEnemies(targetRange.get());
        if (enemies.isEmpty()) return;

        int ticks = predict.get() ? predictTicks.get() : 0;
        Vec3d selfPos = mc.player.getEntityPos();
        float hpPool = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;
            Vec3d crystalPos = crystal.getEntityPos();
            Vec3d attackVec = getVisiblePoint(crystalPos);
            if (attackVec == null) continue;

            double dist = mc.player.getEyePos().distanceTo(attackVec);
            if (dist > breakRange.get()) continue;
            if (!mc.player.canSee(crystal) && dist > wallRange.get()) continue;

            float selfDamage = SynUtils.crystalDamageAt(mc.player, selfPos, crystalPos);
            if (selfDamage > maxSelfDamage.get()) continue;
            if (reserve.get() > 0 && selfDamage > hpPool - reserve.get()) continue;

            for (PlayerEntity enemy : enemies) {
                Vec3d enemyPos = SynUtils.predictPos(enemy, ticks, maxMotionY.get());
                float damage = SynUtils.crystalDamageAt(enemy, enemyPos, crystalPos);
                if (damage <= bestBreakDamage || damage < minDamage.get()) continue;
                bestBreakDamage = damage;
                bestBreakCrystal = crystal;
                bestTarget = enemy;
            }
        }

        double range = placeRange.get() + 1.5;
        for (BlockPos pos : SynUtils.getSphere(range)) {
            Vec3d attackVec = getVisiblePoint(pos.toBottomCenterPos());
            if (attackVec == null) continue;
            if (!isWithinReach(pos.down(), placeRange.get())) continue;
            if (!isMoving(pos)) continue;

            float selfDamage = SynUtils.crystalDamageAt(mc.player, selfPos, pos.toBottomCenterPos());
            if (selfDamage > maxSelfDamage.get()) continue;
            if (reserve.get() > 0 && selfDamage > hpPool - reserve.get()) continue;

            for (PlayerEntity enemy : enemies) {
                Vec3d enemyPos = SynUtils.predictPos(enemy, ticks, maxMotionY.get());
                float damage = SynUtils.crystalDamageAt(enemy, enemyPos, pos.toBottomCenterPos());
                if (damage <= bestPlaceDamage || damage < minDamage.get()) continue;
                bestPlaceDamage = damage;
                bestPlacePos = pos;
                bestTarget = enemy;
            }
        }
    }

    private void breakCrystal(EndCrystalEntity crystal, long period) {
        if (crystal == null || !crystal.isAlive()) return;
        if (!breakTimer.passed(period)) return;
        if (!canBreakCrystal(crystal)) return;
        if (rotations.get()) {
            Vec3d attackVec = getVisiblePoint(crystal.getEntityPos());
            setRotationTarget(attackVec != null ? attackVec : crystal.getEntityPos());
        }
        attackCrystal(crystal);
    }

    private boolean breakNearestCrystal(BlockPos pos) {
        if (!breakTimer.passed(crystalPeriod())) return false;
        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos);
        positions.add(pos.up());
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (x == 0 && z == 0) continue;
                BlockPos offset = pos.add(x, 0, z);
                positions.add(offset);
                positions.add(offset.up());
            }
        }
        List<EndCrystalEntity> crystals = new ArrayList<>();
        for (BlockPos checkPos : positions) {
            for (EndCrystalEntity crystal : SynUtils.getEndCrystals(new Box(checkPos))) {
                if (crystal.isAlive() && !crystals.contains(crystal)) crystals.add(crystal);
            }
        }
        if (crystals.isEmpty()) return false;

        EndCrystalEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (EndCrystalEntity crystal : crystals) {
            Vec3d attackVec = getVisiblePoint(crystal.getEntityPos());
            if (attackVec == null) continue;
            double dist = mc.player.getEyePos().distanceTo(attackVec);
            if (dist > breakRange.get()) continue;
            if (!mc.player.canSee(crystal) && dist > wallRange.get()) continue;
            if (!(dist < closestDist)) continue;
            closestDist = dist;
            closest = crystal;
        }
        if (closest != null) {
            if (rotations.get()) {
                Vec3d attackVec = getVisiblePoint(closest.getEntityPos());
                setRotationTarget(attackVec != null ? attackVec : closest.getEntityPos());
            }
            attackCrystal(closest);
            return true;
        }
        return false;
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (crystal == null || !crystal.isAlive()) return;
        PlayerInteractEntityC2SPacket attack =
            PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking());
        mc.player.networkHandler.sendPacket(attack);
        sendAttackFactor(attack);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.resetTicksSince();
        if (remove.get()) {
            mc.world.removeEntity(crystal.getId(), Entity.RemovalReason.KILLED);
        }
        breakTimer.reset();
        if (strongLoop.get() && bestPlacePos != null && !hasCrystalAt(bestPlacePos)) {
            placeCrystal(bestPlacePos, 0L, true);
        }
    }

    private void sendAttackFactor(PlayerInteractEntityC2SPacket base) {
        int factor = fastLayer.get() ? attackFactor.get() : 1;
        if (factor <= 1) return;

        try {
            ensureAttackReflection();
            if (!ATTACK_REFLECT_READY) return;

            int entityId = ATTACK_ID_FIELD.getInt(base);
            boolean sneaking = base.isPlayerSneaking();

            for (int i = 1; i < factor; ++i) {
                mc.player.networkHandler.sendPacket((Packet<?>) ATTACK_CTOR.newInstance(entityId + i, sneaking, ATTACK_TYPE));
            }
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void ensureAttackReflection() {
        if (ATTACK_REFLECT_READY) return;

        try {
            Class<?> typeHandler = Class.forName(
                "net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket$InteractTypeHandler"
            );
            ATTACK_CTOR = PlayerInteractEntityC2SPacket.class.getDeclaredConstructor(
                int.class, boolean.class, typeHandler
            );
            ATTACK_CTOR.setAccessible(true);

            Field attackField = PlayerInteractEntityC2SPacket.class.getDeclaredField("ATTACK");
            attackField.setAccessible(true);
            ATTACK_TYPE = attackField.get(null);

            ATTACK_ID_FIELD = PlayerInteractEntityC2SPacket.class.getDeclaredField("entityId");
            ATTACK_ID_FIELD.setAccessible(true);

            ATTACK_REFLECT_READY = true;
        } catch (Throwable t) {
            ATTACK_REFLECT_READY = false;
        }
    }

    private void placeCrystal(BlockPos pos, long period, boolean force) {
        if (!force && !placeTimer.passed(period)) return;
        if (hasCrystalAt(pos)) return;
        if (!isMoving(pos)) return;
        if (!isWithinReach(pos.down(), placeRange.get())) return;

        boolean mainHandCrystal = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
        boolean offHandCrystal = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        int crystalSlot = -1;
        if (!mainHandCrystal && !offHandCrystal) {
            if (autoSwap.get() == SwapMode.None) return;
            crystalSlot = findCrystalSlot();
            if (crystalSlot == -1) return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (crystalSlot != -1) swapToSlot(crystalSlot);

        BlockPos obsPos = pos.down();
        Direction facing = SynUtils.getClickSide(obsPos);
        if (facing == null) facing = Direction.UP;

        Hand hand = offHandCrystal ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (crystalSlot != -1) hand = Hand.MAIN_HAND;

        Vec3d hitVec = obsPos.toCenterPos().add(
            facing.getVector().getX() * 0.5, facing.getVector().getY() * 0.5, facing.getVector().getZ() * 0.5
        );
        SynUtils.swingHand(hand, SynUtils.SwingSide.All);
        mc.interactionManager.interactBlock(mc.player, hand, new net.minecraft.util.hit.BlockHitResult(hitVec, facing, obsPos, false));

        if (crystalSlot != -1) {
            SwapMode mode = autoSwap.get();
            if (mode == SwapMode.Silent) {
                SynUtils.switchToSlot(oldSlot);
            } else if (mode == SwapMode.Inventory) {
                SynUtils.inventorySwap(crystalSlot, mc.player.getInventory().getSelectedSlot());
                SynUtils.syncInventory();
            }
        }
        placeTimer.reset();
    }

    private int findCrystalSlot() {
        SwapMode mode = autoSwap.get();
        if (mode == SwapMode.Silent || mode == SwapMode.Normal) {
            return SynUtils.findItem(Items.END_CRYSTAL);
        }
        if (mode == SwapMode.Inventory) {
            return SynUtils.findItemInventorySlot(Items.END_CRYSTAL);
        }
        return -1;
    }

    private void swapToSlot(int slot) {
        if (fastLayer.get() && silentSwap.get()) {
            silentSelectSlot(slot);
            return;
        }
        SwapMode mode = autoSwap.get();
        if (mode == SwapMode.Silent || mode == SwapMode.Normal) {
            SynUtils.switchToSlot(slot);
        } else if (mode == SwapMode.Inventory) {
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        }
    }

    private void silentSelectSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        InvUtils.swap(slot, false);
    }

    private boolean shouldPause() {
        if (usingPause.get() && mc.player.isUsingItem()) return true;
        if (eatingPause.get() && mc.player.isUsingItem()
            && mc.player.getActiveItem().get(net.minecraft.component.DataComponentTypes.FOOD) != null) return true;
        if (usingPause.get() && mc.options.useKey.isPressed()) {
            Item mainHand = mc.player.getMainHandStack().getItem();
            Item offHand = mc.player.getOffHandStack().getItem();
            if (isUsablePauseItem(mainHand) || isUsablePauseItem(offHand)) return true;
        }
        return guiPause.get() && mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen;
    }

    private static boolean isUsablePauseItem(Item item) {
        return item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.CHORUS_FRUIT
            || item == Items.POTION || item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT
            || item == Items.EXPERIENCE_BOTTLE;
    }

    private Vec3d getVisiblePoint(Vec3d vec) {
        return SynUtils.getPointToBoxFromBottom(mc.player.getEyePos(), vec, breakRange.get(), 2.0, 0.1);
    }

    public boolean isMoving(BlockPos pos) {
        BlockPos down = pos.down();
        if (mc.world.getBlockState(down).getBlock() != net.minecraft.block.Blocks.BEDROCK
            && mc.world.getBlockState(down).getBlock() != net.minecraft.block.Blocks.OBSIDIAN) return false;
        if (SynUtils.getClickSideStrict(down) == null) return false;
        if (!mc.world.isAir(pos) && mc.world.getBlockState(pos).getBlock() != net.minecraft.block.Blocks.FIRE) return false;
        if (!isBlockClear(pos)) return false;
        return isBlockClear(pos.up());
    }

    private boolean isBlockClear(BlockPos pos) {
        for (Entity entity : SynUtils.getEntities(new Box(pos))) {
            if (!entity.isAlive() || entity instanceof net.minecraft.entity.ItemEntity
                || entity instanceof EndCrystalEntity) continue;
            return false;
        }
        return true;
    }

    private boolean isWithinReach(BlockPos pos, double range) {
        Direction side = SynUtils.getClickSideStrict(pos);
        if (side == null) return false;
        Vec3d sideOffset = new Vec3d(
            side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5
        );
        return pos.toCenterPos().add(sideOffset).distanceTo(mc.player.getEyePos()) <= range;
    }

    private boolean canBreakCrystal(EndCrystalEntity crystal) {
        Vec3d attackVec = getVisiblePoint(crystal.getEntityPos());
        if (attackVec == null) return false;
        double dist = mc.player.getEyePos().distanceTo(attackVec);
        if (dist > breakRange.get()) return false;
        if (!mc.player.canSee(crystal)) return dist <= wallRange.get();
        return true;
    }

    private boolean hasCrystalAt(BlockPos pos) {
        Box box = new Box(
            pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1
        );
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;
            if (crystal.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        BlockPos blockPos = bestPlacePos;
        if (blockPos != null) {
            renderTargetPos = blockPos.down().toCenterPos();
            renderTimer.reset();
        }
        if (renderTargetPos == null) return;

        boolean active = !renderTimer.passed(startFade.get() * 1000.0);
        renderScale = fadeSpeed.get() >= 1.0
            ? (active ? 0.5 : 0.0)
            : SynUtils.animate(renderScale, active ? 0.5 : 0.0, fadeSpeed.get() / 10.0);

        if (renderScale <= 0.001) {
            renderPos = null;
            return;
        }

        if (renderPos != null && sliderSpeed.get() < 1.0) {
            double speed = sliderSpeed.get() / 10.0;
            renderPos = new Vec3d(
                SynUtils.animate(renderPos.x, renderTargetPos.x, speed),
                SynUtils.animate(renderPos.y, renderTargetPos.y, speed),
                SynUtils.animate(renderPos.z, renderTargetPos.z, speed)
            );
        } else {
            renderPos = renderTargetPos;
        }

        if (!render.get()) return;

        Box renderBox = new Box(renderPos, renderPos);
        renderBox = shrink.get() ? renderBox.expand(renderScale) : renderBox.expand(0.5);
        double scale = renderScale * 2.0;

        SettingColor fillColor = fill.get();
        int fillAlpha = fillEnabled.get() ? (int) ((double) fillColor.a * scale) : 0;
        SettingColor lineColor = box.get();
        int lineAlpha = boxEnabled.get() ? (int) ((double) lineColor.a * scale) : 0;

        renderActive = active;

        event.renderer.box(
            renderBox,
            new meteordevelopment.meteorclient.utils.render.color.Color(fillColor.r, fillColor.g, fillColor.b, fillAlpha),
            new meteordevelopment.meteorclient.utils.render.color.Color(lineColor.r, lineColor.g, lineColor.b, lineAlpha),
            ShapeMode.Both,
            0
        );
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!damageText.get() || !renderActive || targetDamage <= 0.0f || renderPos == null) return;

        Vector3d pos = new Vector3d(renderPos.x, renderPos.y, renderPos.z);
        if (!NametagUtils.to2D(pos, 1.0)) return;

        String text = damageFormat.format(targetDamage);
        int width = mc.textRenderer.getWidth(text);

        NametagUtils.begin(pos, event.drawContext);
        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().scale(0.5f, 0.5f);
        event.drawContext.drawText(mc.textRenderer, text, -width / 2, 0, textColor.get().getPacked(), false);
        event.drawContext.getMatrices().popMatrix();
        NametagUtils.end(event.drawContext);
    }

    public enum SwapMode {
        None,
        Normal,
        Silent,
        Inventory
    }
}
