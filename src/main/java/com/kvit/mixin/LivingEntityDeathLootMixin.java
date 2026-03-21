package com.kvit.mixin;

import com.kvit.collector.ExpCollectorManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
abstract class LivingEntityDeathLootMixin {
	@Shadow
	protected abstract void dropFromLootTable(ServerLevel serverLevel, DamageSource damageSource, boolean playerKill);

	@Shadow
	protected abstract void dropCustomDeathLoot(ServerLevel serverLevel, DamageSource damageSource, boolean playerKill);

	@Shadow
	protected abstract void dropEquipment(ServerLevel serverLevel);

	@Redirect(
		method = "dropAllDeathLoot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"
		)
	)
	private void simpleExpCollector$maybeDropFromLootTable(LivingEntity instance, ServerLevel serverLevel, DamageSource damageSource, boolean playerKill) {
		if (!this.simpleExpCollector$shouldSuppressMobDrops(serverLevel)) {
			this.dropFromLootTable(serverLevel, damageSource, playerKill);
		}
	}

	@Redirect(
		method = "dropAllDeathLoot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"
		)
	)
	private void simpleExpCollector$maybeDropCustomDeathLoot(LivingEntity instance, ServerLevel serverLevel, DamageSource damageSource, boolean playerKill) {
		if (!this.simpleExpCollector$shouldSuppressMobDrops(serverLevel)) {
			this.dropCustomDeathLoot(serverLevel, damageSource, playerKill);
		}
	}

	@Redirect(
		method = "dropAllDeathLoot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V"
		)
	)
	private void simpleExpCollector$maybeDropEquipment(LivingEntity instance, ServerLevel serverLevel) {
		if (!this.simpleExpCollector$shouldSuppressMobDrops(serverLevel)) {
			this.dropEquipment(serverLevel);
		}
	}

	private boolean simpleExpCollector$shouldSuppressMobDrops(ServerLevel serverLevel) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Mob)) {
			return false;
		}

		return ExpCollectorManager.areMobDropsDisabledInChunk(serverLevel, self.chunkPosition());
	}
}
