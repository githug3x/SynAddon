package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BurrowPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<RotateMode> rotate = sgGeneral.add(new EnumSetting.Builder<RotateMode>()
        .name("rotate-mode")
        .description("How the client rotates while placing.")
        .defaultValue(RotateMode.Bypass)
        .build()
    );

    private final Setting<LagBackMode> lagMode = sgGeneral.add(new EnumSetting.Builder<LagBackMode>()
        .name("lag-mode")
        .description("Movement packets sent after placing.")
        .defaultValue(LagBackMode.TrollHack)
        .build()
    );

    private final Setting<LagBackMode> aboveLagMode = sgGeneral.add(new EnumSetting.Builder<LagBackMode>()
        .name("move-lag-mode")
        .description("Movement packets sent when moving above.")
        .defaultValue(LagBackMode.Smart)
        .build()
    );

    private final Setting<Boolean> disable = sgGeneral.add(new BoolSetting.Builder()
        .name("disable")
        .description("Disables the module after placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jumpDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("jump-disable")
        .description("Disables the module when jumping.")
        .defaultValue(true)
        .visible(() -> !disable.get())
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Milliseconds between placements.")
        .defaultValue(500)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .visible(() -> !disable.get())
        .build()
    );

    private final Setting<Integer> webTime = sgGeneral.add(new IntSetting.Builder()
        .name("web-time")
        .description("Milliseconds to wait after leaving cobweb.")
        .defaultValue(0)
        .range(0, 500)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> enderChest = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chest")
        .description("Falls back to ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiLag = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-lag")
        .description("Requires a block below before placing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> single = sgGeneral.add(new BoolSetting.Builder()
        .name("single-block")
        .description("Only places in the player's own block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectMine = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-mining")
        .description("Skips blocks being mined.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> headFill = sgGeneral.add(new BoolSetting.Builder()
        .name("head-fill")
        .description("Places blocks above the head.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("using-pause")
        .description("Pauses while using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> down = sgGeneral.add(new BoolSetting.Builder()
        .name("down")
        .description("Places blocks below the player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noSelfPos = sgGeneral.add(new BoolSetting.Builder()
        .name("no-self-pos")
        .description("Never places in the player's own block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packetPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place")
        .description("Places using packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sound = sgGeneral.add(new BoolSetting.Builder()
        .name("sound")
        .description("Plays a placement sound.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> blocksPer = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per")
        .description("Blocks placed per execution.")
        .defaultValue(4)
        .range(1, 4)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> breakCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("break")
        .description("Attacks crystals in the way.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wait = sgGeneral.add(new BoolSetting.Builder()
        .name("wait")
        .description("Waits instead of disabling when blocked.")
        .defaultValue(true)
        .visible(disable::get)
        .build()
    );

    private final Setting<Boolean> fakeMove = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-move")
        .description("Moves the server-side player into the hole.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-center")
        .description("Allows centering the player.")
        .defaultValue(true)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> preCorrect = sgGeneral.add(new DoubleSetting.Builder()
        .name("pre-correct")
        .description("Pre-correction distance.")
        .defaultValue(0.25)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> moveDis = sgGeneral.add(new DoubleSetting.Builder()
        .name("move-dis")
        .description("Move distance.")
        .defaultValue(0.25)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> moveDis2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("move-dis2")
        .description("Second move distance.")
        .defaultValue(0.25)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> moveCorrect2 = sgGeneral.add(new DoubleSetting.Builder()
        .name("correct")
        .description("Final correction distance.")
        .defaultValue(0.25)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Y offset used while fake-moving.")
        .defaultValue(0.01)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(fakeMove::get)
        .build()
    );

    private final Setting<Boolean> inventory = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Swaps blocks from the inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> smartX = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-xz")
        .description("Horizontal search radius for Smart lag.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartUp = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-up")
        .description("Upward search radius for Smart lag.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartDown = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-down")
        .description("Downward search radius for Smart lag.")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final Setting<Double> smartDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("smart-distance")
        .description("Minimum distance for Smart lag.")
        .defaultValue(2.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> lagMode.get() == LagBackMode.Smart || aboveLagMode.get() == LagBackMode.Smart)
        .build()
    );

    private final List<BlockPos> placePos = new ArrayList<>();
    private final SynTimer timer = new SynTimer();
    private final SynTimer webTimer = new SynTimer();
    private int progress = 0;
    private Vec3d currentPos;

    private float prevYaw;
    private float prevPitch;

    public BurrowPlus() {
        super(SynAddon.CATEGORY, "Burrow+", "Places blocks around the player to trap them.");
    }

    @Override
    public void onActivate() {
        progress = 0;
        placePos.clear();
        currentPos = null;
        timer.reset();
        webTimer.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (inventory.get() && !SynUtils.inInventory()) return;

        if (!disable.get() && jumpDisable.get() && mc.player.input.playerInput.jump()) {
            disable();
            return;
        }
        if (isInWeb()) {
            webTimer.reset();
            return;
        }
        if (usingPause.get() && mc.player.isUsingItem()) return;
        if (!webTimer.passed(webTime.get())) return;
        if (!disable.get() && !timer.passed(delay.get())) return;
        if (!mc.player.isOnGround()) return;
        if (antiLag.get() && !canCollide(mc.player, new Box(playerPos(true).down()))) return;

        if (single.get() && SynUtils.isInsideBlock(mc.player)) {
            if (disable.get()) disable();
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int block = getBlock();
        if (block == -1) {
            error("No block found.");
            disable();
            return;
        }

        progress = 0;
        placePos.clear();

        BlockPos pos1 = blockPos(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos2 = blockPos(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos3 = blockPos(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        BlockPos pos4 = blockPos(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        BlockPos pos5 = blockPos(mc.player.getX() + 0.3, mc.player.getY() + 1.5, mc.player.getZ() + 0.3);
        BlockPos pos6 = blockPos(mc.player.getX() - 0.3, mc.player.getY() + 1.5, mc.player.getZ() + 0.3);
        BlockPos pos7 = blockPos(mc.player.getX() + 0.3, mc.player.getY() + 1.5, mc.player.getZ() - 0.3);
        BlockPos pos8 = blockPos(mc.player.getX() - 0.3, mc.player.getY() + 1.5, mc.player.getZ() - 0.3);
        BlockPos pos9 = blockPos(mc.player.getX() + 0.3, mc.player.getY() - 1.0, mc.player.getZ() + 0.3);
        BlockPos pos10 = blockPos(mc.player.getX() - 0.3, mc.player.getY() - 1.0, mc.player.getZ() + 0.3);
        BlockPos pos11 = blockPos(mc.player.getX() + 0.3, mc.player.getY() - 1.0, mc.player.getZ() - 0.3);
        BlockPos pos12 = blockPos(mc.player.getX() - 0.3, mc.player.getY() - 1.0, mc.player.getZ() - 0.3);
        BlockPos playerPos = playerPos(true);

        boolean fillHead = false;
        if (!(canPlace(pos1) || canPlace(pos2) || canPlace(pos3) || canPlace(pos4))) {
            boolean cantHeadFill = !this.headFill.get()
                || (!canPlace(pos5) && !canPlace(pos6) && !canPlace(pos7) && !canPlace(pos8));
            boolean cantDown = !down.get()
                || (!canPlace(pos9) && !canPlace(pos10) && !canPlace(pos11) && !canPlace(pos12));
            if (cantHeadFill) {
                if (cantDown) {
                    if (!wait.get() && disable.get()) disable();
                    return;
                }
            } else {
                fillHead = true;
            }
        }

        boolean above = false;
        BlockPos headPos = playerPos(true).up(2);
        boolean rotate = this.rotate.get() == RotateMode.Normal;

        attackCrystal(pos1, rotate);
        attackCrystal(pos2, rotate);
        attackCrystal(pos3, rotate);
        attackCrystal(pos4, rotate);

        if (fillHead || mc.player.getPose() == EntityPose.SWIMMING
            || trapped(headPos)
            || trapped(headPos.add(1, 0, 0)) || trapped(headPos.add(-1, 0, 0))
            || trapped(headPos.add(0, 0, 1)) || trapped(headPos.add(0, 0, -1))
            || trapped(headPos.add(1, 0, -1)) || trapped(headPos.add(-1, 0, -1))
            || trapped(headPos.add(1, 0, 1)) || trapped(headPos.add(-1, 0, 1))) {
            above = true;
            if (!fakeMove.get()) {
                if (!wait.get() && disable.get()) disable();
                return;
            }
            if (!fakeMove()) return;
        } else {
            sendPos(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ());
            sendPos(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ());
            sendPos(mc.player.getX(), mc.player.getY() + 0.9999957640154541, mc.player.getZ());
            sendPos(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ());
            currentPos = new Vec3d(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ());
        }

        timer.reset();
        doSwap(block);

        if (this.rotate.get() == RotateMode.Bypass) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            if (above && currentPos != null) {
                float[] angle = getRotation(currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), mc.player.getEntityPos());
                snapAt(angle[0], angle[1]);
            } else {
                snapAt(prevYaw, 90.0f);
            }
        }

        placeBlock(playerPos, rotate);
        placeBlock(pos1, rotate);
        placeBlock(pos2, rotate);
        placeBlock(pos3, rotate);
        placeBlock(pos4, rotate);

        if (down.get()) {
            placeBlock(pos9, rotate);
            placeBlock(pos10, rotate);
            placeBlock(pos11, rotate);
            placeBlock(pos12, rotate);
        }
        if (headFill.get() && above) {
            placeBlock(pos5, rotate);
            placeBlock(pos6, rotate);
            placeBlock(pos7, rotate);
            placeBlock(pos8, rotate);
        }

        if (inventory.get()) {
            doSwap(block);
            SynUtils.syncInventory();
        } else {
            doSwap(oldSlot);
        }

        applyLagPackets(above ? aboveLagMode.get() : lagMode.get());

        if (this.rotate.get() == RotateMode.Bypass) {
            snapAt(prevYaw, prevPitch);
        }

        if (disable.get()) disable();
    }

    private void applyLagPackets(LagBackMode mode) {
        switch (mode) {
            case Smart -> {
                List<BlockPos> list = new ArrayList<>();
                for (double x = mc.player.getX() - smartX.get(); x < mc.player.getX() + smartX.get(); x += 1.0) {
                    for (double z = mc.player.getZ() - smartX.get(); z < mc.player.getZ() + smartX.get(); z += 1.0) {
                        for (double y = mc.player.getY() - smartDown.get(); y < mc.player.getY() + smartUp.get(); y += 1.0) {
                            list.add(blockPos(x, y, z));
                        }
                    }
                }
                double bestDistance = 0.0;
                BlockPos bestPos = null;
                for (BlockPos pos : list) {
                    if (!canMove(pos)
                        || (double) MathHelper.sqrt((float) mc.player.squaredDistanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0))) < smartDistance.get()
                        || (bestPos != null && !(mc.player.squaredDistanceTo(pos.toCenterPos()) < bestDistance))) continue;
                    bestPos = pos;
                    bestDistance = mc.player.squaredDistanceTo(pos.toCenterPos());
                }
                if (bestPos != null) {
                    sendPos(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5);
                }
            }
            case Invalid -> {
                for (int i = 0; i < 20; ++i) {
                    sendPos(mc.player.getX(), mc.player.getY() + 1337.0, mc.player.getZ());
                }
            }
            case Fly -> {
                sendPos(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 1.170005801788139, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 1.2426308013947485, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 2.640088003576279, mc.player.getZ());
            }
            case Glide -> {
                sendPos(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ());
                sendPos(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ());
            }
            case TrollHack -> sendPos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ());
            case Normal -> sendPos(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ());
            case ToVoid -> sendPos(mc.player.getX(), -70.0, mc.player.getZ());
            case ToVoid2 -> sendPos(mc.player.getX(), -7.0, mc.player.getZ());
            case Rotation -> {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(-180.0f, -90.0f, false, false));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(180.0f, 90.0f, false, false));
            }
        }
    }

    private boolean fakeMove() {
        double[] offsets = new double[]{1.0, 0.0, -1.0};
        List<BlockPos> offList = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();
        for (double x : offsets) {
            for (double z : offsets) {
                offList.add(blockPos(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z));
            }
        }

        for (BlockPos pos : offList) {
            if (!checkSelf(pos) || canReplace(pos) || (headFill.get() && canReplace(pos.up()))) continue;
            gotoPos(pos);
            return true;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : offList) {
            if (playerPos.equals(pos) || !checkSelf(pos) || !canMove(pos)) continue;
            candidates.add(pos);
        }
        if (!candidates.isEmpty()) {
            gotoPos(closest(candidates));
            return true;
        }

        candidates.clear();
        for (BlockPos pos : offList) {
            if (playerPos.equals(pos) || !checkSelf(pos)) continue;
            candidates.add(pos);
        }
        if (!candidates.isEmpty()) {
            gotoPos(closest(candidates));
            return true;
        }

        if (!center.get()) return false;

        offList.clear();
        offList.add(blockPos(mc.player.getX() + 1.0, mc.player.getY(), mc.player.getZ()));
        offList.add(blockPos(mc.player.getX() - 1.0, mc.player.getY(), mc.player.getZ()));
        offList.add(blockPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() - 1.0));
        offList.add(blockPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 1.0));
        for (BlockPos pos : offList) {
            if (!canMove(pos)) continue;
            gotoPos(pos);
            return true;
        }

        if (!wait.get() && disable.get()) disable();
        return false;
    }

    private BlockPos closest(List<BlockPos> positions) {
        double best = 10.0;
        BlockPos bestPos = null;
        for (BlockPos pos : positions) {
            double distance = mc.player.getEntityPos().distanceTo(pos.toCenterPos().add(0.0, -0.5, 0.0));
            if (!(distance < best) && bestPos != null) continue;
            bestPos = pos;
            best = distance;
        }
        return bestPos;
    }

    private void placeBlock(BlockPos pos, boolean rotate) {
        if (!canPlace(pos) || placePos.contains(pos) || progress >= blocksPer.get()) return;

        placePos.add(pos);
        Direction side = getPlaceSide(pos);
        if (side == null) return;

        ++progress;
        if (sound.get()) {
            mc.world.playSound(mc.player, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        }
        clickBlock(pos.offset(side), side.getOpposite(), rotate, Hand.MAIN_HAND, packetPlace.get());
    }

    private void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand, boolean packet) {
        Vec3d directionVec = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        float oldYaw = mc.player.getYaw();
        float oldPitch = mc.player.getPitch();
        if (rotate && currentPos != null) {
            float[] angle = getRotation(currentPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), directionVec);
            snapAt(angle[0], angle[1]);
        }

        SynUtils.swingHand(hand, SynUtils.SwingSide.All);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);

        if (packet) {
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(hand, result, 0));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }

        if (rotate) {
            snapAt(oldYaw, oldPitch);
        }
    }

    private void attackCrystal(BlockPos pos, boolean rotate) {
        if (!breakCrystal.get()) return;
        for (Entity entity : mc.world.getOtherEntities(mc.player, new Box(pos))) {
            if (!(entity instanceof EndCrystalEntity)) continue;

            float oldYaw = mc.player.getYaw();
            float oldPitch = mc.player.getPitch();
            if (rotate) {
                Vec3d closest = new Vec3d(
                    MathHelper.clamp(mc.player.getX(), entity.getBoundingBox().minX, entity.getBoundingBox().maxX),
                    MathHelper.clamp(mc.player.getEyeY(), entity.getBoundingBox().minY, entity.getBoundingBox().maxY),
                    MathHelper.clamp(mc.player.getZ(), entity.getBoundingBox().minZ, entity.getBoundingBox().maxZ)
                );
                float[] angle = getRotation(closest, mc.player.getEyePos());
                snapAt(angle[0], angle[1]);
            }

            mc.player.networkHandler.sendPacket(net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
            mc.player.resetTicksSince();
            SynUtils.swingHand(Hand.MAIN_HAND, SynUtils.SwingSide.All);

            if (rotate) snapAt(oldYaw, oldPitch);
        }
    }

    private void doSwap(int slot) {
        if (inventory.get()) {
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            SynUtils.switchToSlot(slot);
        }
    }

    private void gotoPos(BlockPos offPos) {
        double targetX = offPos.getX() + 0.5;
        double targetZ = offPos.getZ() + 0.5;
        double x = mc.player.getX();
        double z = mc.player.getZ();
        double y = mc.player.getY() + yOffset.get();

        double xDiff = Math.abs(x - targetX);
        double zDiff = Math.abs(z - targetZ);

        double correction = preCorrect.get();
        if (correction > 0.0) {
            if (xDiff >= correction) {
                x = x > targetX ? x - correction : x + correction;
                sendPos(x, y, z);
            }
            if (zDiff >= correction) {
                z = z > targetZ ? z - correction : z + correction;
                sendPos(x, y, z);
            }
        }

        xDiff = Math.abs(x - targetX);
        zDiff = Math.abs(z - targetZ);
        double step = moveDis.get();
        if (step > 0.0) {
            while (xDiff > step) {
                x = x > targetX ? x - step : x + step;
                sendPos(x, y, z);
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > step) {
                z = z > targetZ ? z - step : z + step;
                sendPos(x, y, z);
                zDiff = Math.abs(z - targetZ);
            }
        }

        step = moveDis2.get();
        if (step > 0.0) {
            while (xDiff > step) {
                x = x > targetX ? x - step : x + step;
                sendPos(x, y, z);
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > step) {
                z = z > targetZ ? z - step : z + step;
                sendPos(x, y, z);
                zDiff = Math.abs(z - targetZ);
            }
        }

        correction = moveCorrect2.get();
        if (correction > 0.0) {
            if (xDiff >= correction) {
                x = x > targetX ? x - correction : x + correction;
                sendPos(x, y, z);
            }
            if (zDiff >= correction) {
                z = z > targetZ ? z - correction : z + correction;
                sendPos(x, y, z);
            }
        }

        currentPos = new Vec3d(x, y, z);
    }

    private void sendPos(double x, double y, double z) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
    }

    private boolean canMove(BlockPos pos) {
        return mc.world.isAir(pos) && mc.world.isAir(pos.up());
    }

    private boolean canPlace(BlockPos pos) {
        if (noSelfPos.get() && pos.equals(playerPos(true))) return false;
        if (getPlaceSide(pos) == null) return false;
        if (!canReplace(pos)) return false;
        if (detectMine.get() && isMining(pos)) return false;
        return !hasEntity(pos);
    }

    private boolean isMining(BlockPos pos) {
        if (mc.interactionManager == null || !mc.interactionManager.isBreakingBlock()) return false;
        return mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit
            && hit.getBlockPos().equals(pos);
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : mc.world.getOtherEntities(null, new Box(pos))) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (entity instanceof ItemEntity
                || entity instanceof ExperienceOrbEntity
                || entity instanceof ExperienceBottleEntity
                || entity instanceof ArrowEntity
                || (entity instanceof EndCrystalEntity && breakCrystal.get())) continue;
            return true;
        }
        return false;
    }

    private boolean checkSelf(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new Box(pos));
    }

    private boolean trapped(BlockPos pos) {
        return (canCollide(mc.player, new Box(pos)) || mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB)
            && checkSelf(pos.down(2));
    }

    private boolean canReplace(BlockPos pos) {
        if (pos.getY() >= 320) return false;
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean canCollide(Entity entity, Box box) {
        return mc.world.getBlockCollisions(entity, box).iterator().hasNext();
    }

    private Direction getPlaceSide(BlockPos pos) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (mc.world.getBlockState(neighbor).isReplaceable()) continue;
            if (!mc.world.getBlockState(neighbor).isSideSolidFullSquare(mc.world, neighbor, direction.getOpposite())) continue;
            double distance = mc.player.getEyePos().squaredDistanceTo(
                pos.toCenterPos().add(
                    direction.getVector().getX() * 0.5,
                    direction.getVector().getY() * 0.5,
                    direction.getVector().getZ() * 0.5
                )
            );
            if (side != null && distance >= minDistance) continue;
            side = direction;
            minDistance = distance;
        }
        return side;
    }

    private int getBlock() {
        if (inventory.get()) {
            int obsidian = SynUtils.findBlockInventorySlot(Blocks.OBSIDIAN);
            if (obsidian != -1 || !enderChest.get()) return obsidian;
            return SynUtils.findBlockInventorySlot(Blocks.ENDER_CHEST);
        }
        int obsidian = SynUtils.findBlock(Blocks.OBSIDIAN);
        if (obsidian != -1 || !enderChest.get()) return obsidian;
        return SynUtils.findBlock(Blocks.ENDER_CHEST);
    }

    private boolean isInWeb() {
        for (float x : new float[]{0.0f, 0.3f, -0.3f}) {
            for (float z : new float[]{0.0f, 0.3f, -0.3f}) {
                for (int y : new int[]{-1, 0, 1, 2}) {
                    BlockPos pos = blockPos(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z).up(y);
                    if (new Box(pos).intersects(mc.player.getBoundingBox())
                        && mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) return true;
                }
            }
        }
        return false;
    }

    private BlockPos playerPos(boolean fix) {
        return blockPos(mc.player.getX(), mc.player.getY() + (fix ? 0.3 : 0.0), mc.player.getZ());
    }

    private static BlockPos blockPos(double x, double y, double z) {
        return new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    private void snapAt(float yaw, float pitch) {
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private static float[] getRotation(Vec3d target, Vec3d from) {
        double dx = target.x - from.x;
        double dy = target.y - from.y;
        double dz = target.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distance)));
        return new float[]{yaw, pitch};
    }

    public enum RotateMode {
        Bypass,
        Normal,
        None
    }

    public enum LagBackMode {
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
