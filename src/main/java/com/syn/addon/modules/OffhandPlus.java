package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class OffhandPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<OffhandItem> item = sgGeneral.add(new EnumSetting.Builder<OffhandItem>()
        .name("item")
        .description("The item to keep in the offhand.")
        .defaultValue(OffhandItem.Totem)
        .build()
    );

    private final Setting<Boolean> safe = sgGeneral.add(new BoolSetting.Builder()
        .name("safe")
        .description("Only swaps a totem when the player is in danger.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> safeHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold for safe totem swapping.")
        .defaultValue(16.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 36.0)
        .visible(safe::get)
        .build()
    );

    private final Setting<Boolean> lethalCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("lethal-crystal")
        .description("Treats nearby end crystals as lethal.")
        .defaultValue(true)
        .visible(safe::get)
        .build()
    );

    private final Setting<Boolean> gapSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("gap-switch")
        .description("Swaps to a golden apple while using.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> always = sgGeneral.add(new BoolSetting.Builder()
        .name("always")
        .description("Swap to a golden apple whenever the use key is pressed.")
        .defaultValue(false)
        .visible(gapSwitch::get)
        .build()
    );

    private final Setting<Boolean> gapOnTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("gap-totem")
        .description("Swap to a golden apple when holding a totem.")
        .defaultValue(false)
        .visible(gapSwitch::get)
        .build()
    );

    private final Setting<Boolean> gapOnSword = sgGeneral.add(new BoolSetting.Builder()
        .name("gap-sword")
        .description("Swap to a golden apple when holding a sword.")
        .defaultValue(true)
        .visible(gapSwitch::get)
        .build()
    );

    private final Setting<Boolean> gapOnPick = sgGeneral.add(new BoolSetting.Builder()
        .name("gap-pickaxe")
        .description("Swap to a golden apple when holding a pickaxe.")
        .defaultValue(false)
        .visible(gapSwitch::get)
        .build()
    );

    private final Setting<Boolean> mainHandTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("main-hand-totem")
        .description("Keeps a totem in a selected hotbar slot as well.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder()
        .name("slot")
        .description("Hotbar slot used for the main-hand totem.")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(mainHandTotem::get)
        .build()
    );

    private final Setting<Boolean> forceUpdate = sgGeneral.add(new BoolSetting.Builder()
        .name("force-update")
        .description("Always re-select the totem slot even if it is already selected.")
        .defaultValue(false)
        .visible(mainHandTotem::get)
        .build()
    );

    private final Setting<Boolean> withOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("with-offhand")
        .description("Also swap while the offhand already holds the item.")
        .defaultValue(false)
        .visible(mainHandTotem::get)
        .build()
    );

    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>()
        .name("swap-mode")
        .description("How items are moved.")
        .defaultValue(SwapMode.OffhandSwap)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Milliseconds between swaps.")
        .defaultValue(50.0)
        .range(0.0, 500.0)
        .sliderRange(0.0, 500.0)
        .build()
    );

    private final Setting<Timing> timing = sgGeneral.add(new EnumSetting.Builder<Timing>()
        .name("timing")
        .description("Which tick phase to use.")
        .defaultValue(Timing.All)
        .build()
    );

    private final SynTimer timer = new SynTimer();

    public OffhandPlus() {
        super(SynAddon.CATEGORY, "Offhand+", "Keeps a chosen item in the offhand.");
    }

    @Override
    public void onActivate() {
        timer.reset();
    }

    @Override
    public String getInfoString() {
        return item.get().name();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return;
        Entity entity = packet.getEntity(mc.world);
        if (entity != mc.player) return;

        if (mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            mc.player.getInventory().removeStack(0);
        } else if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            mc.player.getInventory().setStack(40, ItemStack.EMPTY);
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (timing.get() == Timing.Post) return;
        tick();
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (timing.get() == Timing.Pre) return;
        tick();
    }

    private boolean lethalCrystal() {
        if (!lethalCrystal.get()) return false;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity)) continue;
            if (mc.player.distanceTo(entity) > 12.0f) continue;
            Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            if (DamageUtils.crystalDamage(mc.player, pos) >= SynUtils.getHealth(mc.player)) {
                return true;
            }
        }
        return false;
    }

    private void tick() {
        if (mc.player == null || mc.world == null) return;
        if (!timer.passed(delay.get())) return;
        if (!SynUtils.inInventory()) return;

        boolean switchMainHandTotem = mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING || withOffhand.get();
        boolean unsafe = (double) SynUtils.getHealth(mc.player) < safeHealth.get() || lethalCrystal();

        int totemSlot = findItemInventorySlot(Items.TOTEM_OF_UNDYING);
        int mainSlot = slot.get() - 1;

        if (mainHandTotem.get() && totemSlot != -1 && mc.player.getInventory().getStack(mainSlot).getItem() != Items.TOTEM_OF_UNDYING) {
            switch (swapMode.get()) {
                case ClickSlot -> {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, mainSlot + 36, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, 0, SlotActionType.PICKUP, mc.player);
                    SynUtils.syncInventory();
                }
                case OffhandSwap -> {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, totemSlot, mainSlot, SlotActionType.SWAP, mc.player);
                    SynUtils.syncInventory();
                }
                case Pick -> {
                    int old = mc.player.getInventory().getSelectedSlot();
                    SynUtils.switchToSlot(mainSlot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(totemSlot));
                    SynUtils.switchToSlot(old);
                }
            }

            if (switchMainHandTotem && (!safe.get() || unsafe)
                && (mainSlot != mc.player.getInventory().getSelectedSlot() || forceUpdate.get())) {
                SynUtils.switchToSlot(mainSlot);
            }
            timer.reset();
            return;
        }

        if (!safe.get()) {
            if (mainHandTotem.get()) {
                if (switchMainHandTotem) {
                    int hotBarSlot = SynUtils.findItem(Items.TOTEM_OF_UNDYING);
                    if (hotBarSlot != -1 && (hotBarSlot != mc.player.getInventory().getSelectedSlot() || forceUpdate.get())) {
                        SynUtils.switchToSlot(hotBarSlot);
                    }
                }
            }
        } else {
            if (unsafe) {
                if (mainHandTotem.get() && switchMainHandTotem) {
                    int hotBarSlot = SynUtils.findItem(Items.TOTEM_OF_UNDYING);
                    if (hotBarSlot != -1 && (hotBarSlot != mc.player.getInventory().getSelectedSlot() || forceUpdate.get())) {
                        SynUtils.switchToSlot(hotBarSlot);
                    }
                } else {
                    swap(Items.TOTEM_OF_UNDYING);
                    timer.reset();
                    return;
                }
            }
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        Item mainItem = mainHand.getItem();
        if (gapSwitch.get()
            && (gapOnSword.get() && isSword(mainItem)
                || gapOnPick.get() && isPickaxe(mainItem)
                || always.get() && mainItem != Items.GOLDEN_APPLE && mainItem != Items.ENCHANTED_GOLDEN_APPLE
                || gapOnTotem.get() && mainItem == Items.TOTEM_OF_UNDYING)
            && mc.options.useKey.isPressed()) {
            swap(Items.GOLDEN_APPLE);
            timer.reset();
            return;
        }

        switch (item.get()) {
            case Totem -> {
                swap(Items.TOTEM_OF_UNDYING);
                timer.reset();
            }
            case Shield -> {
                swap(Items.SHIELD);
                timer.reset();
            }
            case Chorus -> {
                swap(Items.CHORUS_FRUIT);
                timer.reset();
            }
            case Crystal -> {
                swap(Items.END_CRYSTAL);
                timer.reset();
            }
            case Gapple -> {
                swap(Items.GOLDEN_APPLE);
                timer.reset();
            }
            case None -> {
            }
        }
    }

    private void swap(Item item) {
        int itemSlot = item == Items.GOLDEN_APPLE ? getGAppleSlot() : findItemInventorySlot(item);
        if (itemSlot == -1) return;

        switch (swapMode.get()) {
            case ClickSlot -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemSlot, 0, SlotActionType.PICKUP, mc.player);
                SynUtils.syncInventory();
            }
            case OffhandSwap -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemSlot, 40, SlotActionType.SWAP, mc.player);
                SynUtils.syncInventory();
            }
            case Pick -> {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(itemSlot));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0), Direction.DOWN, 0
                ));
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(itemSlot));
            }
        }
    }

    private int getGAppleSlot() {
        return findItemInventorySlot(Items.ENCHANTED_GOLDEN_APPLE) != -1
            ? findItemInventorySlot(Items.ENCHANTED_GOLDEN_APPLE)
            : findItemInventorySlot(Items.GOLDEN_APPLE);
    }

    private int findItemInventorySlot(Item item) {
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() == Items.GOLDEN_APPLE && item == Items.GOLDEN_APPLE) return -1;
        if (offhand.getItem() == Items.ENCHANTED_GOLDEN_APPLE
            && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return -1;
        if (item == offhand.getItem()) return -1;

        if (swapMode.get() == SwapMode.Pick) {
            for (int i = 9; i <= 40; ++i) {
                if (mc.player.getInventory().getStack(i).getItem() == item) return i;
            }
            for (int i = 0; i < 9; ++i) {
                if (mc.player.getInventory().getStack(i).getItem() == item) return i;
            }
        } else {
            for (int i = 40; i >= 0; --i) {
                if (mc.player.getInventory().getStack(i).getItem() == item) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    private static boolean isSword(Item item) {
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD
            || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
    }

    private static boolean isPickaxe(Item item) {
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
            || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    public enum OffhandItem {
        None,
        Totem,
        Crystal,
        Gapple,
        Shield,
        Chorus
    }

    public enum SwapMode {
        ClickSlot,
        OffhandSwap,
        Pick
    }

    public enum Timing {
        All,
        Pre,
        Post
    }
}
