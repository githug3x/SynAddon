package com.syn.addon.modules;

import com.syn.addon.SynAddon;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of deepseekclient's BlockLag module. Places obsidian blocks around
 * the player to trap them inside a cage, then fires lag packets before the
 * server detects the cage and rubs the player.
 */
public class BlockLag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLag = settings.createGroup("Lag");
    private final SettingGroup sgFake = settings.createGroup("FakeMove");
    private final SettingGroup sgSmart = settings.createGroup("Smart Lag");

    private final Setting<RotateMode> rotate = sgGeneral.add(new EnumSetting.Builder<RotateMode>()
        .name("rotate-mode")
        .description("How rotations are handled while placing blocks.")
        .defaultValue(RotateMode.Bypass)
        .build()
    );

    private final Setting<LagBackMode> lagMode = sgLag.add(new EnumSetting.Builder<LagBackMode>()
        .name("lag-mode")
        .description("Lag-back sequence when trapping below the player.")
        .defaultValue(LagBackMode.TrollHack)
        .build()
    );

    private final Setting<LagBackMode> aboveLagMode = sgLag.add(new EnumSetting.Builder<LagBackMode>()
        .name("above-lag-mode")
        .description("Lag-back sequence when trapping above the player.")
        .defaultValue(LagBackMode.Smart)
        .build()
    );

    private final Setting<Boolean> disable = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-use")
        .description("Auto-disable after a successful lag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jumpDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("jump-disable")
        .description("Disable when the player is sprinting.")
        .defaultValue(true)
        .visible(disable::get)
        .build()
    );

    private final Setting<Double> heightOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-offset")
        .description("Y offset for the CuiCan lag mode.")
        .defaultValue(4.0)
        .min(-10.0).max(10.0).sliderRange(-10.0, 10.0)
        .build()
    );

    private final Setting<Boolean> enderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chest-fallback")
        .description("Fallback slot if no obsidian is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> headFill = sgGeneral.add(new BoolSetting.Builder()
        .name("head-fill")
        .description("Also place blocks above the player's head.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> down = sgGeneral.add(new BoolSetting.Builder()
        .name("down-fill")
        .description("Also place blocks below the player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> packetPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place")
        .description("Use packet-based placement instead of vanilla.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("break-crystal")
        .description("Allow blocks to replace end crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> blocksPer = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks placed per tick.")
        .defaultValue(4)
        .min(1).max(4).sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> inventory = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Swap items via inventory packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fakeMove = sgFake.add(new BoolSetting.Builder()
        .name("fake-move")
        .description("Teleport to an air block if the cage cannot be completed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowCenter = sgFake.add(new BoolSetting.Builder()
        .name("allow-center")
        .description("Use the block centre as a last-resort fake position.")
        .defaultValue(true)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> yOffset = sgFake.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Y offset applied during fake-move teleports.")
        .defaultValue(0.01)
        .min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> smartXZ = sgSmart.add(new DoubleSetting.Builder()
        .name("smart-xz")
        .defaultValue(3.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartUp = sgSmart.add(new DoubleSetting.Builder()
        .name("smart-up")
        .defaultValue(3.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartDown = sgSmart.add(new DoubleSetting.Builder()
        .name("smart-down")
        .defaultValue(3.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartDistance = sgSmart.add(new DoubleSetting.Builder()
        .name("smart-distance")
        .defaultValue(2.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private int progress;
    private final List<BlockPos> placedPos = new ArrayList<>();
    private double previousSlot = -1;

    public BlockLag() {
        super(SynAddon.CATEGORY, "BlockLag", "Traps you in obsidian and fires lag packets before rubberband.");
    }

    @Override
    public void onActivate() { reset(); }

    @Override
    public void onDeactivate() {
        restoreSlot();
        reset();
    }

    private void reset() {
        progress = 0;
        placedPos.clear();
    }

    // ---- Main logic ---------------------------------------------------
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        PlayerEntity player = mc.player;
        if (!player.isAlive() || player.isOnGround()) return;

        if (jumpDisable.get() && disable.get() && player.isSneaking()) {
            disable();
            return;
        }

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        previousSlot = player.getInventory().getSelectedSlot();
        progress = 0;
        placedPos.clear();

        double bx = player.getX();
        double by = player.getY();
        double bz = player.getZ();

        BlockPos p1 = BlockPos.ofFloored(bx, by + 0.5, bz);
        BlockPos p2 = BlockPos.ofFloored(bx, by + 0.5, bz); // placeholder, filled below
        // We don't need 12 positions — deepseekclient's 12 is for the 2x3x2 cage
        // around the player.  Simplify to 4 sides + optional head + optional down.
        List<BlockPos> sidePositions = List.of(
            BlockPos.ofFloored(bx + 1, by, bz),
            BlockPos.ofFloored(bx - 1, by, bz),
            BlockPos.ofFloored(bx, by, bz + 1),
            BlockPos.ofFloored(bx, by, bz - 1)
        );
        List<BlockPos> headPositions = List.of(
            BlockPos.ofFloored(bx + 1, by + 1, bz),
            BlockPos.ofFloored(bx - 1, by + 1, bz),
            BlockPos.ofFloored(bx, by + 1, bz + 1),
            BlockPos.ofFloored(bx, by + 1, bz - 1)
        );
        List<BlockPos> downPositions = List.of(
            BlockPos.ofFloored(bx + 1, by - 1, bz),
            BlockPos.ofFloored(bx - 1, by - 1, bz),
            BlockPos.ofFloored(bx, by - 1, bz + 1),
            BlockPos.ofFloored(bx, by - 1, bz - 1)
        );

        boolean canFillSide = false;
        for (BlockPos p : sidePositions) if (canPlace(p)) { canFillSide = true; break; }
        boolean canFillHead = headFill.get();
        if (canFillHead) {
            for (BlockPos p : headPositions) if (canPlace(p)) { canFillHead = true; break; }
        }
        boolean canFillDown = down.get();
        if (canFillDown) {
            for (BlockPos p : downPositions) if (canPlace(p)) { canFillDown = true; break; }
        }

        if (!canFillSide && !canFillHead && !canFillDown) {
            if (fakeMove.get()) {
                if (!doFakeMove(bx, by, bz)) {
                    if (disable.get()) disable();
                    return;
                }
            } else {
                if (disable.get()) disable();
                return;
            }
        }

        boolean above = canFillHead;

        // 4-step fake-move pre-teleport (from Forever)
        if (!above && fakeMove.get()) {
            sendPos(bx, by + 0.42, bz, false);
            sendPos(bx, by + 0.7532, bz, false);
            sendPos(bx, by + 1.0, bz, false);
            sendPos(bx, by + 1.1661092609382138, bz, false);
        } else if (above && !fakeMove.get()) {
            if (disable.get()) disable();
            return;
        } else if (above && fakeMove.get()) {
            if (!doFakeMove(bx, by, bz)) {
                if (disable.get()) disable();
                return;
            }
        }

        // Swap to block
        doSwap(blockSlot);

        // Rotation snap
        RotateMode rot = rotate.get();
        if (rot == RotateMode.Bypass || rot == RotateMode.Normal) {
            Rotations.rotate(player.getYaw(), 90.0f, 100);
        }

        // Place sides
        boolean rotateEnabled = rot == RotateMode.Normal || rot == RotateMode.Yawstep;
        for (BlockPos p : sidePositions) placeBlock(p, rotateEnabled);
        if (down.get()) for (BlockPos p : downPositions) placeBlock(p, rotateEnabled);
        if (headFill.get() && above) for (BlockPos p : headPositions) placeBlock(p, rotateEnabled);

        // Restore
        doSwap(blockSlot); // inventory-swap restores automatically above
        restoreSlot();

        // Lag back
        LagBackMode mode = above ? aboveLagMode.get() : lagMode.get();
        applyLag(mode, bx, by, bz);

        if (disable.get()) disable();
    }

    // ---- Finding block slot -------------------------------------------
    private int findBlockSlot() {
        PlayerEntity p = mc.player;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() == Items.CRYING_OBSIDIAN) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() == Items.OBSIDIAN) return i;
        }
        if (enderChest.get()) {
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) return i;
            }
        }
        return -1;
    }

    private void doSwap(int slot) {
        PlayerEntity p = mc.player;
        if (slot < 0) return;
        if (inventory.get()) {
            int cur = p.getInventory().getSelectedSlot();
            if (cur != slot) {
                ItemStack tmp = p.getInventory().getStack(cur);
                p.getInventory().setStack(cur, p.getInventory().getStack(slot));
                p.getInventory().setStack(slot, tmp);
                p.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } else {
            p.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void restoreSlot() {
        if (previousSlot < 0 || mc.player == null) return;
        int cur = mc.player.getInventory().getSelectedSlot();
        if (cur != previousSlot) {
            mc.player.getInventory().setSelectedSlot((int) previousSlot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket((int) previousSlot));
        }
    }

    // ---- Placement ----------------------------------------------------
    private boolean canPlace(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isReplaceable()) return false;
        // Check entities blocking
        for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
            if (e.getBlockPos().equals(pos)) {
                if (e == mc.player) continue;
                if (e instanceof EndCrystalEntity && breakCrystal.get()) continue;
                return false;
            }
        }
        return true;
    }

    private Direction findPlaceSide(BlockPos pos) {
        if (mc.world == null) return Direction.UP;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isSideSolid(mc.world, neighbor, dir.getOpposite(), SideShapeType.FULL)) {
                return dir.getOpposite();
            }
        }
        return Direction.UP;
    }

    private void placeBlock(BlockPos pos, boolean rotateEnabled) {
        if (!canPlace(pos)) return;
        if (placedPos.contains(pos)) return;
        if (progress >= blocksPer.get()) return;
        progress++;
        placedPos.add(pos);

        Direction side = findPlaceSide(pos);
        BlockPos neighbor = pos.offset(side);
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (rotateEnabled) {
            Vec3d eyePos = mc.player.getEyePos();
            double yaw = Rotations.getYaw(hitVec);
            double pitch = Rotations.getPitch(hitVec);
            Rotations.rotate(yaw, pitch, 100);
        }

        // Swing hand
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // Place block
        BlockHitResult hit = new BlockHitResult(hitVec, side, neighbor, false);
        if (packetPlace.get()) {
            mc.player.networkHandler.sendPacket(
                new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0)
            );
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        }
    }

    // ---- Fake-move ----------------------------------------------------
    private boolean doFakeMove(double bx, double by, double bz) {
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        // 1st: replaceable + walkable
        for (int[] off : offsets) {
            BlockPos c = BlockPos.ofFloored(bx + off[0], by, bz + off[1]);
            if (canPlace(c) && isWalkable(c)) return gotoPos(c, bx, by, bz);
        }
        // 2nd: any walkable
        for (int[] off : offsets) {
            BlockPos c = BlockPos.ofFloored(bx + off[0], by, bz + off[1]);
            if (isWalkable(c)) return gotoPos(c, bx, by, bz);
        }
        // 3rd: allow center move
        if (allowCenter.get()) {
            for (int[] off : offsets) {
                BlockPos c = BlockPos.ofFloored(bx + off[0], by, bz + off[1]);
                if (isAir(c)) return gotoPos(c, bx, by, bz);
            }
        }
        return false;
    }

    private boolean isAir(BlockPos pos) { return mc.world != null && mc.world.isAir(pos); }
    private boolean isWalkable(BlockPos pos) { return isAir(pos.up()) && isAir(pos.up().up()); }

    private boolean gotoPos(BlockPos target, double bx, double by, double bz) {
        double tx = target.getX() + 0.5;
        double tz = target.getZ() + 0.5;
        double y = by + yOffset.get();

        double x = bx, z = bz;
        double moveDis = 0.25;

        // Step toward target
        if (Math.abs(x - tx) >= moveDis) {
            x += (x > tx ? -moveDis : moveDis);
            sendPos(x, y, z, false);
        }
        if (Math.abs(z - tz) >= moveDis) {
            z += (z > tz ? -moveDis : moveDis);
            sendPos(x, y, z, false);
        }
        sendPos(tx, y, tz, false);
        return true;
    }

    // ---- Lag application (10 modes, ordinal matches enum) --------------
    private void applyLag(LagBackMode mode, double bx, double by, double bz) {
        switch (mode.ordinal()) {
            case 0 -> applySmartLag(bx, by, bz);
            case 1 -> { for (int i = 0; i < 20; i++) sendPos(bx, by + 1337.0, bz, false); }
            case 2 -> sendPos(bx, by + 2.3400880035762786, bz, false);
            case 3 -> sendPos(bx, -70.0, bz, false);
            case 4 -> sendPos(bx, by - heightOffset.get(), bz, false);
            case 5 -> sendPos(bx, -7.0, bz, false);
            case 6 -> sendPos(bx, by + 1.9, bz, false);
            case 7 -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(-180.0f, -90.0f, false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(180.0f, 90.0f, false, false));
            }
            case 8 -> {
                sendPos(bx, by + 1.16610926093821, bz, false);
                sendPos(bx, by + 1.170005801788139, bz, false);
                sendPos(bx, by + 1.2426308013947485, bz, false);
                sendPos(bx, by + 2.3400880035762786, bz, false);
                sendPos(bx, by + 2.640088003576279, bz, false);
            }
            case 9 -> {
                sendPos(bx, by + 1.1001, bz, false);
                sendPos(bx, by + 1.0605, bz, false);
                sendPos(bx, by + 1.0802, bz, false);
                sendPos(bx, by + 1.1127, bz, false);
            }
        }
    }

    private void applySmartLag(double bx, double by, double bz) {
        if (mc.world == null) return;
        List<BlockPos> candidates = new ArrayList<>();
        for (double x = bx - smartXZ.get(); x < bx + smartXZ.get(); x += 1.0) {
            for (double z = bz - smartXZ.get(); z < bz + smartXZ.get(); z += 1.0) {
                for (double y = by - smartDown.get(); y < by + smartUp.get(); y += 1.0) {
                    BlockPos p = BlockPos.ofFloored(x, y, z);
                    if (!mc.world.isAir(p) || !mc.world.isAir(p.up())) continue;
                    candidates.add(p);
                }
            }
        }

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : candidates) {
            double d = Math.sqrt(
                Math.pow(p.getX() + 0.5 - bx, 2)
                    + Math.pow(p.getY() - by, 2)
                    + Math.pow(p.getZ() + 0.5 - bz, 2)
            );
            if (d < bestDist) { bestDist = d; best = p; }
        }
        if (best == null) return;
        sendPos(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, false);
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false)
        );
    }

    // ---- Enums --------------------------------------------------------
    public enum RotateMode { None, Normal, Bypass, Grim, Yawstep }
    public enum LagBackMode { Smart, Invalid, TrollHack, ToVoid, CuiCan, ToVoid2, Normal, Rotation, Fly, Glide }
}
