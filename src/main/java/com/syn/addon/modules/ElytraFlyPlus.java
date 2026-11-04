package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import com.syn.addon.utils.SynTimer;
import com.syn.addon.utils.SynUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ElytraFlyPlus extends Module {
    public final Setting<Mode> mode = settings.getDefaultGroup().add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Elytra flight mode.")
        .defaultValue(Mode.Control)
        .build()
    );

    public final Setting<Boolean> infiniteDura = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("infinite-dura")
        .description("Repeats the chest slot interaction to refresh durability.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> packet = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("packet")
        .description("Uses packet-based elytra flight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> packetDelay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("packet-delay")
        .description("Ticks between packet interactions.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .visible(packet::get)
        .build()
    );

    private final Setting<Boolean> setFlag = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("set-flag")
        .description("Starts gliding client-side too.")
        .defaultValue(false)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    public final Setting<Boolean> firework = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("firework")
        .description("Uses firework-style velocity control.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Keybind> fireWork = settings.getDefaultGroup().add(new KeybindSetting.Builder()
        .name("firework-bind")
        .description("Key used to launch a firework.")
        .defaultValue(Keybind.none())
        .visible(firework::get)
        .build()
    );

    public final Setting<Boolean> packetInteract = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("packet-interact")
        .description("Uses an interact packet for fireworks.")
        .defaultValue(true)
        .visible(firework::get)
        .build()
    );

    public final Setting<Boolean> inventory = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("Allows swapping rockets from the inventory.")
        .defaultValue(true)
        .visible(firework::get)
        .build()
    );

    public final Setting<Boolean> onlyOne = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("only-one")
        .description("Does not launch another rocket while one is active.")
        .defaultValue(true)
        .visible(firework::get)
        .build()
    );

    private final Setting<Boolean> usingPause = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("using-pause")
        .description("Pauses while using an item.")
        .defaultValue(true)
        .visible(firework::get)
        .build()
    );

    public final Setting<Boolean> autoJump = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Holds the jump key in bounce mode.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Bounce)
        .build()
    );

    public final Setting<Double> upPitch = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("up-pitch")
        .description("Pitch used for upward velocity.")
        .defaultValue(0.0)
        .range(0.0, 90.0)
        .sliderRange(0.0, 90.0)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Double> upFactor = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("up-factor")
        .description("Upward acceleration factor.")
        .defaultValue(1.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Double> downFactor = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("fall-speed")
        .description("Downward acceleration factor.")
        .defaultValue(1.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Double> speed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal speed.")
        .defaultValue(1.0)
        .range(0.1, 10.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Boolean> speedLimit = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("speed-limit")
        .description("Limits horizontal speed.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Double> maxSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum horizontal speed.")
        .defaultValue(2.5)
        .range(0.1, 10.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> speedLimit.get() && mode.get() == Mode.Control)
        .build()
    );

    public final Setting<Boolean> noDrag = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("no-drag")
        .description("Ignores air drag.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Boolean> autoStop = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-stop")
        .description("Stops moving in unloaded chunks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sprint = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("sprint")
        .description("Forces sprinting in bounce mode.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Bounce)
        .build()
    );

    private final Setting<Double> pitch = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Pitch used in bounce mode.")
        .defaultValue(88.0)
        .range(-90.0, 90.0)
        .sliderRange(-90.0, 90.0)
        .visible(() -> mode.get() == Mode.Bounce)
        .build()
    );

    private final Setting<Boolean> instantFly = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-start")
        .description("Starts gliding when falling.")
        .defaultValue(true)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    private final Setting<Boolean> checkSpeed = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("check-speed")
        .description("Only launches rockets below the minimum speed.")
        .defaultValue(false)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    public final Setting<Double> minSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("Minimum speed threshold.")
        .defaultValue(70.0)
        .range(0.1, 200.0)
        .sliderRange(0.1, 200.0)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    private final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("delay")
        .description("Milliseconds between rockets.")
        .defaultValue(1000)
        .range(0, 20000)
        .sliderRange(0, 20000)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    private final Setting<Double> timeout = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("timeout")
        .description("Seconds between auto-start attempts.")
        .defaultValue(0.0)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .visible(() -> mode.get() != Mode.Bounce)
        .build()
    );

    private final Setting<Double> sneakDownSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("down-speed")
        .description("Descent speed while sneaking.")
        .defaultValue(1.0)
        .range(0.1, 10.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> mode.get() == Mode.Control)
        .build()
    );

    private final Setting<Double> boost = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("boost")
        .description("Boost strength.")
        .defaultValue(1.0)
        .range(0.1, 4.0)
        .sliderRange(0.1, 4.0)
        .visible(() -> mode.get() == Mode.Boost)
        .build()
    );

    private final Setting<Boolean> freeze = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("freeze")
        .description("Freezes movement when no keys are pressed.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Rotation)
        .build()
    );

    private final Setting<Boolean> motionStop = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("motion-stop")
        .description("Stops motion when no keys are pressed.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Rotation)
        .build()
    );

    private final Setting<Double> infiniteMaxSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("infinite-max-speed")
        .description("Maximum speed for pitch mode.")
        .defaultValue(150.0)
        .range(50.0, 170.0)
        .sliderRange(50.0, 170.0)
        .visible(() -> mode.get() == Mode.Pitch)
        .build()
    );

    private final Setting<Double> infiniteMinSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("infinite-min-speed")
        .description("Minimum speed for pitch mode.")
        .defaultValue(25.0)
        .range(10.0, 70.0)
        .sliderRange(10.0, 70.0)
        .visible(() -> mode.get() == Mode.Pitch)
        .build()
    );

    private final Setting<Integer> infiniteMaxHeight = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("infinite-max-height")
        .description("Height limit for pitch mode.")
        .defaultValue(200)
        .range(-50, 360)
        .sliderRange(-50, 360)
        .visible(() -> mode.get() == Mode.Pitch)
        .build()
    );

    public final Setting<Boolean> releaseSneak = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("release-sneak")
        .description("Releases sneak on disable.")
        .defaultValue(false)
        .build()
    );

    private final SynTimer fireworkTimer = new SynTimer();
    private final SynTimer instantFlyTimer = new SynTimer();

    private boolean hasElytra = false;
    private float rotationYaw = 0.0f;
    private float rotationPitch = 0.0f;
    private boolean flying = false;
    private int packetDelayInt = 0;
    private boolean down = false;
    private float lastInfinitePitch = 0.0f;
    private float infinitePitch = 0.0f;
    private float previousPitch = 0.0f;
    private boolean prev = false;
    private boolean fireworkPressed = false;

    public ElytraFlyPlus() {
        super(SynAddon.CATEGORY, "ElytraFly+", "Controls elytra flight with several modes.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        hasElytra = false;
        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();
        fireworkTimer.reset();
        instantFlyTimer.reset();
        packetDelayInt = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;
        if (releaseSneak.get()) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    public boolean isPacketEnabled() {
        return packet.get();
    }

    public boolean isFallFlying() {
        return mc.player.isGliding()
            || (packet.get() && hasElytra && !mc.player.isOnGround())
            || flying;
    }

    public static boolean checkConditions(PlayerEntity player) {
        ItemStack stack = player.getEquippedStack(EquipmentSlot.CHEST);
        return !player.getAbilities().flying && !player.hasVehicle() && !player.isClimbing()
            && stack.isOf(Items.ELYTRA) && isElytraUsable(stack);
    }

    private static boolean ignoreGround(PlayerEntity player) {
        if (!player.isTouchingWater() && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)) {
            ItemStack stack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (stack.isOf(Items.ELYTRA) && isElytraUsable(stack)) {
                player.startGliding();
                return true;
            }
        }
        return false;
    }

    private static boolean isElytraUsable(ItemStack stack) {
        return stack.getDamage() < stack.getMaxDamage() - 1;
    }

    public static boolean recastElytra(net.minecraft.client.network.ClientPlayerEntity player) {
        if (checkConditions(player) && ignoreGround(player)) {
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            ElytraFlyPlus fly = meteordevelopment.meteorclient.systems.modules.Modules.get().get(ElytraFlyPlus.class);
            if (fly != null && fly.setFlag.get()) {
                player.startGliding();
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        update();
        tickRotation();
        tickFireworkBind();
        tickBounce();
    }

    private void update() {
        getInfinitePitch();
        flying = false;

        if (packet.get()) {
            hasElytra = SynUtils.findItemInventorySlot(Items.ELYTRA) != -1;
        } else {
            hasElytra = false;
            for (ItemStack stack : new ItemStack[]{
                mc.player.getEquippedStack(EquipmentSlot.HEAD),
                mc.player.getEquippedStack(EquipmentSlot.CHEST),
                mc.player.getEquippedStack(EquipmentSlot.LEGS),
                mc.player.getEquippedStack(EquipmentSlot.FEET),
                mc.player.getMainHandStack(),
                mc.player.getOffHandStack()
            }) {
                if (SynUtils.isElytra(stack)) {
                    hasElytra = true;
                    break;
                }
            }

            if (infiniteDura.get() && !mc.player.isOnGround() && hasElytra) {
                flying = true;
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (setFlag.get()) mc.player.startGliding();
            }

            if (mode.get() == Mode.Bounce) return;
        }

        double x = mc.player.getX() - mc.player.lastX;
        double y = mc.player.getY() - mc.player.lastY;
        double z = mc.player.getZ() - mc.player.lastZ;
        double dist = Math.sqrt(x * x + z * z + y * y) / 1000.0;
        double div = 1.388888888888889E-5;
        double speedValue = dist / div;

        if (mode.get() == Mode.Boost) boost();

        if (packet.get()) {
            if (mc.player.isOnGround()) return;
            ++packetDelayInt;
            if ((double) packetDelayInt <= packetDelay.get()) return;

            int elytra = SynUtils.findItem(Items.ELYTRA);
            if (elytra != -1) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, mc.player);
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                tryFirework(speedValue);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, mc.player);
                packetDelayInt = 0;
            } else {
                elytra = SynUtils.findItemInventorySlot(Items.ELYTRA);
                if (elytra != -1) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    mc.player.startGliding();
                    tryFirework(speedValue);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                    packetDelayInt = 0;
                }
            }
            return;
        }

        tryFirework(speedValue);

        if (!isFallFlying() && hasElytra) {
            fireworkTimer.reset();
            if (!mc.player.isOnGround() && instantFly.get() && mc.player.getVelocity().y < 0.0 && !infiniteDura.get()) {
                if (!instantFlyTimer.passed(1000.0 * timeout.get())) return;
                instantFlyTimer.reset();
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                if (setFlag.get()) mc.player.startGliding();
            }
        }
    }

    private void tryFirework(double speedValue) {
        if ((!checkSpeed.get() || speedValue <= minSpeed.get())
            && firework.get()
            && fireworkTimer.passed(delay.get())
            && (SynUtils.isMoving() || (mode.get() == Mode.Rotation && mc.options.jumpKey.isPressed()))
            && (!mc.player.isUsingItem() || !usingPause.get())
            && isFallFlying()) {
            off();
            fireworkTimer.reset();
        }
    }

    private void boost() {
        if (!hasElytra || !isFallFlying()) return;
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        if (mc.options.forwardKey.isPressed()) {
            mc.player.addVelocity(
                (double) (-MathHelper.sin(yaw) * boost.get() / 10.0f), 0.0,
                (double) (MathHelper.cos(yaw) * boost.get() / 10.0f)
            );
        }
    }

    private void tickBounce() {
        if (mode.get() == Mode.Bounce && hasElytra) {
            if (autoJump.get()) mc.options.jumpKey.setPressed(true);
            if (!isFallFlying()) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            if (checkConditions(mc.player) && !sprint.get()) {
                mc.player.setSprinting(!isFallFlying() || mc.player.isOnGround());
            } else if (checkConditions(mc.player) && sprint.get()) {
                mc.player.setSprinting(true);
            }
        }
    }

    private void tickRotation() {
        if (mode.get() == Mode.Rotation && isFallFlying()) {
            if (SynUtils.isMoving()) {
                if (mc.options.jumpKey.isPressed()) {
                    rotationPitch = -45.0f;
                } else if (mc.options.sneakKey.isPressed()) {
                    rotationPitch = 45.0f;
                } else {
                    rotationPitch = -1.9f;
                    if (motionStop.get()) mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
                }
            } else if (mc.options.jumpKey.isPressed()) {
                rotationPitch = -89.0f;
            } else if (mc.options.sneakKey.isPressed()) {
                rotationPitch = 89.0f;
            } else if (motionStop.get()) {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
            }

            if (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) {
                rotationYaw = getSprintYaw(mc.player.getYaw());
            } else if (motionStop.get()) {
                mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
            }
            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);
        } else if (mode.get() == Mode.Pitch && isFallFlying()) {
            mc.player.setPitch(infinitePitch);
        } else if (mode.get() == Mode.Bounce && isFallFlying()) {
            mc.player.setPitch((float) (double) pitch.get());
        }
    }

    private void tickFireworkBind() {
        if (firework.get() && inventory.get() && !SynUtils.inInventory()) return;
        if (mc.currentScreen == null) {
            if (fireWork.get().isPressed()) {
                if (!(fireworkPressed || !fireworkTimer.passed(delay.get())
                    || (mc.player.isUsingItem() && usingPause.get()) || !isFallFlying())) {
                    off();
                    fireworkTimer.reset();
                }
                fireworkPressed = true;
            } else {
                fireworkPressed = false;
            }
        } else {
            fireworkPressed = false;
        }
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Movement —— 复刻 deepseekclient 的 TravelEvent 逻辑
    // ------------------------------------------------------------------

    /**
     * 由 LivingEntityTravelMixin 在 travel() 入口调用。
     *
     * @return true 表示已接管本 tick 的移动，mixin 会 cancel 原版 travel()
     */
    public static boolean handleTravel(LivingEntity entity) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || entity != client.player) return false;
            if (Modules.get() == null) return false;
            ElytraFlyPlus module = Modules.get().get(ElytraFlyPlus.class);
            if (module == null || !module.isActive()) return false;
            return module.travelControl(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean travelControl(LivingEntity player) {
        if (!hasElytra || !isFallFlying()) return false;

        // 区块未加载时停住（对应永久端的 autoStop）
        if (autoStop.get()) {
            int chunkX = (int) (player.getX() / 16.0);
            int chunkZ = (int) (player.getZ() / 16.0);
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                player.setVelocity(Vec3d.ZERO);
                player.move(MovementType.SELF, Vec3d.ZERO);
                return true;
            }
        }

        PlayerInput input = mc.player.input.playerInput;
        boolean jumping = input.jump();
        boolean sneaking = input.sneak();

        // 冻结模式
        if ((mode.get() == Mode.Freeze || (mode.get() == Mode.Rotation && freeze.get()))
            && !SynUtils.isMoving() && !jumping && !sneaking) {
            player.setVelocity(Vec3d.ZERO);
            player.move(MovementType.SELF, Vec3d.ZERO);
            return true;
        }

        if (mode.get() != Mode.Control) return false;

        Vec3d velocity = player.getVelocity();
        double motionX = velocity.x;
        double motionY = velocity.y;
        double motionZ = velocity.z;

        Vec3d lookVec = getRotationVec(0.0f);
        double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
        double motionDist = Math.sqrt(motionX * motionX + motionZ * motionZ);

        if (sneaking) {
            motionY = -sneakDownSpeed.get();
        } else if (!jumping) {
            // 平飞关键：垂直速度几乎为 0
            motionY = -3.0E-11 * downFactor.get();
        }

        if (jumping) {
            // deepseek 用 upFactor 的上限/10 作为阈值（上限 10 → 常量 1.0）
            if (motionDist > 1.0) {
                double rawUpSpeed = motionDist * 0.01325;
                motionY += rawUpSpeed * 3.2;
                if (lookDist > 0.0) {
                    motionX -= lookVec.x * rawUpSpeed / lookDist;
                    motionZ -= lookVec.z * rawUpSpeed / lookDist;
                }
            } else {
                double[] dir = SynUtils.directionSpeed(speed.get());
                motionX = dir[0];
                motionZ = dir[1];
            }
        }

        if (lookDist > 0.0) {
            motionX += (lookVec.x / lookDist * motionDist - motionX) * 0.1;
            motionZ += (lookVec.z / lookDist * motionDist - motionZ) * 0.1;
        }

        if (!jumping) {
            double[] dir = SynUtils.directionSpeed(speed.get());
            motionX = dir[0];
            motionZ = dir[1];
        }

        if (!noDrag.get()) {
            motionY *= 0.99;
            motionX *= 0.98;
            motionZ *= 0.99;
        }

        double finalDist = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (speedLimit.get() && finalDist > maxSpeed.get()) {
            motionX *= maxSpeed.get() / finalDist;
            motionZ *= maxSpeed.get() / finalDist;
        }

        player.setVelocity(motionX, motionY, motionZ);
        // 原版 travel() 已被取消，这里必须自己应用位移
        player.move(MovementType.SELF, player.getVelocity());
        return true;
    }

    // ------------------------------------------------------------------
    // Fireworks
    // ------------------------------------------------------------------

    public void off() {
        if (inventory.get() && !SynUtils.inInventory()) return;
        if (onlyOne.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof FireworkRocketEntity rocket && rocket.getOwner() == mc.player) return;
            }
        }

        fireworkTimer.reset();
        Hand hand = Hand.MAIN_HAND;
        if (mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
            useFirework(hand);
        } else if (inventory.get() && SynUtils.findItemInventorySlot(Items.FIREWORK_ROCKET) != -1) {
            int slot = SynUtils.findItemInventorySlot(Items.FIREWORK_ROCKET);
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
            useFirework(hand);
            SynUtils.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
            SynUtils.syncInventory();
        } else {
            int slot = SynUtils.findItem(Items.FIREWORK_ROCKET);
            if (slot != -1) {
                int old = mc.player.getInventory().getSelectedSlot();
                SynUtils.switchToSlot(slot);
                useFirework(hand);
                SynUtils.switchToSlot(old);
            }
        }
    }

    private void useFirework(Hand hand) {
        if (packetInteract.get()) {
            mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(hand, 0, mc.player.getYaw(), mc.player.getPitch()));
        } else {
            mc.interactionManager.interactItem(mc.player, hand);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (mode.get() == Mode.Bounce && hasElytra) {
            mc.options.jumpKey.setPressed(true);
        }
    }

    private void getInfinitePitch() {
        lastInfinitePitch = infinitePitch;
        double currentPlayerSpeed = Math.hypot(mc.player.getX() - mc.player.lastX, mc.player.getZ() - mc.player.lastZ);
        if (mc.player.getY() < infiniteMaxHeight.get()) {
            if (currentPlayerSpeed * 72.0 < infiniteMinSpeed.get() && !down) down = true;
            if (currentPlayerSpeed * 72.0 > infiniteMaxSpeed.get() && down) down = false;
        } else {
            down = true;
        }
        infinitePitch = down ? infinitePitch + 3.0f : infinitePitch - 3.0f;
        infinitePitch = MathHelper.clamp(infinitePitch, -40.0f, 40.0f);
    }

    private Vec3d getRotationVec(float tickDelta) {
        float f = (float) (-upPitch.get()) * ((float) Math.PI / 180);
        float g = -mc.player.getYaw() * ((float) Math.PI / 180);
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d((double) (i * j), (double) (-k), (double) (h * j));
    }

    private static float getSprintYaw(float yaw) {
        var input = MinecraftClientHolder.input();
        if (input == null) return yaw;
        if (input.forward() && input.left()) return yaw + 45.0f;
        if (input.forward() && input.right()) return yaw - 45.0f;
        if (input.backward() && input.left()) return yaw + 135.0f;
        if (input.backward() && input.right()) return yaw - 135.0f;
        if (input.left()) return yaw + 90.0f;
        if (input.right()) return yaw - 90.0f;
        if (input.backward()) return yaw + 180.0f;
        return yaw;
    }

    private static final class MinecraftClientHolder {
        private static PlayerInput input() {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            return mc.player == null ? null : mc.player.input.playerInput;
        }
    }

    public enum Mode {
        Control,
        Boost,
        Bounce,
        Freeze,
        None,
        Rotation,
        Pitch
    }
}
