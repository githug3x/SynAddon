package com.syn.addon.utils;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class SynUtils {
    private SynUtils() {}

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ------------------------------------------------------------------
    // Player / movement helpers (ported from EntityUtil & MovementUtil)
    // ------------------------------------------------------------------

    /**
     * Mirrors the original client's "inInventory" check: the player is not
     * blocked by an unrelated container screen.
     */
    public static boolean inInventory() {
        var screen = mc().currentScreen;
        return screen == null
            || screen instanceof GameOptionsScreen
            || screen instanceof OptionsScreen
            || screen instanceof ChatScreen
            || screen instanceof InventoryScreen
            || screen instanceof GameMenuScreen;
    }

    public static boolean isMoving() {
        var input = mc().player.input.playerInput;
        return input.forward() || input.backward() || input.left() || input.right();
    }

    public static boolean isJumping() {
        return mc().player.input.playerInput.jump();
    }

    public static double getDistance2D() {
        var p = mc().player;
        double xDist = p.getX() - p.lastX;
        double zDist = p.getZ() - p.lastZ;
        return Math.sqrt(xDist * xDist + zDist * zDist);
    }

    public static float getHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getHealth() + living.getAbsorptionAmount();
        }
        return 0.0f;
    }

    public static boolean isInsideBlock(PlayerEntity player) {
        return player.getEntityWorld().getBlockCollisions(player, player.getBoundingBox()).iterator().hasNext();
    }

    public static int getDamagePercent(ItemStack stack) {
        if (stack.getDamage() == stack.getMaxDamage()) return 100;
        return (int) ((double) (stack.getMaxDamage() - stack.getDamage()) / Math.max(0.1, (double) stack.getMaxDamage()) * 100.0);
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3d testVec = pos.toCenterPos().add(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5);
        BlockHitResult result = mc().world.raycast(new RaycastContext(
            mc().player.getEyePos(), testVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc().player
        ));
        return result == null || result.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    public static double getSpeed(boolean slowness) {
        return getSpeed(slowness, 0.2873);
    }

    public static double getSpeed(boolean slowness, double defaultSpeed) {
        if (mc().player.hasStatusEffect(StatusEffects.SPEED)) {
            int amplifier = mc().player.getActiveStatusEffects().get(StatusEffects.SPEED).getAmplifier();
            defaultSpeed *= 1.0 + 0.2 * (double) (amplifier + 1);
        }
        if (slowness && mc().player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            int amplifier = mc().player.getActiveStatusEffects().get(StatusEffects.SLOWNESS).getAmplifier();
            defaultSpeed /= 1.0 + 0.2 * (double) (amplifier + 1);
        }
        if (mc().player.isSneaking()) defaultSpeed /= 5.0;
        return defaultSpeed;
    }

    public static double[] directionSpeed(double speed) {
        var input = mc().player.input.playerInput;
        float forward = input.forward() ? 1.0f : (input.backward() ? -1.0f : 0.0f);
        float side = input.left() ? 1.0f : (input.right() ? -1.0f : 0.0f);
        float yaw = mc().player.lastYaw + (mc().player.getYaw() - mc().player.lastYaw) * mc().getRenderTickCounter().getTickProgress(true);
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += (float) (forward > 0.0f ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += (float) (forward > 0.0f ? 45 : -45);
            }
            side = 0.0f;
        }
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double posX = (double) forward * speed * cos + (double) side * speed * sin;
        double posZ = (double) forward * speed * sin - (double) side * speed * cos;
        return new double[]{posX, posZ};
    }

    // ------------------------------------------------------------------
    // Inventory helpers (ported from InventoryUtil)
    // ------------------------------------------------------------------

    public static void syncInventory() {
        // The original client only closes the screen when its AntiCheat
        // "closeScreen" option is enabled; that option defaults to off.
    }

    public static void closeScreen() {
        if (mc().player == null) return;
        mc().player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc().player.currentScreenHandler.syncId));
    }

    public static void swingHand(Hand hand, SwingSide side) {
        switch (side) {
            case All -> mc().player.swingHand(hand);
            case Client -> mc().player.swingHand(hand, false);
            case Server -> mc().player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
        }
    }

    public static void selectHotbar(int slot) {
        switchToSlot(slot);
    }

    public static void switchToSlot(int slot) {
        PlayerInventory inv = mc().player.getInventory();
        inv.setSelectedSlot(slot);
        mc().player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static int lastSlot = -1;
    private static int lastSelect = -1;

    /**
     * @param slot the screen-handler slot index (see findItemInventorySlot)
     * @param selectedSlot the currently selected hotbar slot (0-8)
     */
    public static void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
            return;
        }
        if (slot - 36 == selectedSlot) return;
        if (!inInventory()) return;

        mc().interactionManager.clickSlot(
            mc().player.currentScreenHandler.syncId, slot, selectedSlot, SlotActionType.SWAP, mc().player
        );
        mc().player.getInventory().updateItems();
    }

    public static int findItem(Item input) {
        for (int i = 0; i < 9; ++i) {
            if (mc().player.getInventory().getStack(i).getItem() == input) return i;
        }
        return -1;
    }

    public static int getFood() {
        for (int i = 0; i < 9; ++i) {
            if (mc().player.getInventory().getStack(i).get(DataComponentTypes.FOOD) != null) return i;
        }
        return -1;
    }

    public static int getItemCount(Item item) {
        int count = 0;
        for (int i = 0; i <= 35; ++i) {
            if (mc().player.getInventory().getStack(i).getItem() == item) {
                count += mc().player.getInventory().getStack(i).getCount();
            }
        }
        if (mc().player.getOffHandStack().getItem() == item) count += mc().player.getOffHandStack().getCount();
        return count;
    }

    public static int findClass(Class<?> clazz) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (clazz.isInstance(stack.getItem())) return i;
            if (stack.getItem() instanceof BlockItem bi && clazz.isInstance(bi.getBlock())) return i;
        }
        return -1;
    }

    public static int findBlock(Block blockIn) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi) || bi.getBlock() != blockIn) continue;
            return i;
        }
        return -1;
    }

    public static int findUnBlock() {
        for (int i = 0; i < 9; ++i) {
            if (mc().player.getInventory().getStack(i).getItem() instanceof BlockItem) continue;
            return i;
        }
        return -1;
    }

    public static int findBlock() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            if (block == net.minecraft.block.Blocks.COBWEB) continue;
            return i;
        }
        return -1;
    }

    public static int findBlockInventorySlot(Block block) {
        return findItemInventorySlot(block.asItem());
    }

    public static int findItemInventorySlot(Item item) {
        for (int i = 35; i >= 0; --i) {
            if (mc().player.getInventory().getStack(i).getItem() == item) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findItemInventorySlotFromZero(Item item) {
        for (int i = 0; i < 36; ++i) {
            if (mc().player.getInventory().getStack(i).getItem() == item) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findClassInventorySlot(Class<?> clazz) {
        for (int i = 35; i >= 0; --i) {
            ItemStack stack = mc().player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (clazz.isInstance(stack.getItem())) return i < 9 ? i + 36 : i;
            if (stack.getItem() instanceof BlockItem bi && clazz.isInstance(bi.getBlock())) return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Item / component helpers
    // ------------------------------------------------------------------

    public static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null) return 0;
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            if (entry.matchesKey(key)) return component.getLevel(entry);
        }
        return 0;
    }

    public static EquipmentSlot armorSlot(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return null;
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.BODY ? EquipmentSlot.CHEST : slot;
    }

    public static boolean isElytra(ItemStack stack) {
        return stack.getItem() == Items.ELYTRA || stack.get(DataComponentTypes.GLIDER) != null;
    }

    public static boolean isArmor(ItemStack stack) {
        EquipmentSlot slot = armorSlot(stack);
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    /** Base armor points provided by the stack's attribute modifiers. */
    public static double armorPoints(ItemStack stack) {
        AttributeModifiersComponent component = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (component == null) return 0;
        double points = 0;
        for (AttributeModifiersComponent.Entry entry : component.modifiers()) {
            if (entry.attribute().value() == EntityAttributes.ARMOR.value()) {
                points += entry.modifier().value();
            }
        }
        return points;
    }

    public enum SwingSide { All, Client, Server }

    // ------------------------------------------------------------------
    // Combat / world helpers used by the crystal modules
    // ------------------------------------------------------------------

    public static java.util.List<net.minecraft.entity.player.PlayerEntity> getEnemies(double range) {
        java.util.List<net.minecraft.entity.player.PlayerEntity> enemies = new java.util.ArrayList<>();
        if (mc().world == null || mc().player == null) return enemies;
        for (var player : mc().world.getPlayers()) {
            if (player == mc().player || !player.isAlive()) continue;
            if (player.distanceTo(mc().player) > range) continue;
            enemies.add(player);
        }
        return enemies;
    }

    public static java.util.List<BlockPos> getSphere(double range) {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        if (mc().player == null) return positions;
        int px = MathHelper.floor(mc().player.getX());
        int py = MathHelper.floor(mc().player.getY());
        int pz = MathHelper.floor(mc().player.getZ());
        int r = (int) Math.ceil(range);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > range * range) continue;
                    positions.add(new BlockPos(px + x, py + y, pz + z));
                }
            }
        }
        return positions;
    }

    public static java.util.List<Entity> getEntities(Box box) {
        return mc().world == null ? java.util.List.of() : mc().world.getOtherEntities(null, box);
    }

    public static java.util.List<EndCrystalEntity> getEndCrystals(Box box) {
        java.util.List<EndCrystalEntity> crystals = new java.util.ArrayList<>();
        for (Entity entity : getEntities(box)) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) crystals.add(crystal);
        }
        return crystals;
    }

    public static boolean isClickable(Block block) {
        return block instanceof net.minecraft.block.CraftingTableBlock
            || block instanceof net.minecraft.block.AnvilBlock
            || block instanceof net.minecraft.block.LoomBlock
            || block instanceof net.minecraft.block.CartographyTableBlock
            || block instanceof net.minecraft.block.GrindstoneBlock
            || block instanceof net.minecraft.block.StonecutterBlock
            || block instanceof net.minecraft.block.ButtonBlock
            || block instanceof net.minecraft.block.AbstractPressurePlateBlock
            || block instanceof net.minecraft.block.BlockWithEntity
            || block instanceof net.minecraft.block.BedBlock
            || block instanceof net.minecraft.block.FenceGateBlock
            || block instanceof net.minecraft.block.DoorBlock
            || block instanceof net.minecraft.block.NoteBlock
            || block instanceof net.minecraft.block.TrapdoorBlock;
    }

    public static Direction getClickSide(BlockPos pos) {
        return getClickSide(pos, false);
    }

    public static Direction getClickSideStrict(BlockPos pos) {
        return getClickSide(pos, true);
    }

    private static Direction getClickSide(BlockPos pos, boolean strict) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            var state = mc().world.getBlockState(neighbor);
            if (state.isReplaceable()) continue;
            if (state.getBlock() == net.minecraft.block.Blocks.COBWEB && strict) continue;
            if (!mc().player.isSneaking() && isClickable(state.getBlock())) continue;
            if (!state.isSideSolidFullSquare(mc().world, neighbor, direction.getOpposite())) continue;
            double distance = mc().player.getEyePos().squaredDistanceTo(
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

    /** Predicted position used by the crystal planner. */
    public static Vec3d predictPos(LivingEntity entity, int ticks, double maxMotionY) {
        Vec3d velocity = entity.getVelocity();
        double vy = MathHelper.clamp(velocity.y, -maxMotionY, maxMotionY);
        return entity.getEntityPos().add(velocity.x * ticks, vy * ticks, velocity.z * ticks);
    }

    /**
     * Approximates vanilla explosion damage at a predicted position.
     */
    public static float crystalDamageAt(LivingEntity target, Vec3d targetPos, Vec3d crystalPos) {
        if (target == null || target.isDead() || target.getHealth() <= 0) return 0;

        double radius = 12.0;
        double distance = targetPos.distanceTo(crystalPos);
        if (distance > radius) return 0;

        Box box = target.getBoundingBox().offset(targetPos.subtract(target.getEntityPos()));
        double exposure = getExposure(crystalPos, box);
        double impact = (1.0 - distance / radius) * exposure;
        float damage = (float) (((impact * impact + impact) / 2.0) * 7.0 * radius + 1.0);

        double armor = target.getAttributeValue(EntityAttributes.ARMOR);
        double toughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        damage = (float) (damage * (1.0 - Math.min(20.0, Math.max(armor / 5.0, armor - damage / (2.0 + toughness / 4.0))) / 25.0));

        var resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage = (float) (damage * (1.0 - 0.2 * (double) (resistance.getAmplifier() + 1)));
        }

        int protection = 0;
        if (target instanceof PlayerEntity player) {
            for (ItemStack stack : new ItemStack[]{
                player.getEquippedStack(EquipmentSlot.HEAD),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.FEET)
            }) {
                protection += enchantLevel(stack, Enchantments.PROTECTION);
                protection += enchantLevel(stack, Enchantments.BLAST_PROTECTION) * 2;
            }
        }
        damage = (float) (damage * (1.0 - Math.min(20.0, protection) / 25.0));

        return Math.max(0.0f, damage);
    }

    private static double getExposure(Vec3d source, Box box) {
        // 暴露度 = 可见采样点比例（原实现把"被遮挡比例"当暴露度用，空旷环境恒为 0，
        // 导致伤害计算恒为 0、模块永远达不到 minDamage 而无法攻击）
        int visible = 0;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double x = box.minX + (box.maxX - box.minX) * (double) i / 2.0;
                double y = box.minY + (box.maxY - box.minY) * 0.5;
                double z = box.minZ + (box.maxZ - box.minZ) * (double) j / 2.0;
                if (isExposed(new Vec3d(x, y, z), source)) visible++;
                count++;
            }
        }
        return count == 0 ? 0.0 : (double) visible / (double) count;
    }

    private static boolean isExposed(Vec3d boxPoint, Vec3d source) {
        var result = mc().world.raycast(new RaycastContext(
            boxPoint, source, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc().player
        ));
        return result == null || result.getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    public static Vec3d getPointToBoxFromBottom(Vec3d eye, Vec3d center, double range, double height, double step) {
        if (eye.distanceTo(center) > range) return null;

        // 纯几何实现（原实现在 box 上以 0.1 步长逐层做 world.raycast，
        // 每个候选位置要 ~189 次射线检测，place 球体上千个位置导致严重卡顿）。
        // 这里只采样 box 表面的 18 个点，可见性由调用方的 canSee/wallRange 检查兜底。
        double minX = center.x - 0.5, maxX = center.x + 0.5;
        double minZ = center.z - 0.5, maxZ = center.z + 0.5;

        Vec3d best = null;
        double bestDistance = Double.MAX_VALUE;
        double[] ys = {center.y, center.y + height};
        for (double y : ys) {
            for (double x : new double[]{minX, center.x, maxX}) {
                for (double z : new double[]{minZ, center.z, maxZ}) {
                    Vec3d point = new Vec3d(x, y, z);
                    double distance = eye.distanceTo(point);
                    if (distance <= range && distance < bestDistance) {
                        best = point;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public static double animate(double current, double target, double delta) {
        return current + (target - current) * delta;
    }
}
