package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

public class MaceGod extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> attackRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("attack-range")
        .description("Target range.")
        .defaultValue(6.0)
        .range(1.0, 20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> voidY = sgGeneral.add(new DoubleSetting.Builder()
        .name("void-y")
        .description("Y position used while attacking.")
        .defaultValue(-70.0)
        .range(-500.0, 320.0)
        .sliderRange(-500.0, 320.0)
        .build()
    );

    private final Setting<Integer> apt = sgGeneral.add(new IntSetting.Builder()
        .name("apt")
        .description("Attacks sent per tick.")
        .defaultValue(1)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> autoMace = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-mace")
        .description("Automatically selects a mace.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder()
        .name("swap")
        .description("Swaps to a mace while attacking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> breachSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("breach-swap")
        .description("Prefers a mace with enchantments.")
        .defaultValue(false)
        .visible(swap::get)
        .build()
    );

    private final Setting<Boolean> noCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("no-crystal")
        .description("Does not attack end crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Swaps the mace from the inventory.")
        .defaultValue(false)
        .visible(swap::get)
        .build()
    );

    private final Setting<Boolean> customDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Uses a custom attack delay.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Milliseconds between attacks.")
        .defaultValue(500)
        .range(50, 3000)
        .sliderRange(50, 3000)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Boolean> mobs = sgGeneral.add(new BoolSetting.Builder()
        .name("mobs")
        .description("Targets mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableInteract = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-interact")
        .description("Disables interaction tweaks while active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCrystalSync = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-crystal-sync")
        .description("Disables crystal bot while active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ghostHand = sgGeneral.add(new BoolSetting.Builder()
        .name("ghost-hand")
        .description("Swaps the mace from the inventory without changing the selected slot.")
        .defaultValue(false)
        .visible(swap::get)
        .build()
    );

    private final SynTimer attackTimer = new SynTimer();

    private Entity target;
    private int prevSlot = -1;
    private boolean holdingMace;

    public MaceGod() {
        super(SynAddon.CATEGORY, "MaceGodPlus", "Attacks targets with the mace from below the world.");
    }

    @Override
    public void onActivate() {
        prevSlot = -1;
        holdingMace = false;
        attackTimer.reset();
    }

    @Override
    public void onDeactivate() {
        restoreSlot();
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        target = findTarget();
        if (target == null) {
            holdingMace = false;
            restoreSlot();
            return;
        }
        if (customDelay.get() && !attackTimer.passed(attackDelay.get())) return;

        doVoidSwap();
        try {
            int attacks = apt.get();
            for (int i = 0; i < attacks; ++i) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), voidY.get(), mc.player.getZ(), false, false
                ));
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false
                ));
            }
        } finally {
            restoreSlot();
        }
        attackTimer.reset();
    }

    private void doVoidSwap() {
        if (!swap.get()) {
            holdingMace = mc.player.getMainHandStack().getItem() == Items.MACE;
            return;
        }
        if (mc.player.getMainHandStack().getItem() == Items.MACE && !ghostHand.get()) {
            holdingMace = true;
            return;
        }
        if (holdingMace) return;

        if (ghostHand.get()) {
            int invSlot = SynUtils.findItemInventorySlot(Items.MACE);
            if (invSlot != -1) {
                SynUtils.inventorySwap(invSlot, mc.player.getInventory().getSelectedSlot());
                holdingMace = true;
            }
            return;
        }

        int slot = findMaceSlot();
        if (slot != -1) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            if (inventorySwap.get()) {
                SynUtils.inventorySwap(slot + 36, mc.player.getInventory().getSelectedSlot());
            } else {
                SynUtils.switchToSlot(slot);
            }
            holdingMace = true;
        }
    }

    private void restoreSlot() {
        if (prevSlot != -1 && !inventorySwap.get()) {
            SynUtils.switchToSlot(prevSlot);
        }
        prevSlot = -1;
        holdingMace = false;
    }

    private int findMaceSlot() {
        int best = -1;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.MACE) continue;
            if (best == -1) best = i;
            if (!breachSwap.get() || !stack.hasEnchantments()) continue;
            best = i;
            break;
        }
        return best;
    }

    private Entity findTarget() {
        Entity best = null;
        double bestDist = attackRange.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || entity == mc.player || !entity.isAlive() || !isValid(entity)) continue;
            if (entity instanceof EndCrystalEntity) {
                if (noCrystal.get()) continue;
                return entity;
            }
            if (!(entity instanceof PlayerEntity) && (!(entity instanceof MobEntity) || !mobs.get())) continue;
            double dist = mc.player.distanceTo(entity);
            if (!(dist <= bestDist)) continue;
            bestDist = dist;
            best = entity;
        }
        return best;
    }

    private static boolean isValid(Entity entity) {
        return entity.isAlive() && entity.isAttackable() && !entity.isInvulnerable();
    }
}
