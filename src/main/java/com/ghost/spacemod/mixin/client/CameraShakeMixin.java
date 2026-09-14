package com.ghost.spacemod.mixin.client;

import com.ghost.spacemod.client.CryoCastEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a decaying visual camera shake (rotation only — does not affect the player's aim). */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void spacemod$shake(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float tickDelta, CallbackInfo ci) {
        float amt = CryoCastEffects.shakeAmount();
        if (amt > 0.01f) {
            double t = CryoCastEffects.shakeFrame() + tickDelta;
            float dYaw = amt * 2.0f * (float) Math.sin(t * 24.0);
            float dPitch = amt * 1.4f * (float) Math.cos(t * 20.0);
            setRotation(getYaw() + dYaw, getPitch() + dPitch);
        }
    }
}
