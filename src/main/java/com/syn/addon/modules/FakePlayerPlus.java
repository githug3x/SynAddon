package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of deepseekclient's FakePlayer module. Spawns a local FakePlayer
 * entity (using Meteor Client's FakePlayerEntity API) that mimics a real
 * player: takes damage from your attacks, pops totems, and can have its
 * trajectory recorded & replayed.
 *
 * Damage / explosion formulas are simplified versions of vanilla logic so
 * you can test PvP combos, totem timing, and explosion deflection locally.
 */
public class FakePlayerPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("Name for the dummy.")
        .defaultValue("SynBot")
        .build()
    );

    private final Setting<Boolean> damageSim = sgGeneral.add(new BoolSetting.Builder()
        .name("damage-sim")
        .description("Simulates damage on attack packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> explosionSim = sgGeneral.add(new BoolSetting.Builder()
        .name("explosion-sim")
        .description("Takes damage from nearby explosion packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-totem")
        .description("Gives the dummy a totem and respawns on death.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> explosionRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("explosion-radius")
        .description("Max distance from explosion source that still damages the dummy.")
        .defaultValue(10.0)
        .min(1.0).max(20.0).sliderRange(1.0, 20.0)
        .visible(explosionSim::get)
        .build()
    );

    private final Setting<Boolean> copyInv = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-inventory")
        .description("Copies the player's inventory onto the dummy on spawn.")
        .defaultValue(true)
        .build()
    );

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private FakePlayerEntity dummy;
    private final List<TickSample> recordBuffer = new ArrayList<>();
    private boolean recording;
    private boolean playing;
    private int playIndex;

    public FakePlayerPlus() {
        super(SynAddon.CATEGORY, "FakePlayer+", "Spawns a dummy player for offline sparring practice.");
    }

    @Override
    public void onActivate() {
        spawn();
    }

    @Override
    public void onDeactivate() {
        despawn();
        recordBuffer.clear();
        recording = false;
        playing = false;
        playIndex = 0;
    }

    @Override
    public String getInfoString() {
        return name.get();
    }

    // ------------------------------------------------------------------
    // Spawn / despawn
    // ------------------------------------------------------------------
    private void spawn() {
        if (mc.player == null) { disable(); return; }
        despawn();
        String n = name.get();
        FakePlayerManager.add(n, 20.0f, copyInv.get());
        dummy = FakePlayerManager.get(n);
        if (dummy == null) { disable(); return; }

        // Give the dummy a totem in the off-hand so AutoTotem pops look real
        if (autoTotem.get()) {
            dummy.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    private void despawn() {
        if (dummy != null) {
            FakePlayerManager.remove(dummy);
            dummy = null;
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Respawn if dummy disappeared or died
        if (dummy == null || !FakePlayerManager.contains(dummy)) {
            spawn();
            return;
        }

        // Respawn on death
        if (dummy.getHealth() <= 0.0f) {
            if (autoTotem.get()) {
                despawn();
                spawn();
                info("FakePlayer respawned.");
            }
            return;
        }

        // Keep off-hand stocked with totem
        if (autoTotem.get() && dummy.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            dummy.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }

        // Record player trajectory
        if (recording) {
            recordBuffer.add(new TickSample(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch()
            ));
        }

        // Play recorded trajectory on the dummy
        if (playing && !recordBuffer.isEmpty()) {
            playIndex++;
            if (playIndex >= recordBuffer.size()) playIndex = 0;
            TickSample s = recordBuffer.get(playIndex);
            dummy.refreshPositionAndAngles(s.x, s.y, s.z, s.yaw, s.pitch);
        }
    }

    // ------------------------------------------------------------------
    // Damage simulation — attacks on the dummy
    // ------------------------------------------------------------------
    /**
     * 使用 Meteor 的 AttackEntityEvent（在 ClientPlayerInteractionManager#attackEntity
     * 处触发），比反射解析攻击包可靠。原实现反射读取包字段在 Yarn 映射下失败且被
     * 静默吞掉，导致假人永远"打不了"。
     */
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!damageSim.get() || dummy == null) return;
        if (event.entity != dummy) return;
        applyAttackDamage();
    }

    private void applyAttackDamage() {
        if (dummy == null || mc.player == null || mc.world == null) return;

        // deepseek 原版行为：命中时播放攻击音效
        mc.world.playSoundFromEntity(
            null, dummy, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.0f
        );

        float damage = DamageUtils.getAttackDamage(mc.player, dummy);
        if (damage <= 0.0f) return;

        // 模拟受击无敌帧：hurtTime > 0 时伤害减半（最少 1 点）
        if (dummy.hurtTime > 0) damage = Math.max(1.0f, damage * 0.5f);

        takeDamage(damage);
    }

    /**
     * Applies damage to the dummy, respecting totem-popping.
     */
    public void takeDamage(double amount) {
        if (dummy == null) return;

        // Totem pop — off-hand totem takes precedence
        boolean totemPopped = false;
        if (autoTotem.get() && dummy.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (dummy.getHealth() - (float) amount <= 0.0f) {
                dummy.getOffHandStack().decrement(1);
                totemPopped = true;
            }
        }
        if (!totemPopped) {
            for (int slot = 0; slot < 46; slot++) {
                ItemStack stack = dummy.getInventory().getStack(slot);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING
                    && dummy.getHealth() - (float) amount <= 0.0f) {
                    stack.decrement(1);
                    totemPopped = true;
                    break;
                }
            }
        }

        if (totemPopped) {
            dummy.setHealth(10.0f); // totem pop gives back 5 HP + 5 absorption
            dummy.setAbsorptionAmount(5.0f);
            return;
        }

        float newHealth = Math.max(0.0f, dummy.getHealth() - (float) amount);
        dummy.setHealth(newHealth);

        // Simple knockback
        if (mc.player != null) {
            double kx = dummy.getX() - mc.player.getX();
            double kz = dummy.getZ() - mc.player.getZ();
            double kMag = Math.sqrt(kx * kx + kz * kz) + 0.001;
            dummy.addVelocity(
                (kx / kMag) * 0.4,
                0.3,
                (kz / kMag) * 0.4
            );
        }
    }

    // ------------------------------------------------------------------
    // Explosion damage simulation
    // ------------------------------------------------------------------
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!explosionSim.get() || dummy == null) return;
        if (!(event.packet instanceof ExplosionS2CPacket packet)) return;

        // 1.21.11 的 ExplosionS2CPacket 是 record：center() 为爆炸位置，radius() 为当量。
        // 原实现反射读取 x/y/z/power 字段在 Yarn 映射下失败且被静默吞掉。
        Vec3d center = packet.center();
        Vec3d dummyPos = dummy.getEntityPos();
        double dist = center.distanceTo(dummyPos);
        if (dist > explosionRadius.get()) return;

        float radius = packet.radius();
        if (radius <= 0.0f) radius = 6.0f; // 结晶爆炸当量（deepseek 原版固定 6.0f）

        // 用 Meteor 的完整结晶伤害计算（含护甲/保护附魔/方块遮挡，固定 6.0 当量）
        float dmg = DamageUtils.crystalDamage(dummy, center);
        if (dmg <= 0.0f && radius > 0.0f) {
            // 非结晶当量的爆炸（TNT 等）退化为线性衰减近似
            double falloff = Math.max(0.1, 1.0 - dist / explosionRadius.get());
            dmg = (float) (radius * 2.0 * falloff);
        }
        if (dmg > 0.0f) takeDamage(dmg);
    }

    // ------------------------------------------------------------------
    // Recording / playback
    // ------------------------------------------------------------------
    public void startRecording() {
        if (dummy == null) spawn();
        recordBuffer.clear();
        recording = true;
        playing = false;
        playIndex = 0;
        info("Recording started.");
    }

    public void stopRecording() {
        recording = false;
        info("Recording stopped (" + recordBuffer.size() + " samples).");
    }

    public void startPlayback() {
        if (recordBuffer.isEmpty()) return;
        playing = true;
        recording = false;
        playIndex = 0;
        info("Playback started (" + recordBuffer.size() + " samples).");
    }

    public void stopPlayback() {
        playing = false;
        if (dummy != null && mc.player != null) {
            dummy.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch());
        }
        info("Playback stopped.");
    }

    public boolean isRecording() { return recording; }
    public boolean isPlaying() { return playing; }
    public List<TickSample> getRecordBuffer() { return recordBuffer; }

    public static final class TickSample {
        public final double x, y, z;
        public final float yaw, pitch;
        public TickSample(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
    }
}
