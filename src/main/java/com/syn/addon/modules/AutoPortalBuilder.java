package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动地狱门：开启时以鼠标准心指向的位置为中心，自动放置 4x5 黑曜石框架，
 * 然后用打火石点燃，完成后自动关闭。
 *
 * 设置：
 * - PacketPlace：用发包方式放置（否则走原版交互流程）
 * - InventorySwap：允许从背包中把黑曜石/打火石交换到热键栏
 */
public class AutoPortalBuilder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> packetPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place")
        .description("Places blocks using packets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Swaps obsidian and flint-and-steel from the inventory.")
        .defaultValue(false)
        .build()
    );

    /** 待放置的框架格（按下到上排列，保证每格都有支撑面） */
    private final List<BlockPos> frame = new ArrayList<>();
    private int placeIndex = 0;
    /** 框架内部底排中心（点火位置），即准心指向的基准格 */
    private BlockPos igniteBase = null;

    /** 正在等待生效的物品切换（背包交换需要等服务器回包） */
    private Item pendingItem;
    private int pendingTicks;
    /** 两次点击之间的冷却与重试计数 */
    private int clickCooldown;
    private int retries;

    public AutoPortalBuilder() {
        super(SynAddon.CATEGORY, "AutoPortalBuilder", "Builds a nether portal centered on your crosshair target, then disables itself.");
    }

    @Override
    public void onActivate() {
        frame.clear();
        placeIndex = 0;
        igniteBase = null;
        pendingItem = null;
        pendingTicks = 0;
        clickCooldown = 0;
        retries = 0;

        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            error("请将准心指向一个方块再开启。");
            disable();
            return;
        }

        // 基准格 = 准心指向位置（点击面偏移后，即玩家想放置的那个空气格）
        BlockPos base = hit.getBlockPos().offset(hit.getSide());
        igniteBase = base;

        // 传送门平面朝向与玩家朝向垂直：框架横向轴 = 玩家朝向的侧面
        Direction axis = mc.player.getHorizontalFacing().rotateYClockwise();

        // 4x5 框架：底行 y=0、顶行 y=4 各 4 块，两侧柱 w=-1 / w=2 各 3 块，共 14 块。
        // 按下到上排序，保证放置时总能找到支撑面。
        for (int y = 0; y <= 4; y++) {
            for (int w = -1; w <= 2; w++) {
                boolean isFrame = (y == 0 || y == 4) || (w == -1 || w == 2);
                if (isFrame) {
                    frame.add(base.offset(axis, w).up(y));
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // 背包交换只能用 clickSlot 发包，手上物品要等服务器回包后才更新，
        // 必须等它真正到手上再点击，否则放下去的还是原来的物品。
        if (pendingItem != null) {
            if (handWith(pendingItem) == null) {
                if (++pendingTicks > 40) {
                    error("物品切换超时，请把物品放到快捷栏，或检查服务器是否禁止背包点击。");
                    pendingItem = null;
                    disable();
                }
                return;
            }
            pendingItem = null;
            pendingTicks = 0;
        }

        if (clickCooldown > 0) {
            clickCooldown--;
            return;
        }

        // 第一阶段：放完整个框架（以客户端方块实际形成为准推进，放失败会原地重试）
        while (placeIndex < frame.size()) {
            BlockPos pos = frame.get(placeIndex);
            var state = mc.world.getBlockState(pos);
            if (state.isOf(Blocks.OBSIDIAN)) {
                placeIndex++;
                retries = 0;
                continue;
            }
            if (!state.isReplaceable()) {
                error("目标位置被其他方块阻挡，无法搭建。");
                disable();
                return;
            }

            Direction side = SynUtils.getClickSide(pos);
            if (side == null) {
                if (++retries > 40) {
                    error("找不到可点击的支撑面，请靠近目标再试。");
                    disable();
                } else {
                    clickCooldown = 2;
                }
                return;
            }
            if (!ensureHolding(Items.OBSIDIAN)) {
                reportMissing(Items.OBSIDIAN, "黑曜石");
                return;
            }
            Hand hand = handWith(Items.OBSIDIAN);
            if (hand == null) return; // 等交换生效

            clickBlock(pos.offset(side), side.getOpposite(), hand);
            clickCooldown = 1;
            if (++retries > 60) {
                error("黑曜石放置失败，请检查服务器是否拦截放置。");
                disable();
            }
            return; // 每 tick 只放一格
        }

        // 第二阶段：点火（点击内部底排黑曜石的顶面，在框架内生成火）
        if (mc.world.getBlockState(igniteBase.up()).isOf(Blocks.FIRE)
            || mc.world.getBlockState(igniteBase.up()).isOf(Blocks.NETHER_PORTAL)) {
            disable();
            return;
        }
        if (!mc.world.getBlockState(igniteBase).isOf(Blocks.OBSIDIAN)) {
            // 底排黑曜石还没同步到客户端，稍后重试
            clickCooldown = 2;
            return;
        }
        if (!ensureHolding(Items.FLINT_AND_STEEL)) {
            reportMissing(Items.FLINT_AND_STEEL, "打火石");
            return;
        }
        Hand hand = handWith(Items.FLINT_AND_STEEL);
        if (hand == null) return;

        clickBlock(igniteBase, Direction.UP, hand);
        disable();
    }

    /**
     * 确保指定物品在主手或副手上。热键栏直接切换；背包则在开启 InventorySwap 时交换。
     *
     * @return 背包/快捷栏里是否具备该物品（返回 true 不代表已经拿在手上，需再查 handWith）
     */
    private boolean ensureHolding(Item item) {
        if (handWith(item) != null) return true;

        int hotbar = SynUtils.findItem(item);
        if (hotbar != -1) {
            if (hotbar != mc.player.getInventory().getSelectedSlot()) SynUtils.switchToSlot(hotbar);
            return true;
        }
        if (inventorySwap.get()) {
            int slot = SynUtils.findItemInventorySlot(item);
            if (slot != -1) {
                SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
                pendingItem = item;
                pendingTicks = 0;
                return true;
            }
        }
        return false;
    }

    /** 主手优先，其次副手 */
    private Hand handWith(Item item) {
        if (mc.player.getMainHandStack().isOf(item)) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().isOf(item)) return Hand.OFF_HAND;
        return null;
    }

    private void reportMissing(Item item, String name) {
        boolean anywhere = handWith(item) != null || InvUtils.find(item).found();
        if (anywhere) {
            error("背包里有" + name + "，但 InventorySwap 未开启，请开启或把它放到快捷栏。");
        } else {
            error("背包里没有" + name + "。");
        }
        disable();
    }

    private void clickBlock(BlockPos pos, Direction side, Hand hand) {
        Vec3d hitVec = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );
        Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
        SynUtils.swingHand(hand, SynUtils.SwingSide.All);
        BlockHitResult result = new BlockHitResult(hitVec, side, pos, false);
        if (packetPlace.get()) {
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                hand, result, mc.player.currentScreenHandler.getRevision()
            ));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }
    }
}
