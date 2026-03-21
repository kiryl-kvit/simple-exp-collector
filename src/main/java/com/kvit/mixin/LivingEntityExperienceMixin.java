package com.kvit.mixin;

import com.kvit.collector.ExpCollectorManager;
import com.kvit.data.ExpCollectorRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(LivingEntity.class)
abstract class LivingEntityExperienceMixin {
	@Shadow
	protected abstract boolean shouldDropExperience();

	@Shadow
	protected abstract boolean isAlwaysExperienceDropper();

	@Shadow
	public abstract boolean wasExperienceConsumed();

	@Shadow
	public abstract void skipDropExperience();

	@Shadow
	public abstract int getExperienceReward(ServerLevel serverLevel, @Nullable Entity entity);

	@Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
	private void simpleExpCollector$collectMobExperience(ServerLevel serverLevel, @Nullable Entity entity, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Mob)) {
			return;
		}
		if (this.wasExperienceConsumed()) {
			return;
		}

		Optional<ExpCollectorRecord> collector = ExpCollectorManager.getCollectorInChunk(serverLevel, self.chunkPosition());
		if (collector.isEmpty()) {
			return;
		}

		boolean canDrop = this.isAlwaysExperienceDropper()
			|| this.shouldDropExperience() && serverLevel.getGameRules().get(GameRules.MOB_DROPS);
		if (!canDrop) {
			return;
		}

		int xp = this.getExperienceReward(serverLevel, entity);
		this.skipDropExperience();
		if (xp > 0) {
			ExpCollectorManager.addXp(serverLevel, collector.get().blockPos(), xp);
		}
		ci.cancel();
	}
}
