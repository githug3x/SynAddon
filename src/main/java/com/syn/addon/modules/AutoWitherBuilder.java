package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 移植自 teto client 的 AutoWitherBuilder（自动搭建凋零）。
 *
 * 开启时以玩家朝向 2 格处为基点搭建 T 形灵魂沙 + 3 个凋零骷髅头，
 * 凋零生成后自动用指定名称的命名牌为其命名，然后关闭。
 *
 * 对原版的必要修复（原版逻辑会导致流程卡死，移植时一并修正）：
 * 1. 原版切换物品直接改 selectedSlot 字段且不发 UpdateSelectedSlotC2SPacket，
 *    服务器仍按旧槽位交互 → 这里用 SynUtils.switchToSlot 正常发包切换。
 * 2. 原版放两侧头颅时点击下方灵魂沙的"侧面"，落点是已被占据的中心格，
 *    永远无法放下 → 三个头颅统一改为点击其下方方块的顶面。
 */
public class AutoWitherBuilder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between placements.")
        .defaultValue(3).range(1, 10).sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> autoRename = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-name")
        .description("Renames the wither with a name tag after it spawns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renameDelay = sgGeneral.add(new IntSetting.Builder()
        .name("name-delay")
        .description("Ticks to wait after the wither spawns before renaming.")
        .defaultValue(5).range(1, 20).sliderRange(1, 20)
        .build()
    );

    private final Setting<String> witherName = sgGeneral.add(new StringSetting.Builder()
        .name("wither-name")
        .description("The custom name the name tag must have.")
        .defaultValue("§c§l凋零 BOSS")
        .visible(autoRename::get)
        .build()
    );

    private int placeTimer = 0;
    private int renameTimer = 0;
    private int stage = 0;
    private BlockPos witherPos;
    private boolean witherSpawned = false;
    /** 正在等待生效的背包交换（clickSlot 要等服务器回包手上才更新） */
    private Item pendingItem;
    private int pendingTicks;

    public AutoWitherBuilder() {
        super(SynAddon.CATEGORY, "AutoWitherBuilder", "Automatically builds a wither and optionally renames it.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }
        placeTimer = 0;
        renameTimer = 0;
        stage = 0;
        witherSpawned = false;
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Direction facing = Direction.getFacing(lookVec.x, lookVec.y, lookVec.z);
        witherPos = mc.player.getBlockPos().offset(facing, 2);
        info("§a[自动搭建凋零] §f开始搭建凋零，请确保背包中有3个凋零骷髅头颅和4个灵魂沙！");
    }

    @Override
    public void onDeactivate() {
        info("§c[自动搭建凋零] §f已停止搭建");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (witherSpawned) {
            handleRename();
            return;
        }
        // 背包交换要等服务器回包，物品真正到手上后才继续放置
        if (pendingItem != null) {
            if (handWith(pendingItem) == null) {
                if (++pendingTicks > 40) {
                    error("§c[自动搭建凋零] §f切换物品超时，请把材料放到快捷栏。");
                    pendingItem = null;
                    disable();
                }
                return;
            }
            pendingItem = null;
            pendingTicks = 0;
        }
        if (!hasRequiredItems()) {
            error("§c[自动搭建凋零] §f缺少材料！需要3个凋零骷髅头颅和4个灵魂沙");
            disable();
            return;
        }
        if (placeTimer > 0) {
            --placeTimer;
            return;
        }

        switch (stage) {
            case 0 -> placeSoulSand();
            case 1 -> placeSoulSandTop();
            case 2 -> placeSkulls();
            case 3 -> {
                info("§a[自动搭建凋零] §f凋零搭建完成！");
                witherSpawned = true;
            }
        }
    }

    private void placeSoulSand() {
        if (mc.world.getBlockState(witherPos).getBlock() != Blocks.SOUL_SAND) {
            placeBlock(witherPos, Items.SOUL_SAND);
            placeTimer = delay.get();
        } else {
            stage = 1;
        }
    }

    private void placeSoulSandTop() {
        // 顶排沿东西轴向：up、up.west、up.east
        for (BlockPos pos : new BlockPos[]{witherPos.up(), witherPos.up().west(), witherPos.up().east()}) {
            if (mc.world.getBlockState(pos).getBlock() == Blocks.SOUL_SAND) continue;
            placeBlock(pos, Items.SOUL_SAND);
            placeTimer = delay.get();
            return;
        }
        stage = 2;
    }

    private void placeSkulls() {
        BlockPos centerSkull = witherPos.up(2);
        BlockPos leftSkull = witherPos.west().up(2);
        BlockPos rightSkull = witherPos.east().up(2);
        boolean centerPlaced = mc.world.getBlockState(centerSkull).getBlock() == Blocks.WITHER_SKELETON_SKULL;
        boolean leftPlaced = mc.world.getBlockState(leftSkull).getBlock() == Blocks.WITHER_SKELETON_SKULL;
        boolean rightPlaced = mc.world.getBlockState(rightSkull).getBlock() == Blocks.WITHER_SKELETON_SKULL;
        if (centerPlaced && leftPlaced && rightPlaced) {
            stage = 3;
        } else if (!centerPlaced) {
            placeSkull(centerSkull);
            placeTimer = delay.get();
        } else if (!leftPlaced) {
            placeSkull(leftSkull);
            placeTimer = delay.get();
        } else if (!rightPlaced) {
            placeSkull(rightSkull);
            placeTimer = delay.get();
        }
    }

    private void placeBlock(BlockPos pos, Item item) {
        if (!ensureHolding(item)) return;
        Hand hand = handWith(item);
        if (hand == null) return; // 背包交换还没生效，下一轮再放
        // 优先点击目标下方方块的顶面；否则退到任意可点击侧面
        if (canClickTop(pos.down())) {
            clickBlock(pos.down(), Direction.UP, hand);
            return;
        }
        Direction side = SynUtils.getClickSide(pos);
        if (side != null) {
            clickBlock(pos.offset(side), side.getOpposite(), hand);
        }
    }

    private void placeSkull(BlockPos pos) {
        if (!ensureHolding(Items.WITHER_SKELETON_SKULL)) return;
        Hand hand = handWith(Items.WITHER_SKELETON_SKULL);
        if (hand == null) return; // 背包交换还没生效，下一轮再放
        // 修复原版 bug：三个头颅统一点击下方方块顶面放置
        if (canClickTop(pos.down())) {
            clickBlock(pos.down(), Direction.UP, hand);
            return;
        }
        Direction side = SynUtils.getClickSide(pos);
        if (side != null) {
            clickBlock(pos.offset(side), side.getOpposite(), hand);
        }
    }

    private boolean canClickTop(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return !state.isReplaceable() && state.isSideSolidFullSquare(mc.world, pos, Direction.UP);
    }

    private void clickBlock(BlockPos pos, Direction side, Hand hand) {
        Vec3d hitVec = Vec3d.ofCenter(pos);
        mc.interactionManager.interactBlock(
            mc.player, hand, new BlockHitResult(hitVec, side, pos, false)
        );
    }

    /**
     * 确保物品在主手或副手。热键栏直接切换；背包用 SWAP 点击交换（需等服务器回包生效）。
     */
    private boolean ensureHolding(Item item) {
        if (handWith(item) != null) return true;
        int hotbar = SynUtils.findItem(item);
        if (hotbar != -1) {
            if (hotbar != mc.player.getInventory().getSelectedSlot()) {
                SynUtils.switchToSlot(hotbar);
            }
            return true;
        }
        int slot = SynUtils.findItemInventorySlot(item);
        if (slot != -1) {
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
            pendingItem = item;
            pendingTicks = 0;
            return true;
        }
        return false;
    }

    /** 主手优先，其次副手 */
    private Hand handWith(Item item) {
        if (mc.player.getMainHandStack().isOf(item)) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().isOf(item)) return Hand.OFF_HAND;
        return null;
    }

    private boolean hasRequiredItems() {
        return countItem(Items.WITHER_SKELETON_SKULL) >= 3 && countItem(Items.SOUL_SAND) >= 4;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            count += stack.getCount();
        }
        return count;
    }

    private void handleRename() {
        if (!autoRename.get()) return;
        if (renameTimer > 0) {
            --renameTimer;
            return;
        }
        if (!hasNameTag()) {
            error("§c[自动搭建凋零] §f缺少命名牌！");
            disable();
            return;
        }
        BlockPos playerPos = mc.player.getBlockPos();
        Box box = new Box(
            playerPos.getX() - 5, playerPos.getY() - 5, playerPos.getZ() - 5,
            playerPos.getX() + 5, playerPos.getY() + 5, playerPos.getZ() + 5
        );
        mc.world.getEntitiesByClass(WitherEntity.class, box, entity -> true)
            .forEach(this::renameWither);
        disable();
    }

    private boolean hasNameTag() {
        for (int i = 0; i < 36; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.NAME_TAG) return true;
        }
        return false;
    }

    private void renameWither(WitherEntity wither) {
        int slot = findNamedNameTag();
        if (slot == -1) {
            error("§c[自动搭建凋零] §f请先在铁砧中将命名牌命名为 \"" + witherName.get() + "\"！");
            return;
        }
        if (slot < 9) {
            if (slot != mc.player.getInventory().getSelectedSlot()) {
                SynUtils.switchToSlot(slot);
            }
        } else {
            SynUtils.inventorySwap(slot < 9 ? slot + 36 : slot, mc.player.getInventory().getSelectedSlot());
        }
        mc.interactionManager.interactEntity(mc.player, wither, Hand.MAIN_HAND);
    }

    private int findNamedNameTag() {
        String targetName = witherName.get();
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.NAME_TAG) continue;
            if (!stack.getName().getString().equals(targetName)) continue;
            return i;
        }
        return -1;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!witherSpawned) return;
        if (!(event.packet instanceof PlaySoundS2CPacket packet)) return;
        if (packet.getSound().value() == SoundEvents.ENTITY_WITHER_SPAWN) {
            renameTimer = renameDelay.get();
        }
    }
}
