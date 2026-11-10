package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 完整复刻 aevum client 的 VoidMaceGhost（虚空锤Ghost）。
 *
 * 原理：向服务器发送下潜到虚空再回来的移动包，让服务器累计巨大的 fallDistance，
 * 随后立刻用重锤攻击目标触发猛击增伤，而客户端玩家实际从未移动，不受摔落伤害。
 *
 * 与原版一一对应：
 * - onTick 的门禁顺序：attackDelay → chargeWait → groundOnly → totemGuard → flyCancel → hasMaceReady → findTarget → rotate → execute
 * - execute 的 dest 计算（belowWorld / diveHeight / bottom 钳制）、fall >= 20、spaceCheck、waterCancel
 * - 攻击前后各两个/一个 sendPos 包
 * - ghostHand / normalAttack / swapTo / swapBack / findMaceSlot / isMace(Breach) 逻辑
 */
public class VoidMaceGhost extends Module {
    // ---------------- Target ----------------
    private final SettingGroup sgTarget = settings.createGroup("Target");

    private final Setting<Double> targetRange = sgTarget.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to search for targets.")
        .defaultValue(8.0).range(1.0, 30.0).sliderRange(1.0, 30.0)
        .build()
    );

    private final Setting<Double> attackRange = sgTarget.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Maximum distance allowed to attack.")
        .defaultValue(6.0).range(1.0, 10.0).sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Boolean> players = sgTarget.add(new BoolSetting.Builder()
        .name("players")
        .description("Targets players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mobs = sgTarget.add(new BoolSetting.Builder()
        .name("mobs")
        .description("Targets hostile mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Does not target friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgTarget.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the target.")
        .defaultValue(false)
        .build()
    );

    // ---------------- Path ----------------
    private final SettingGroup sgPath = settings.createGroup("Path");

    private final Setting<Double> diveHeight = sgPath.add(new DoubleSetting.Builder()
        .name("dive-height")
        .description("How far to fake-dive below your feet.")
        .defaultValue(26.0).range(21.0, 60.0).sliderRange(21.0, 60.0)
        .build()
    );

    private final Setting<Boolean> belowWorld = sgPath.add(new BoolSetting.Builder()
        .name("below-world")
        .description("Dives below the world bottom instead of a fixed height.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> voidY = sgPath.add(new DoubleSetting.Builder()
        .name("void-y")
        .description("Y level used when diving below the world.")
        .defaultValue(-70.0).range(-200.0, 64.0).sliderRange(-200.0, 64.0)
        .visible(belowWorld::get)
        .build()
    );

    // ---------------- Safe ----------------
    private final SettingGroup sgSafe = settings.createGroup("Safe");

    private final Setting<Boolean> waterCancel = sgSafe.add(new BoolSetting.Builder()
        .name("water-cancel")
        .description("Cancels when the dive path contains fluids.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> flyCancel = sgSafe.add(new BoolSetting.Builder()
        .name("fly-cancel")
        .description("Cancels while fall flying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> totemGuard = sgSafe.add(new BoolSetting.Builder()
        .name("totem-guard")
        .description("Cancels when your health is too low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> totemMinHp = sgSafe.add(new DoubleSetting.Builder()
        .name("totem-min-hp")
        .description("Minimum health required to attack.")
        .defaultValue(4.0).range(0.0, 20.0).sliderRange(0.0, 20.0)
        .visible(totemGuard::get)
        .build()
    );

    private final Setting<Boolean> groundOnly = sgSafe.add(new BoolSetting.Builder()
        .name("ground-only")
        .description("Only works while on ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> spaceCheck = sgSafe.add(new BoolSetting.Builder()
        .name("space-check")
        .description("Requires an air column for the dive path.")
        .defaultValue(true)
        .build()
    );

    // ---------------- Misc ----------------
    private final SettingGroup sgMisc = settings.createGroup("Misc");

    private final Setting<Boolean> ghostHand = sgMisc.add(new BoolSetting.Builder()
        .name("ghost-hand")
        .description("Swaps to the mace only for the attack packet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breachSwap = sgMisc.add(new BoolSetting.Builder()
        .name("breach-swap")
        .description("Only uses maces enchanted with Breach.")
        .defaultValue(false)
        .visible(ghostHand::get)
        .build()
    );

    private final Setting<Boolean> ghostInventory = sgMisc.add(new BoolSetting.Builder()
        .name("ghost-inventory")
        .description("Finds the mace in the whole inventory and swaps via inventory click.")
        .defaultValue(false)
        .visible(ghostHand::get)
        .build()
    );

    private final Setting<Boolean> autoMace = sgMisc.add(new BoolSetting.Builder()
        .name("auto-mace")
        .description("Allows swapping to a mace when not holding one.")
        .defaultValue(true)
        .visible(() -> !ghostHand.get())
        .build()
    );

    private final Setting<Boolean> syncBack = sgMisc.add(new BoolSetting.Builder()
        .name("sync-back")
        .description("Sends a close-screen packet after swapping back.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chargeWait = sgMisc.add(new BoolSetting.Builder()
        .name("charge-wait")
        .description("Waits for the attack cooldown to fully charge.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> attackDelay = sgMisc.add(new DoubleSetting.Builder()
        .name("attack-delay")
        .description("Milliseconds between attacks.")
        .defaultValue(2000.0).range(0.0, 5000.0).sliderRange(0.0, 5000.0)
        .build()
    );

    private final Setting<Boolean> swing = sgMisc.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand when attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debug = sgMisc.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints why the module is idle.")
        .defaultValue(false)
        .build()
    );

    private final SynTimer attackTimer = new SynTimer();
    private double lastFall;
    private String lastReason = "";

    public VoidMaceGhost() {
        super(SynAddon.CATEGORY, "VoidMaceGhost", "Fake dive for mace smash fall distance, restored without fall damage.");
    }

    @Override
    public void onActivate() {
        lastFall = 0.0;
        lastReason = "";
        attackTimer.reset();
    }

    @Override
    public String getInfoString() {
        if (lastFall > 0.0) {
            return String.format("%.0f", lastFall);
        }
        return lastReason.isEmpty() ? "idle" : lastReason;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (!attackTimer.passed(attackDelay.get())) return;
        if (chargeWait.get() && mc.player.getAttackCooldownProgress(0.5f) < 0.99f) {
            reason("charge");
            return;
        }
        if (groundOnly.get() && !mc.player.isOnGround()) {
            reason("air");
            return;
        }
        if (totemGuard.get() && SynUtils.getHealth(mc.player) <= totemMinHp.get()) {
            reason("lowhp");
            return;
        }
        if (flyCancel.get() && mc.player.isGliding()) {
            reason("elytra");
            return;
        }
        if (!hasMaceReady()) {
            reason("nomace");
            return;
        }
        Entity target = findTarget();
        if (target == null) {
            reason("notarget");
            return;
        }
        if (rotate.get()) {
            Vec3d point = getClosestPointToBox(mc.player.getEyePos(), target.getBoundingBox());
            Rotations.rotate(Rotations.getYaw(point), Rotations.getPitch(point));
        }
        if (execute(target)) {
            attackTimer.reset();
            lastReason = "";
        }
    }

    private boolean execute(Entity target) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        double bottom = mc.world.getBottomY();
        double dest;
        if (belowWorld.get()) {
            dest = Math.min(voidY.get(), bottom - 4.0);
        } else {
            dest = y - diveHeight.get();
            if (dest < bottom + 1.0) {
                dest = bottom + 1.0;
            }
        }
        double fall = y - dest;
        if (fall < 20.0) {
            reason("lowspace");
            return false;
        }
        if (spaceCheck.get() && !airColumn(x, dest, y, z)) {
            reason("blocked");
            return false;
        }
        if (waterCancel.get() && fluidBetween(dest, y, x, z)) {
            reason("water");
            return false;
        }

        sendPos(x, dest, z, false);
        sendPos(x, y, z, false);

        if (ghostHand.get()) {
            ghostAttack(target);
        } else {
            normalAttack(target);
        }

        sendPos(x, y, z, false);

        lastFall = fall;
        note("smash fall=" + String.format("%.1f", fall) + " dest=" + String.format("%.1f", dest));
        return true;
    }

    private void normalAttack(Entity entity) {
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        mc.player.resetTicksSinceLastAttack();
        if (swing.get()) {
            SynUtils.swingHand(Hand.MAIN_HAND, SynUtils.SwingSide.All);
        }
    }

    private void ghostAttack(Entity entity) {
        int slot = findMaceSlot();
        if (slot == -1) {
            normalAttack(entity);
            return;
        }
        boolean inventoryMode = ghostInventory.get() && slot >= 36;
        int selected = mc.player.getInventory().getSelectedSlot();
        swapTo(slot, inventoryMode);
        try {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
            mc.player.resetTicksSinceLastAttack();
            if (swing.get()) {
                SynUtils.swingHand(Hand.MAIN_HAND, SynUtils.SwingSide.All);
            }
        } finally {
            swapBack(slot, selected, inventoryMode);
        }
    }

    private void swapTo(int slot, boolean inventoryMode) {
        if (inventoryMode) {
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            SynUtils.switchToSlot(slot);
        }
    }

    private void swapBack(int slot, int selected, boolean inventoryMode) {
        if (inventoryMode) {
            SynUtils.inventorySwap(slot, selected);
            // 原版此处调用 EntityUtil.syncInventory()，其内部由 AntiCheat.CloseScreen
            // 门控（默认关）。这里直接让 SyncBack 设置生效：发送关闭界面包同步背包。
            if (syncBack.get()) {
                SynUtils.closeScreen();
            }
        } else if (selected >= 0 && selected != mc.player.getInventory().getSelectedSlot()) {
            mc.player.getInventory().setSelectedSlot(selected);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selected));
        }
    }

    private boolean hasMaceReady() {
        if (ghostHand.get()) {
            return findMaceSlot() != -1;
        }
        if (mc.player.getMainHandStack().getItem() == Items.MACE) {
            return true;
        }
        return autoMace.get() && findMaceSlot() != -1;
    }

    private int findMaceSlot() {
        boolean breach = breachSwap.get() && ghostHand.get();
        if (ghostHand.get() && ghostInventory.get()) {
            for (int i = 0; i < 36; i++) {
                if (isMace(mc.player.getInventory().getStack(i), breach)) {
                    return i < 9 ? i + 36 : i;
                }
            }
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (isMace(mc.player.getInventory().getStack(i), breach)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMace(ItemStack stack, boolean requireBreach) {
        if (stack.getItem() != Items.MACE) {
            return false;
        }
        if (!requireBreach) {
            return true;
        }
        return SynUtils.enchantLevel(stack, Enchantments.BREACH) > 0;
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        if (mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
    }

    private boolean airColumn(double x, double fromY, double toY, double z) {
        if (mc.world == null) {
            return false;
        }
        double lo = Math.min(fromY, toY);
        double hi = Math.max(fromY, toY);
        for (double cy = lo; cy <= hi; cy += 1.0) {
            BlockPos pos = BlockPos.ofFloored(x, cy, z);
            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean fluidBetween(double fromY, double toY, double x, double z) {
        if (mc.world == null) {
            return true;
        }
        double lo = Math.min(fromY, toY);
        double hi = Math.max(fromY, toY);
        for (double cy = lo; cy <= hi; cy += 1.0) {
            if (!mc.world.getBlockState(BlockPos.ofFloored(x, cy, z)).getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Entity findTarget() {
        double range = targetRange.get();
        double rangeSq = range * range;
        double attack = attackRange.get();
        Entity best = null;
        double bestSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !players.get()) {
                continue;
            }
            if (!acceptPlayer(player)) {
                continue;
            }
            double d = mc.player.squaredDistanceTo(player);
            if (d > rangeSq || d > attack * attack || d >= bestSq) {
                continue;
            }
            bestSq = d;
            best = player;
        }
        if (best != null) {
            return best;
        }
        if (!mobs.get()) {
            return null;
        }
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity instanceof EndCrystalEntity) {
                continue;
            }
            if (!(entity instanceof MobEntity) || entity instanceof AnimalEntity) {
                continue;
            }
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            double d = mc.player.squaredDistanceTo(entity);
            if (d > rangeSq || d > attack * attack || d >= bestSq) {
                continue;
            }
            bestSq = d;
            best = entity;
        }
        return best;
    }

    private boolean acceptPlayer(PlayerEntity player) {
        if (!player.isAlive() || player.isRemoved() || player.getHealth() <= 0.0f) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        if (ignoreFriends.get()) {
            try {
                if (Friends.get().isFriend(player)) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
        }
        return Double.isFinite(player.getX()) && Double.isFinite(player.getZ());
    }

    // 原版 MathUtil.getClosestPointToBox
    private static Vec3d getClosestPointToBox(Vec3d pos, Box box) {
        double closestX = Math.max(box.minX, Math.min(pos.x, box.maxX));
        double closestY = Math.max(box.minY, Math.min(pos.y, box.maxY));
        double closestZ = Math.max(box.minZ, Math.min(pos.z, box.maxZ));
        return new Vec3d(closestX, closestY, closestZ);
    }

    private void reason(String why) {
        if (!why.equals(lastReason)) {
            lastReason = why;
            if (debug.get()) {
                info("§7[VoidMaceGhost] 卡住: " + why);
            }
        }
    }

    private void note(String message) {
        if (debug.get()) {
            info("§7[VoidMaceGhost] " + message);
        }
    }
}
