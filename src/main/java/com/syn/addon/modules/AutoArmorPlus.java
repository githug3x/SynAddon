package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

public class AutoArmorPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<EnchantPriority> head = sgGeneral.add(new EnumSetting.Builder<EnchantPriority>()
        .name("head")
        .description("Enchant priority for helmets.")
        .defaultValue(EnchantPriority.Protection)
        .build()
    );

    private final Setting<EnchantPriority> body = sgGeneral.add(new EnumSetting.Builder<EnchantPriority>()
        .name("body")
        .description("Enchant priority for chestplates and elytras.")
        .defaultValue(EnchantPriority.Protection)
        .build()
    );

    private final Setting<EnchantPriority> tights = sgGeneral.add(new EnumSetting.Builder<EnchantPriority>()
        .name("tights")
        .description("Enchant priority for leggings.")
        .defaultValue(EnchantPriority.Protection)
        .build()
    );

    private final Setting<EnchantPriority> feet = sgGeneral.add(new EnumSetting.Builder<EnchantPriority>()
        .name("feet")
        .description("Enchant priority for boots.")
        .defaultValue(EnchantPriority.Protection)
        .build()
    );

    private final Setting<Boolean> ignoreCurse = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-curse")
        .description("Skip armor with Binding Curse.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noMove = sgGeneral.add(new BoolSetting.Builder()
        .name("no-move")
        .description("Pauses while the player is moving.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between armor updates.")
        .defaultValue(3)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> autoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-elytra")
        .description("Equips a usable elytra when ElytraFly is enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<HotbarSwapMode> hotbarSwap = sgGeneral.add(new EnumSetting.Builder<HotbarSwapMode>()
        .name("hotbar-swap")
        .description("How armor from the hotbar is equipped.")
        .defaultValue(HotbarSwapMode.Swap)
        .build()
    );

    private final Setting<InventorySwapMode> inventorySwap = sgGeneral.add(new EnumSetting.Builder<InventorySwapMode>()
        .name("inventory-swap")
        .description("How armor from the inventory is equipped.")
        .defaultValue(InventorySwapMode.ClickSlot)
        .build()
    );

    private int tickDelay = 0;

    public AutoArmorPlus() {
        super(SynAddon.CATEGORY, "AutoArmor+", "Automatically equips the best armor from the inventory.");
    }

    @Override
    public void onActivate() {
        tickDelay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!SynUtils.inInventory()) return;
        if (mc.player.playerScreenHandler != mc.player.currentScreenHandler) return;
        if (SynUtils.isMoving() && noMove.get()) return;

        if (tickDelay > 0) {
            --tickDelay;
            return;
        }
        tickDelay = delay.get();

        Map<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
        armorMap.put(EquipmentSlot.FEET, new int[]{36, getProtection(mc.player.getInventory().getStack(36)), -1, -1});
        armorMap.put(EquipmentSlot.LEGS, new int[]{37, getProtection(mc.player.getInventory().getStack(37)), -1, -1});
        armorMap.put(EquipmentSlot.CHEST, new int[]{38, getProtection(mc.player.getInventory().getStack(38)), -1, -1});
        armorMap.put(EquipmentSlot.HEAD, new int[]{39, getProtection(mc.player.getInventory().getStack(39)), -1, -1});

        ElytraFlyPlus elytraFly = Modules.get().get(ElytraFlyPlus.class);
        boolean elytraOn = elytraFly != null && elytraFly.isActive();
        boolean elytraPacket = elytraFly != null && elytraFly.isPacketEnabled();

        for (int s = 0; s < 36; ++s) {
            ItemStack stack = mc.player.getInventory().getStack(s);
            boolean elytra = SynUtils.isElytra(stack);
            if (!SynUtils.isArmor(stack) && !elytra) continue;

            if (elytra && ((!elytraOn && autoElytra.get()) || (elytraOn && elytraPacket))) continue;

            int protection = getProtection(stack);
            EquipmentSlot slot = elytra ? EquipmentSlot.CHEST : SynUtils.armorSlot(stack);
            if (slot == null) continue;

            for (Map.Entry<EquipmentSlot, int[]> entry : armorMap.entrySet()) {
                if (autoElytra.get() && elytraOn && entry.getKey() == EquipmentSlot.CHEST) {
                    ItemStack currentChest = mc.player.getInventory().getStack(38);
                    int[] data = entry.getValue();
                    boolean currentUsable = !currentChest.isEmpty() && SynUtils.isElytra(currentChest) && isElytraUsable(currentChest);
                    boolean pendingUsable = data[2] != -1
                        && !mc.player.getInventory().getStack(data[2]).isEmpty()
                        && SynUtils.isElytra(mc.player.getInventory().getStack(data[2]))
                        && isElytraUsable(mc.player.getInventory().getStack(data[2]));
                    if (currentUsable || pendingUsable || stack.isEmpty() || !elytra || !isElytraUsable(stack)) continue;
                    data[2] = s;
                    continue;
                }

                int[] data = entry.getValue();
                if (protection <= 0 || entry.getKey() != slot || protection <= data[1] || protection <= data[3]) continue;
                data[2] = s;
                data[3] = protection;
            }
        }

        for (Map.Entry<EquipmentSlot, int[]> entry : armorMap.entrySet()) {
            int[] data = entry.getValue();
            if (data[2] == -1) continue;

            if (data[2] < 9) {
                switch (hotbarSwap.get()) {
                    case Swap -> {
                        int armorSlot = 44 - data[0];
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, armorSlot, data[2], SlotActionType.SWAP, mc.player
                        );
                        SynUtils.syncInventory();
                    }
                    case Switch -> {
                        int old = mc.player.getInventory().getSelectedSlot();
                        SynUtils.switchToSlot(data[2]);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        SynUtils.switchToSlot(old);
                    }
                }
            } else if (mc.player.playerScreenHandler == mc.player.currentScreenHandler) {
                int armorSlot = 44 - data[0];
                int newArmorSlot = data[2];

                switch (inventorySwap.get()) {
                    case Pick -> {
                        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(newArmorSlot));
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(newArmorSlot));
                    }
                    case ClickSlot -> {
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player
                        );
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player
                        );
                        if (data[1] != -1) {
                            mc.interactionManager.clickSlot(
                                mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player
                            );
                        }
                    }
                }
                SynUtils.syncInventory();
            }
            return;
        }
    }

    private boolean isElytraUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    private int getProtection(ItemStack stack) {
        boolean elytra = SynUtils.isElytra(stack);
        if (!SynUtils.isArmor(stack) && !elytra) {
            return stack.isEmpty() ? -1 : 0;
        }

        EquipmentSlot slot = elytra ? EquipmentSlot.BODY : SynUtils.armorSlot(stack);
        int prot = 0;

        if (elytra) {
            if (!isElytraUsable(stack)) return 0;
            prot = 1;
        }

        int blastMultiplier = 1;
        int protectionMultiplier = 1;

        if (slot != null) {
            switch (slot) {
                case HEAD -> {
                    if (head.get() == EnchantPriority.Protection) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case BODY -> {
                    if (body.get() == EnchantPriority.Protection) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case LEGS -> {
                    if (tights.get() == EnchantPriority.Protection) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case FEET -> {
                    if (feet.get() == EnchantPriority.Protection) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                default -> {
                }
            }
        }

        if (stack.hasEnchantments()) {
            prot += SynUtils.enchantLevel(stack, Enchantments.PROTECTION) * protectionMultiplier;
            prot += SynUtils.enchantLevel(stack, Enchantments.BLAST_PROTECTION) * blastMultiplier;
            if (SynUtils.enchantLevel(stack, Enchantments.BINDING_CURSE) > 0 && ignoreCurse.get()) {
                prot = -999;
            }
        }

        return (int) Math.round(SynUtils.armorPoints(stack)) + prot;
    }

    public enum EnchantPriority {
        Blast,
        Protection
    }

    public enum HotbarSwapMode {
        Swap,
        Switch
    }

    public enum InventorySwapMode {
        ClickSlot,
        Pick
    }
}
