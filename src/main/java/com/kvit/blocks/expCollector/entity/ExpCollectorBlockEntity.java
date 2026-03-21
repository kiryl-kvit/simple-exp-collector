package com.kvit.blocks.expCollector.entity;

import com.kvit.ModContent;
import com.kvit.collector.ExpCollectorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

public final class ExpCollectorBlockEntity extends BlockEntity {
	private static final String NBT_STORED_XP = "StoredXp";

	private long storedXp;

	public ExpCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.expCollectorBlockEntity(), pos, state);
	}

	public long getStoredXp() {
		return this.storedXp;
	}

	public void setStoredXpSilently(long storedXp) {
		long normalized = Math.max(0L, storedXp);
		if (this.storedXp == normalized) {
			return;
		}

		this.storedXp = normalized;
		this.setChanged();
	}

	@Override
	protected void loadAdditional(@NonNull ValueInput input) {
		super.loadAdditional(input);
		this.storedXp = Math.max(0L, input.getLongOr(NBT_STORED_XP, 0L));
	}

	@Override
	protected void saveAdditional(@NonNull ValueOutput output) {
		super.saveAdditional(output);
		output.putLong(NBT_STORED_XP, this.storedXp);
	}

	@Override
	public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
		if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			ExpCollectorManager.remove(serverLevel, pos);
		}
		super.preRemoveSideEffects(pos, state);
	}
}
