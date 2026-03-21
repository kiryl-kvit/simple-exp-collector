package com.kvit.items;

import com.kvit.ModContent;
import com.kvit.collector.ExpCollectorManager;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public final class ExpCollectorBlockItem extends PolymerBlockItem {
	public ExpCollectorBlockItem(Block block, Item.Properties properties, Item polymerItem, boolean useModel) {
		super(block, properties, polymerItem, useModel);
	}

	@Override
	public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
		BlockPlaceContext updated = super.updatePlacementContext(context);
		if (updated == null || updated.getLevel().isClientSide()) {
			return updated;
		}

		if (updated.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
			&& !ExpCollectorManager.canPlaceAt(serverLevel, updated.getClickedPos())) {
			if (updated.getPlayer() instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
					Component.literal("There is already an exp collector in this chunk.").withStyle(ChatFormatting.RED),
					false
				);
			}
			return null;
		}

		return updated;
	}

	@Override
	public @NonNull InteractionResult place(BlockPlaceContext context) {
		return super.place(context);
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return canSyncRawToClient(context) ? this : super.getPolymerItem(itemStack, context);
	}

	@Override
	public ItemStack getPolymerItemStack(ItemStack itemStack, TooltipFlag tooltipType, PacketContext context) {
		if (canSyncRawToClient(context)) {
			return itemStack;
		}

		ItemStack result = super.getPolymerItemStack(itemStack, tooltipType, context);
		result.set(DataComponents.ITEM_NAME, ModContent.expCollectorName());
		return result;
	}

	@Override
	public boolean canSyncRawToClient(PacketContext context) {
		return ModContent.isModdedClient(context);
	}
}
