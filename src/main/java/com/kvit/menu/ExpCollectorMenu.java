package com.kvit.menu;

import com.kvit.ModContent;
import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.collector.CollectorMessages;
import com.kvit.collector.ExperiencePreview;
import com.kvit.collector.ExpCollectorManager;
import com.kvit.preview.CollectorPreviewManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ExpCollectorMenu extends ChestMenu {
	private static final int MENU_SIZE = 27;
	private static final int INFO_SLOT = 4;
	private static final int RENAME_SLOT = 10;
	private static final int PREVIEW_SLOT = 12;
	private static final int TOGGLE_DROP_SLOT = 13;
	private static final int COLLECT_AMOUNT_SLOT = 14;
	private static final int COLLECT_ALL_SLOT = 16;
	private static final ItemStack FILLER_TEMPLATE;

	static {
		FILLER_TEMPLATE = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		FILLER_TEMPLATE.set(DataComponents.ITEM_NAME, MenuComponents.plain(Component.literal(" ")));
	}

	private final SimpleContainer container;
	private final ServerLevel level;
	private final BlockPos pos;
	private final UUID playerId;
	private final ExperiencePreview.ExperienceState viewerExperience;

	public ExpCollectorMenu(int syncId, Inventory playerInventory, ServerLevel level, BlockPos pos, UUID playerId) {
		this(syncId, playerInventory, new SimpleContainer(MENU_SIZE), level, pos, playerId, ExperiencePreview.capture(playerInventory.player));
	}

	private ExpCollectorMenu(int syncId, Inventory playerInventory, SimpleContainer container, ServerLevel level, BlockPos pos, UUID playerId, ExperiencePreview.ExperienceState viewerExperience) {
		super(MenuType.GENERIC_9x3, syncId, playerInventory, container, 3);
		this.container = container;
		this.level = level;
		this.pos = pos.immutable();
		this.playerId = playerId;
		this.viewerExperience = viewerExperience;
		this.fillBackground();
		this.refreshActionSlots();
	}

	@Override
	public boolean stillValid(@NonNull Player player) {
		return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(this.level, this.pos), player, ModContent.expCollector());
	}

	@Override
	public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void clicked(int slotId, int button, @NonNull ClickType clickType, @NonNull Player player) {
		if (slotId >= 0 && slotId < MENU_SIZE) {
			if (clickType == ClickType.PICKUP && button == 0) {
				this.handleMenuClick(slotId, player);
			}
			return;
		}

		super.clicked(slotId, button, clickType, player);
	}

	private void handleMenuClick(int slotId, Player player) {
		ExpCollectorBlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		switch (slotId) {
			case RENAME_SLOT -> {
				if (player instanceof ServerPlayer serverPlayer) {
					this.openRenameMenu(serverPlayer);
				}
				return;
			}
			case PREVIEW_SLOT -> {
				if (player instanceof ServerPlayer serverPlayer) {
					CollectorPreviewManager.toggle(serverPlayer, blockEntity);
				}
			}
			case TOGGLE_DROP_SLOT -> {
				if (player instanceof ServerPlayer serverPlayer) {
					ExpCollectorManager.toggleMobDrops(this.level, this.pos).ifPresent(updated ->
						serverPlayer.sendSystemMessage(CollectorMessages.mobDropsToggleResult(collectorLabel(updated.id(), updated.name()), updated.mobDropsDisabled()), false)
					);
				}
			}
			case COLLECT_AMOUNT_SLOT -> {
				if (player instanceof ServerPlayer serverPlayer && blockEntity.getStoredXp() > 0L) {
					this.openCollectMenu(serverPlayer);
				}
				return;
			}
			case COLLECT_ALL_SLOT -> {
				if (player instanceof ServerPlayer serverPlayer) {
					this.collectAll(serverPlayer, blockEntity.getStoredXp());
				}
			}
			default -> {
				return;
			}
		}

		this.refreshActionSlots();
		this.broadcastFullState();
	}

	private void fillBackground() {
		for (int i = 0; i < MENU_SIZE; i++) {
			this.container.setItem(i, FILLER_TEMPLATE.copy());
		}
	}

	private void refreshActionSlots() {
		ExpCollectorBlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity == null) {
			return;
		}

		long storedXp = blockEntity.getStoredXp();
		ExperiencePreview collectAllPreview = ExperiencePreview.simulate(this.viewerExperience, storedXp);
		boolean previewing = CollectorPreviewManager.isPreviewing(this.playerId, this.level, this.pos);
		boolean mobDropsDisabled = ExpCollectorManager.getCollector(this.level, this.pos)
			.map(record -> record.mobDropsDisabled())
			.orElse(false);
		String collectorName = ExpCollectorManager.getCollector(this.level, this.pos)
			.map(record -> record.name())
			.filter(name -> !name.isBlank())
			.orElse("none");

		this.container.setItem(INFO_SLOT, actionItem(
			storedXp > 0L ? Items.EXPERIENCE_BOTTLE : Items.GLASS_BOTTLE,
			Component.literal("Stored Experience").withStyle(ChatFormatting.AQUA),
			Component.literal("Stored XP: " + formatXp(storedXp)).withStyle(storedXp > 0L ? ChatFormatting.GREEN : ChatFormatting.GRAY),
			Component.literal("Your level: " + this.viewerExperience.display()).withStyle(ChatFormatting.GRAY),
			Component.literal("Collect all -> " + collectAllPreview.expectedDisplay()).withStyle(storedXp > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY),
			Component.literal("Working area: this chunk").withStyle(ChatFormatting.GRAY),
			Component.literal("Mob drops: " + (mobDropsDisabled ? "disabled" : "enabled")).withStyle(mobDropsDisabled ? ChatFormatting.YELLOW : ChatFormatting.GREEN)
		));

		this.container.setItem(RENAME_SLOT, actionItem(
			Items.NAME_TAG,
			Component.literal("Rename Collector").withStyle(ChatFormatting.AQUA),
			Component.literal("Current name: " + collectorName).withStyle(ChatFormatting.GRAY)
		));

		this.container.setItem(PREVIEW_SLOT, actionItem(
			previewing ? Items.ENDER_EYE : Items.SPYGLASS,
			Component.literal(previewing ? "Hide Chunk" : "Show Chunk").withStyle(previewing ? ChatFormatting.YELLOW : ChatFormatting.GREEN),
			Component.literal("Shows the chunk this collector works in.").withStyle(ChatFormatting.GRAY)
		));

		this.container.setItem(TOGGLE_DROP_SLOT, actionItem(
			mobDropsDisabled ? Items.BARRIER : Items.CHEST,
			Component.literal(mobDropsDisabled ? "Enable Mob Drops" : "Disable Mob Drops").withStyle(mobDropsDisabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
			Component.literal("Current: " + (mobDropsDisabled ? "disabled" : "enabled")).withStyle(mobDropsDisabled ? ChatFormatting.YELLOW : ChatFormatting.GREEN),
			Component.literal("XP collection stays enabled.").withStyle(ChatFormatting.GRAY),
			Component.literal(mobDropsDisabled ? "Allow mobs in this chunk to drop loot again." : "Prevent mobs in this chunk from dropping loot.").withStyle(ChatFormatting.GRAY)
		));

		boolean hasXp = storedXp > 0L;
		this.container.setItem(COLLECT_AMOUNT_SLOT, actionItem(
			hasXp ? Items.EXPERIENCE_BOTTLE : Items.GLASS_BOTTLE,
			Component.literal("Collect Amount").withStyle(hasXp ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
			Component.literal(hasXp ? "Open amount picker with expected level preview." : "No XP stored.").withStyle(ChatFormatting.GRAY)
		));

		this.container.setItem(COLLECT_ALL_SLOT, actionItem(
			hasXp ? Items.LIME_DYE : Items.GRAY_DYE,
			Component.literal("Collect All").withStyle(hasXp ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
			Component.literal(hasXp ? "Collect all " + formatXp(storedXp) + " XP." : "Collector is empty.").withStyle(ChatFormatting.GRAY),
			Component.literal(hasXp ? "Expected: " + collectAllPreview.expectedDisplay() : "Expected: no change").withStyle(hasXp ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
		));
	}

	private void openRenameMenu(ServerPlayer player) {
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
			(syncId, inventory, openPlayer) -> new ExpCollectorRenameMenu(syncId, inventory, this.level, this.pos),
			Component.literal("Rename Collector")
		));
	}

	private void openCollectMenu(ServerPlayer player) {
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
			(syncId, inventory, openPlayer) -> new ExpCollectorCollectMenu(syncId, inventory, this.level, this.pos),
			Component.literal("Collect Experience")
		));
	}

	private void collectAll(ServerPlayer player, long storedXp) {
		if (storedXp <= 0L) {
			player.sendSystemMessage(Component.literal("This collector is empty.").withStyle(ChatFormatting.YELLOW), false);
			return;
		}

		ExperiencePreview preview = ExperiencePreview.simulate(player, storedXp);
		long collected = ExpCollectorManager.collectToPlayer(this.level, this.pos, player, storedXp);
		if (collected > 0L) {
			Component label = ExpCollectorManager.getCollector(this.level, this.pos)
				.map(record -> collectorLabel(record.id(), record.name()))
				.orElse(CollectorMessages.collectorIdComponent(0));
			player.sendSystemMessage(CollectorMessages.collectionResult(label, collected, preview), false);
		}
	}

	private static Component collectorLabel(int id, String name) {
		if (name.isBlank()) {
			return CollectorMessages.collectorIdComponent(id);
		}

		return CollectorMessages.collectorIdComponent(id)
			.append(Component.literal(" "))
			.append(CollectorMessages.namedComponent(name));
	}

	private ExpCollectorBlockEntity getBlockEntity() {
		return this.level.getBlockEntity(this.pos) instanceof ExpCollectorBlockEntity blockEntity ? blockEntity : null;
	}

	private static ItemStack actionItem(Item item, Component name, Component... loreLines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, MenuComponents.plain(name));

		if (loreLines.length > 0) {
			List<Component> lines = new ArrayList<>(loreLines.length);
			for (Component line : loreLines) {
				lines.add(MenuComponents.plain(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}

		return stack;
	}

	public static String formatXp(long xp) {
		return CollectorMessages.formatXp(xp);
	}
}
