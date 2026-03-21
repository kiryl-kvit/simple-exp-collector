package com.kvit.menu;

import com.kvit.ModContent;
import com.kvit.collector.CollectorMessages;
import com.kvit.collector.ExpCollectorManager;
import com.kvit.mixin.AnvilMenuAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

public final class ExpCollectorRenameMenu extends AnvilMenu {
	private static final int RESULT_SLOT = 2;

	private final ServerLevel level;
	private final BlockPos pos;
	private String pendingName = "";

	public ExpCollectorRenameMenu(int syncId, Inventory playerInventory, ServerLevel level, BlockPos pos) {
		super(syncId, playerInventory, ContainerLevelAccess.create(level, pos));
		this.level = level;
		this.pos = pos.immutable();
		this.initializeInput();
	}

	@Override
	public boolean stillValid(@NonNull Player player) {
		return stillValid(ContainerLevelAccess.create(this.level, this.pos), player, ModContent.expCollector());
	}

	@Override
	public void clicked(int slotId, int button, @NonNull ClickType clickType, @NonNull Player player) {
		if (slotId == RESULT_SLOT && clickType == ClickType.PICKUP) {
			super.clicked(slotId, button, clickType, player);
		}
	}

	@Override
	public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean mayPickup(@NonNull Player player, boolean hasStack) {
		return hasStack && !this.resultSlots.getItem(0).isEmpty();
	}

	@Override
	protected void onTake(@NonNull Player player, @NonNull ItemStack stack) {
		String nextName = this.pendingName;
		ExpCollectorManager.rename(this.level, this.pos, nextName);

		this.inputSlots.removeItemNoUpdate(0);
		this.inputSlots.removeItemNoUpdate(1);
		this.resultSlots.setItem(0, ItemStack.EMPTY);
		this.setCarried(ItemStack.EMPTY);
		this.broadcastChanges();

		if (player instanceof ServerPlayer serverPlayer) {
			int id = ExpCollectorManager.getCollector(this.level, this.pos).map(record -> record.id()).orElse(0);
			serverPlayer.sendSystemMessage(CollectorMessages.renameResult(id, nextName));
			serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(syncId, inventory, openPlayer) -> new ExpCollectorMenu(syncId, inventory, this.level, this.pos, openPlayer.getUUID()),
				ModContent.menuTitle()
			));
		}
	}

	@Override
	public void removed(@NonNull Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack carried = this.getCarried();
			if (!carried.isEmpty()) {
				boolean shouldDrop = player.isRemoved() && player.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION;
				if (shouldDrop || serverPlayer.hasDisconnected()) {
					player.drop(carried, false);
				} else {
					player.getInventory().placeItemBackInInventory(carried);
				}
				this.setCarried(ItemStack.EMPTY);
			}
		}

		for (int slot = 0; slot < this.inputSlots.getContainerSize(); slot++) {
			this.inputSlots.setItem(slot, ItemStack.EMPTY);
		}
		this.resultSlots.setItem(0, ItemStack.EMPTY);
	}

	@Override
	public boolean setItemName(String name) {
		this.pendingName = ExpCollectorManager.normalizeName(name);
		return super.setItemName(this.pendingName);
	}

	@Override
	public void createResult() {
		ItemStack base = this.inputSlots.getItem(0);
		if (base.isEmpty()) {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
			this.clearRenameCost();
			this.broadcastChanges();
			return;
		}

		ItemStack result = base.copy();
		if (this.pendingName.isEmpty()) {
			result.remove(DataComponents.CUSTOM_NAME);
		} else {
			result.set(DataComponents.CUSTOM_NAME, MenuComponents.plain(Component.literal(this.pendingName)));
		}
		this.resultSlots.setItem(0, result);
		this.clearRenameCost();
		this.broadcastChanges();
	}

	private void clearRenameCost() {
		((AnvilMenuAccessor) (Object) this).simpleExpCollector$getCostSlot().set(0);
	}

	private void initializeInput() {
		String currentName = ExpCollectorManager.getCollector(this.level, this.pos)
			.map(record -> record.name())
			.orElse("");
		this.pendingName = currentName;

		ItemStack stack = new ItemStack(Items.NAME_TAG);
		stack.set(DataComponents.ITEM_NAME, MenuComponents.plain(ModContent.expCollectorName()));
		if (!currentName.isBlank()) {
			stack.set(DataComponents.CUSTOM_NAME, MenuComponents.plain(Component.literal(currentName)));
		}

		this.inputSlots.setItem(0, stack);
		this.slotsChanged(this.inputSlots);
		this.setItemName(currentName);
	}
}
