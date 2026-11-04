package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Port of the original MaceSpoof exploit. Five different ways of spoofing
 * movement data so a mace attack is accepted by the server.
 */
public class MaceSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Spoof method used for the mace attack.")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Boolean> onlyGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-ground")
        .description("Only spoofs while the player is on the ground.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Height sent between the duplicated ground packets.")
        .defaultValue(25.0)
        .min(0.0)
        .max(2000.0)
        .sliderRange(0.0, 2000.0)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Boolean> noCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("no-crystal")
        .description("Ignores attacks on end crystals.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Swap)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Swaps the mace from the inventory instead of the hotbar.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Swap || mode.get() == Mode.SwapVanilla || mode.get() == Mode.Bounce)
        .build()
    );

    private final Setting<Double> bounceInterval = sgGeneral.add(new DoubleSetting.Builder()
        .name("bounce-interval")
        .description("Ticks between two bounce packets.")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .visible(() -> mode.get() == Mode.Bounce && !this.fakeFall.get())
        .build()
    );

    private final Setting<Double> timeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("timeout")
        .description("Time in milliseconds before the bounce state stops.")
        .defaultValue(5000.0)
        .min(1000.0)
        .max(30000.0)
        .sliderRange(1000.0, 30000.0)
        .visible(() -> mode.get() == Mode.Bounce && !this.fakeFall.get())
        .build()
    );

    private final Setting<Double> minFallDist = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance needed to start bouncing.")
        .defaultValue(3.0)
        .min(1.0)
        .max(50.0)
        .sliderRange(1.0, 50.0)
        .visible(() -> mode.get() == Mode.Bounce && !this.fakeFall.get())
        .build()
    );

    private final Setting<Boolean> fakeHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-height")
        .description("Sends a fake height packet before the attack.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Bounce && !this.fakeFall.get() && !this.rubberband.get())
        .build()
    );

    private final Setting<Double> fakeHeightVal = sgGeneral.add(new DoubleSetting.Builder()
        .name("fake-height-value")
        .description("Fake height used before the attack.")
        .defaultValue(5.0)
        .min(1.0)
        .max(50.0)
        .sliderRange(1.0, 50.0)
        .visible(() -> mode.get() == Mode.Bounce && this.fakeHeight.get() && !this.fakeFall.get() && !this.rubberband.get())
        .build()
    );

    private final Setting<Boolean> rubberband = sgGeneral.add(new BoolSetting.Builder()
        .name("rubberband")
        .description("Sends a rubberband sequence after the attack.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Bounce && !this.fakeFall.get())
        .build()
    );

    private final Setting<Double> rbHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("rb-height")
        .description("Height used by the rubberband sequence.")
        .defaultValue(5.0)
        .min(1.0)
        .max(50.0)
        .sliderRange(1.0, 50.0)
        .visible(() -> mode.get() == Mode.Bounce && rubberband.get() && !this.fakeFall.get())
        .build()
    );

    private final Setting<Boolean> fakeFall = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-fall")
        .description("Sends a fake fall sequence before the attack.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Bounce)
        .build()
    );

    private final Setting<Double> fakeFallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fake-fall-distance")
        .description("Height of the fake fall sequence.")
        .defaultValue(100.0)
        .min(10.0)
        .max(500.0)
        .sliderRange(10.0, 500.0)
        .visible(() -> mode.get() == Mode.Bounce && fakeFall.get())
        .build()
    );

    private final Setting<Double> fakeFallSteps = sgGeneral.add(new DoubleSetting.Builder()
        .name("fake-fall-steps")
        .description("Number of packets in the fake fall sequence.")
        .defaultValue(20.0)
        .min(5.0)
        .max(50.0)
        .sliderRange(5.0, 50.0)
        .visible(() -> mode.get() == Mode.Bounce && fakeFall.get())
        .build()
    );

    private final SynTimer bounceTimer = new SynTimer();
    private PlayerInteractEntityC2SPacket pendingPacket;
    private boolean attacking;
    private boolean rbActive;
    private int bounceCounter;

    public MaceSpoof() {
        super(SynAddon.CATEGORY, "MaceSpoof", "Spoofs movement packets to extend mace range.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
        pendingPacket = null;
    }

    private void resetState() {
        rbActive = false;
        attacking = false;
        bounceCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (mode.get() != Mode.Bounce) return;
        if (fakeFall.get()) return;
        if (!rbActive) return;

        if (bounceTimer.passed(timeout.get())) {
            deactivateBounce();
            return;
        }

        bounceCounter++;
        if (bounceCounter >= (int) bounceInterval.get().doubleValue()) {
            bounceCounter = 0;
            sendPos(mc.player.getX(), mc.player.getY() + 1.0E-9, mc.player.getZ(), false);
            mc.player.onLanding();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;

        switch (mode.get()) {
            case Vanilla -> handleVanilla(event);
            case NCP -> forceAirborne(event);
            case Swap -> handleSwap(event);
            case Bounce -> {
                forceAirborne(event);
                if (event.isCancelled()) return;
                handleBounceAttack(event);
            }
            case SwapVanilla -> {
                if (event.isCancelled()) return;
                handleSwapVanilla(event);
            }
        }
    }

    private void handleVanilla(PacketEvent.Send event) {
        if (mc.player.getMainHandStack().getItem() != Items.MACE) return;
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket interact)) return;
        if (!isAttack(interact)) return;

        Entity entity = getPacketEntity(interact);
        if (entity instanceof EndCrystalEntity) return;
        if (onlyGround.get() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) return;
        if (mc.player.isInLava()) return;
        if (mc.player.isSubmergedInWater()) return;
        if (entity == null) return;

        for (int i = 0; i < 4; i++) sendPos(0.0);
        sendPos(height.get());
        sendPos(0.0);
    }

    private void handleSwap(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (attacking) return;

        int slot = findMaceSlot();
        if (slot == -1) return;

        if (!(event.packet instanceof PlayerInteractEntityC2SPacket interact)) return;
        if (!isAttack(interact)) return;
        if (noCrystal.get() && getPacketEntity(interact) instanceof EndCrystalEntity) return;

        pendingPacket = interact;
        attacking = true;
        doSwapAttack();
        attacking = false;
        event.cancel();
    }

    private void handleSwapVanilla(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket interact)) return;
        if (!isAttack(interact)) return;

        Entity entity = getPacketEntity(interact);
        if (entity == null) return;
        if (entity instanceof EndCrystalEntity) return;

        int slot = findMaceSlot();
        if (slot == -1) return;
        if (onlyGround.get() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) return;

        event.cancel();

        int current = mc.player.getInventory().getSelectedSlot();
        switchSlot(slot);

        for (int i = 0; i < 4; i++) sendPos(0.0);
        sendPos(height.get());
        sendPos(0.0);

        event.sendSilently(interact);

        if (inventorySwap.get()) {
            switchSlot(slot);
        } else {
            switchSlot(current);
        }
    }

    private void handleBounceAttack(PacketEvent.Send event) {
        if (attacking) return;
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket interact)) return;
        if (!isAttack(interact)) return;

        Entity entity = getPacketEntity(interact);
        if (entity == null) return;
        if (entity instanceof EndCrystalEntity) return;

        int slot = findMaceSlot();
        if (slot == -1) return;

        event.cancel();

        int current = mc.player.getInventory().getSelectedSlot();
        attacking = true;
        switchSlot(slot);

        if (fakeFall.get()) {
            sendFallSequence();
            event.sendSilently(interact);
            if (inventorySwap.get()) {
                switchSlot(slot);
            } else {
                switchSlot(current);
            }
        } else {
            if (fakeHeight.get()) {
                sendPos(fakeHeightVal.get());
                sendPos(0.0);
            }

            event.sendSilently(interact);

            if (rubberband.get()) {
                sendRubberbandSequence();
                deactivateBounce();
                if (inventorySwap.get()) {
                    switchSlot(slot);
                } else {
                    switchSlot(current);
                }
            } else {
                if (!mc.player.isOnGround() && mc.player.fallDistance >= minFallDist.get()) {
                    rbActive = true;
                    bounceTimer.reset();
                    bounceCounter = 0;
                }
                if (inventorySwap.get()) {
                    switchSlot(slot);
                } else {
                    switchSlot(current);
                }
            }
        }

        attacking = false;
    }

    private void doSwapAttack() {
        if (pendingPacket == null) return;

        int slot = findMaceSlot();
        if (slot == -1) return;

        int current = mc.player.getInventory().getSelectedSlot();
        switchSlot(slot);
        mc.player.networkHandler.sendPacket(pendingPacket);
        if (inventorySwap.get()) {
            switchSlot(slot);
        } else {
            switchSlot(current);
        }
    }

    private void sendRubberbandSequence() {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double top = y + rbHeight.get();

        sendPos(x, top, z);
        sendPos(x, y, z);
        sendPos(x, top, z);
        for (int i = 0; i < 3; i++) {
            sendPos(x, top, z);
        }
    }

    private void sendFallSequence() {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        double top = y + fakeFallDistance.get();
        int steps = (int) fakeFallSteps.get().doubleValue();

        for (int i = 0; i <= steps; i++) {
            double yy = top + (y - top) * ((double) i / (double) steps);
            sendPos(x, yy, z);
        }
    }

    private void deactivateBounce() {
        rbActive = false;
        bounceCounter = 0;
    }

    private void switchSlot(int slot) {
        if (inventorySwap.get()) {
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            SynUtils.switchToSlot(slot);
        }
    }

    private int findMaceSlot() {
        if (inventorySwap.get()) {
            return SynUtils.findItemInventorySlot(Items.MACE);
        }
        return SynUtils.findItem(Items.MACE);
    }

    private void sendPos(double offset) {
        sendPos(mc.player.getX(), mc.player.getY() + offset, mc.player.getZ(), false);
    }

    private void sendPos(double x, double y, double z) {
        sendPos(x, y, z, false);
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false)
        );
    }

    /**
     * Replaces the packet with an identical copy that has onGround set to false.
     */
    private void forceAirborne(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket packet) || !packet.isOnGround()) return;

        event.cancel();

        double x = packet.getX(mc.player.getX());
        double y = packet.getY(mc.player.getY());
        double z = packet.getZ(mc.player.getZ());
        float yaw = packet.getYaw(mc.player.getYaw());
        float pitch = packet.getPitch(mc.player.getPitch());
        boolean collision = packet.horizontalCollision();

        if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround) {
            event.sendSilently(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, collision));
        } else if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround) {
            event.sendSilently(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, false, collision));
        } else if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly) {
            event.sendSilently(new PlayerMoveC2SPacket.OnGroundOnly(false, collision));
        } else {
            event.sendSilently(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, collision));
        }
    }

    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        AttackDetector detector = new AttackDetector();
        packet.handle(detector);
        return detector.attack;
    }

    private static Entity getPacketEntity(PlayerInteractEntityC2SPacket packet) {
        if (packet instanceof IPlayerInteractEntityC2SPacket accessor) {
            return accessor.meteor$getEntity();
        }
        return null;
    }

    private static final class AttackDetector implements PlayerInteractEntityC2SPacket.Handler {
        private boolean attack;

        @Override
        public void interact(Hand hand) {
        }

        @Override
        public void interactAt(Hand hand, Vec3d pos) {
        }

        @Override
        public void attack() {
            attack = true;
        }
    }

    public enum Mode {
        Vanilla,
        NCP,
        Swap,
        SwapVanilla,
        Bounce
    }
}
