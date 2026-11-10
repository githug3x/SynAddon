package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 逐行复刻 deepseekclient 的 FakePlayer 模块。
 *
 * 与原版的对应关系：
 * 1. 攻击触发：原版拦截 PlayerInteractEntityC2SPacket（type == ATTACK）且目标为假人。
 *    这里使用 Meteor 在 ClientPlayerInteractionManager#attackEntity HEAD 真实发布的
 *    AttackEntityEvent —— 该处正是生成 ATTACK 包的唯一入口，语义完全等价。
 * 2. 伤害结算：hurtTime <= 0 作为无敌帧闸门 → 先扣伤害吸收（absorption）→ 再扣血量。
 * 3. 死亡：消耗图腾（等价原版私有方法 tryUseDeathProtector）→ setHealth(10)
 *    → 派发 EntityStatusS2CPacket(status = 35) 触发图腾弹跳动画与音效。
 * 4. 爆炸：ExplosionS2CPacket，10 格内，DamageUtils.explosionDamage(..., power = 6.0f)。
 */
public class FakePlayerPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("The name of the fake player.")
        .defaultValue("IDhammaI")
        .build()
    );

    private final Setting<Boolean> damage = sgGeneral.add(new BoolSetting.Builder()
        .name("damage")
        .description("Takes explosion damage from nearby explosion packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-totem")
        .description("Keeps a totem in the fake player's hands so it pops on lethal damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> record = sgGeneral.add(new BoolSetting.Builder()
        .name("record")
        .description("Records your movement for later playback.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> play = sgGeneral.add(new BoolSetting.Builder()
        .name("play")
        .description("Replays the recorded movement on the fake player.")
        .defaultValue(false)
        .build()
    );

    private FakePlayerEntity fakePlayer;
    private final List<PlayerState> positions = new ArrayList<>();
    private int movementTick;
    private boolean lastRecordValue;

    public FakePlayerPlus() {
        super(SynAddon.CATEGORY, "FakePlayer+", "Spawns a client-side fake player that reacts to your attacks and explosions.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }

        despawn();
        positions.clear();
        movementTick = 0;
        lastRecordValue = false;

        FakePlayerManager.add(name.get(), 20.0f, true);
        fakePlayer = FakePlayerManager.get(name.get());

        if (fakePlayer == null) disable();
    }

    @Override
    public void onDeactivate() {
        despawn();
        positions.clear();
        movementTick = 0;
        lastRecordValue = false;
    }

    private void despawn() {
        if (fakePlayer != null) {
            FakePlayerManager.remove(fakePlayer);
            fakePlayer = null;
        }
    }

    @Override
    public String getInfoString() {
        return name.get();
    }

    @EventHandler
    private void onUpdate(TickEvent.Post event) {
        if (fakePlayer == null || fakePlayer.getEntityWorld() != mc.world) {
            disable();
            return;
        }

        if (autoTotem.get()) {
            if (fakePlayer.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (fakePlayer.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        if (record.get() && !lastRecordValue) {
            positions.clear();
        }
        lastRecordValue = record.get();

        if (record.get()) {
            positions.add(new PlayerState(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch()
            ));
        }

        if (play.get() && !positions.isEmpty()) {
            movementTick++;
            if (movementTick >= positions.size()) movementTick = 0;

            PlayerState p = positions.get(movementTick);
            fakePlayer.setYaw(p.yaw);
            fakePlayer.setPitch(p.pitch);
            fakePlayer.setHeadYaw(p.yaw);
            fakePlayer.updateTrackedPosition(p.x, p.y, p.z);
            fakePlayer.refreshPositionAndAngles(p.x, p.y, p.z, p.yaw, p.pitch);
        }
    }

    // ------------------------------------------------------------------
    // 攻击伤害（对应原版 ATTACK 包拦截）
    // ------------------------------------------------------------------
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (fakePlayer == null) return;
        if (event.entity != fakePlayer) return;

        mc.world.playSound(
            mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
            SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f
        );

        boolean crit = mc.player.fallDistance > 0.0f
            && !mc.player.isOnGround()
            && !mc.player.isClimbing()
            && !mc.player.isTouchingWater()
            && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
            && !mc.player.hasVehicle();

        if (crit) {
            mc.world.playSound(
                mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.0f
            );
            mc.player.addCritParticles(fakePlayer);
        }

        applyDamage(DamageUtils.getAttackDamage(mc.player, fakePlayer));
    }

    // ------------------------------------------------------------------
    // 爆炸伤害（对应原版 PacketEvent.Receive 分支）
    // ------------------------------------------------------------------
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!damage.get() || fakePlayer == null) return;
        if (!(event.packet instanceof ExplosionS2CPacket explosion)) return;

        Vec3d pos = explosion.center();
        if (Math.sqrt(pos.squaredDistanceTo(fakePlayer.getEntityPos())) > 10.0) return;

        float dmg;
        if (mc.world.getBlockState(BlockPos.ofFloored(pos)).isOf(Blocks.RESPAWN_ANCHOR)) {
            // 原版：爆炸点是重生锚时改用 AutoAnchor 的伤害计算
            dmg = DamageUtils.anchorDamage(fakePlayer, pos);
        } else {
            dmg = DamageUtils.explosionDamage(
                fakePlayer,
                fakePlayer.getEntityPos(),
                fakePlayer.getBoundingBox(),
                pos,
                6.0f,
                DamageUtils.HIT_FACTORY
            );
        }

        applyDamage(dmg);
    }

    // ------------------------------------------------------------------
    // 伤害结算（与原版一致：无敌帧 → 吸收 → 血量 → 图腾）
    // ------------------------------------------------------------------
    private void applyDamage(float dmg) {
        if (fakePlayer == null || mc.world == null) return;

        // 原版：if (fakePlayer.hurtTime <= 0)
        if (fakePlayer.hurtTime > 0) return;

        DamageSource source = mc.world.getDamageSources().generic();

        // 等价于原版 fakePlayer.onDamaged(source)：设置受击闪烁
        fakePlayer.hurtTime = 10;
        fakePlayer.maxHurtTime = 10;

        if (fakePlayer.getAbsorptionAmount() >= dmg) {
            fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - dmg);
        } else {
            float rest = dmg - fakePlayer.getAbsorptionAmount();
            fakePlayer.setAbsorptionAmount(0.0f);
            fakePlayer.setHealth(fakePlayer.getHealth() - rest);
        }

        if (fakePlayer.isDead()) {
            // 等价于原版 tryUseDeathProtector(source)
            ItemStack offhand = fakePlayer.getStackInHand(Hand.OFF_HAND);
            ItemStack totem = offhand.isOf(Items.TOTEM_OF_UNDYING) ? offhand : fakePlayer.getMainHandStack();

            if (totem.isOf(Items.TOTEM_OF_UNDYING)) {
                totem.decrement(1);
                fakePlayer.setHealth(10.0f);
                fakePlayer.setAbsorptionAmount(5.0f);
                new EntityStatusS2CPacket(fakePlayer, (byte) 35).apply(mc.getNetworkHandler());
            }
        }
    }

    public static final class PlayerState {
        public final double x, y, z;
        public final float yaw, pitch;

        public PlayerState(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
