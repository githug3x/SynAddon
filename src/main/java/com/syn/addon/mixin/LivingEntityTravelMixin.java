package com.syn.addon.mixin;

import com.syn.addon.modules.ElytraFlyPlus;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 复刻 deepseekclient 的 TravelEvent：
 * 在 LivingEntity.travel() 入口注入，取消原版计算，
 * 由 ElytraFlyPlus 自行 setVelocity 并调用 move()。
 */
@Mixin(LivingEntity.class)
public class LivingEntityTravelMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void syn$travelOverride(Vec3d movementInput, CallbackInfo ci) {
        if (ElytraFlyPlus.handleTravel((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
