package com.kvit.blocks.expCollector;

import com.kvit.ModContent;
import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.collector.ExpCollectorManager;
import com.kvit.menu.ExpCollectorMenu;
import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public final class ExpCollectorBlock extends BaseEntityBlock implements PolymerBlock {
	public static final MapCodec<ExpCollectorBlock> CODEC = simpleCodec(ExpCollectorBlock::new);

	public ExpCollectorBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return canSyncRawToClient(context) ? state : Blocks.SCULK_CATALYST.defaultBlockState();
	}

	@Override
	public boolean canSyncRawToClient(PacketContext context) {
		return ModContent.isModdedClient(context);
	}

	@Override
	public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
		return new ExpCollectorBlockEntity(pos, state);
	}

	@Override
	protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state,
			@NonNull Level level, @NonNull BlockPos pos, @NonNull Player player,
			@NonNull InteractionHand hand, @NonNull BlockHitResult hit) {
		return openMenu(level, pos, player);
	}

	@Override
	protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
			@NonNull Player player, @NonNull BlockHitResult hit) {
		return openMenu(level, pos, player);
	}

	@Override
	public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
			@Nullable LivingEntity placer, @NonNull ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ExpCollectorBlockEntity blockEntity) {
			ExpCollectorManager.syncLoadedBlockEntity(serverLevel, blockEntity);
		}
	}

	@Override
	protected boolean shouldChangedStateKeepBlockEntity(BlockState newState) {
		return newState.is(ModContent.expCollector());
	}

	private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(level.getBlockEntity(pos) instanceof ExpCollectorBlockEntity)) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			serverPlayer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, openPlayer) -> createMenu(syncId, inventory, serverLevel, pos, openPlayer),
				ModContent.menuTitle()
			));
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}

	private AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inventory,
			ServerLevel level, BlockPos pos, Player player) {
		return new ExpCollectorMenu(syncId, inventory, level, pos, player.getUUID());
	}
}
