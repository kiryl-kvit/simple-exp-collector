package com.kvit.menu;

import com.kvit.ExpCollectorContent;
import com.kvit.collector.CollectorMessages;
import com.kvit.collector.ExperiencePreview;
import com.kvit.collector.ExpCollectorManager;
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
import java.util.UUID;

public final class ExpCollectorCollectMenu extends ChestMenu {
	private static final int MENU_SIZE = 45;
	private static final int SUMMARY_SLOT = 13;
	private static final int CLEAR_SLOT = 20;
	private static final int HALF_SLOT = 21;
	private static final int MAX_SLOT = 22;
	private static final int BACK_SLOT = 23;
	private static final int CONFIRM_SLOT = 24;
	private static final int MINUS_ONE_SLOT = 27;
	private static final int MINUS_TEN_SLOT = 28;
	private static final int MINUS_HUNDRED_SLOT = 29;
	private static final int MINUS_THOUSAND_SLOT = 30;
	private static final int PLUS_ONE_SLOT = 32;
	private static final int PLUS_TEN_SLOT = 33;
	private static final int PLUS_HUNDRED_SLOT = 34;
	private static final int PLUS_THOUSAND_SLOT = 35;
	private static final ItemStack FILLER_TEMPLATE;

	static {
		FILLER_TEMPLATE = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		FILLER_TEMPLATE.set(DataComponents.ITEM_NAME, ExpCollectorMenuComponents.plain(Component.literal(" ")));
	}

	private final SimpleContainer container;
	private final ServerLevel level;
	private final BlockPos pos;
	private final UUID playerId;
	private final ExperiencePreview.ExperienceState baseExperience;
	private long selectedAmount;

	public ExpCollectorCollectMenu(int syncId, Inventory playerInventory, ServerLevel level, BlockPos pos) {
		this(syncId, playerInventory, new SimpleContainer(MENU_SIZE), level, pos, playerInventory.player.getUUID(), ExperiencePreview.capture(playerInventory.player));
	}

	private ExpCollectorCollectMenu(
		int syncId,
		Inventory playerInventory,
		SimpleContainer container,
		ServerLevel level,
		BlockPos pos,
		UUID playerId,
		ExperiencePreview.ExperienceState baseExperience
	) {
		super(MenuType.GENERIC_9x5, syncId, playerInventory, container, 5);
		this.container = container;
		this.level = level;
		this.pos = pos.immutable();
		this.playerId = playerId;
		this.baseExperience = baseExperience;
		this.selectedAmount = Math.min(defaultSelection(this.getStoredXp()), this.getStoredXp());
		this.fillBackground();
		this.refreshContents();
	}

	@Override
	public boolean stillValid(@NonNull Player player) {
		return AbstractContainerMenu.stillValid(ContainerLevelAccess.create(this.level, this.pos), player, ExpCollectorContent.expCollector());
	}

	@Override
	public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public void clicked(int slotId, int button, @NonNull ClickType clickType, @NonNull Player player) {
		if (slotId >= 0 && slotId < MENU_SIZE) {
			if (clickType == ClickType.PICKUP && button == 0) {
				this.handleClick(slotId, player);
			}
			return;
		}

		super.clicked(slotId, button, clickType, player);
	}

	private void handleClick(int slotId, Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		long storedXp = this.getStoredXp();
		switch (slotId) {
			case CLEAR_SLOT -> this.selectedAmount = 0L;
			case HALF_SLOT -> this.selectedAmount = storedXp / 2L;
			case MAX_SLOT -> this.selectedAmount = storedXp;
			case BACK_SLOT -> {
				this.reopenMainMenu(serverPlayer);
				return;
			}
			case CONFIRM_SLOT -> {
				this.confirm(serverPlayer);
				return;
			}
			case MINUS_ONE_SLOT -> this.adjust(-1L);
			case MINUS_TEN_SLOT -> this.adjust(-10L);
			case MINUS_HUNDRED_SLOT -> this.adjust(-100L);
			case MINUS_THOUSAND_SLOT -> this.adjust(-1000L);
			case PLUS_ONE_SLOT -> this.adjust(1L);
			case PLUS_TEN_SLOT -> this.adjust(10L);
			case PLUS_HUNDRED_SLOT -> this.adjust(100L);
			case PLUS_THOUSAND_SLOT -> this.adjust(1000L);
			default -> {
				return;
			}
		}

		this.selectedAmount = clamp(this.selectedAmount, storedXp);
		this.refreshContents();
		this.broadcastFullState();
	}

	private void adjust(long delta) {
		if (delta > 0L && Long.MAX_VALUE - this.selectedAmount < delta) {
			this.selectedAmount = Long.MAX_VALUE;
			return;
		}
		this.selectedAmount += delta;
	}

	private void confirm(ServerPlayer player) {
		long storedXp = this.getStoredXp();
		long amount = clamp(this.selectedAmount, storedXp);
		if (amount <= 0L) {
			player.sendSystemMessage(Component.literal("Select an amount greater than zero.").withStyle(ChatFormatting.YELLOW), false);
			return;
		}

		ExperiencePreview preview = ExperiencePreview.simulate(this.baseExperience, amount);
		long collected = ExpCollectorManager.collectToPlayer(this.level, this.pos, player, amount);
		if (collected > 0L) {
			Component label = ExpCollectorManager.getCollector(this.level, this.pos)
				.map(record -> record.hasName()
					? CollectorMessages.collectorIdComponent(record.id()).append(Component.literal(" ")).append(CollectorMessages.namedComponent(record.name()))
					: CollectorMessages.collectorIdComponent(record.id()))
				.orElse(CollectorMessages.collectorIdComponent(0));
			player.sendSystemMessage(CollectorMessages.collectionResult(label, collected, preview), false);
		} else {
			player.sendSystemMessage(Component.literal("This collector is empty.").withStyle(ChatFormatting.YELLOW), false);
		}
		this.reopenMainMenu(player);
	}

	private void reopenMainMenu(ServerPlayer player) {
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
			(syncId, inventory, openPlayer) -> new ExpCollectorMenu(syncId, inventory, this.level, this.pos, openPlayer.getUUID()),
			ExpCollectorContent.menuTitle()
		));
	}

	private void fillBackground() {
		for (int i = 0; i < MENU_SIZE; i++) {
			this.container.setItem(i, FILLER_TEMPLATE.copy());
		}
	}

	private void refreshContents() {
		long storedXp = this.getStoredXp();
		this.selectedAmount = clamp(this.selectedAmount, storedXp);
		ExperiencePreview preview = ExperiencePreview.simulate(this.baseExperience, this.selectedAmount);

		this.container.setItem(SUMMARY_SLOT, item(
			this.selectedAmount > 0L ? Items.EXPERIENCE_BOTTLE : Items.GLASS_BOTTLE,
			Component.literal("Selected Collection").withStyle(ChatFormatting.AQUA),
			Component.literal("Selected XP: " + CollectorMessages.formatXp(this.selectedAmount)).withStyle(this.selectedAmount > 0L ? ChatFormatting.GREEN : ChatFormatting.GRAY),
			Component.literal("Stored XP: " + CollectorMessages.formatXp(storedXp)).withStyle(ChatFormatting.GRAY),
			Component.literal("Current: " + preview.currentDisplay()).withStyle(ChatFormatting.GRAY),
			Component.literal("Expected: " + preview.expectedDisplay()).withStyle(this.selectedAmount > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY),
			Component.literal("Level gain: +" + preview.gainedLevels()).withStyle(this.selectedAmount > 0L ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
		));

		this.container.setItem(CLEAR_SLOT, item(Items.BARRIER, Component.literal("Clear").withStyle(ChatFormatting.RED), Component.literal("Set selected XP to 0.").withStyle(ChatFormatting.GRAY)));
		this.container.setItem(HALF_SLOT, item(Items.IRON_INGOT, Component.literal("Set Half").withStyle(ChatFormatting.YELLOW), Component.literal("Select half of stored XP.").withStyle(ChatFormatting.GRAY)));
		this.container.setItem(MAX_SLOT, item(Items.LIME_DYE, Component.literal("Set Max").withStyle(ChatFormatting.GREEN), Component.literal("Select all stored XP.").withStyle(ChatFormatting.GRAY)));
		this.container.setItem(BACK_SLOT, item(Items.ARROW, Component.literal("Back").withStyle(ChatFormatting.GRAY), Component.literal("Return to the collector menu.").withStyle(ChatFormatting.GRAY)));
		this.container.setItem(CONFIRM_SLOT, item(
			this.selectedAmount > 0L ? Items.EMERALD : Items.GRAY_DYE,
			Component.literal("Confirm Collect").withStyle(this.selectedAmount > 0L ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
			Component.literal(this.selectedAmount > 0L ? "Collect the selected XP amount." : "Pick an amount first.").withStyle(ChatFormatting.GRAY),
			Component.literal(this.selectedAmount > 0L ? "Expected: " + preview.expectedDisplay() : "Expected: no change").withStyle(this.selectedAmount > 0L ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
		));

		this.container.setItem(MINUS_ONE_SLOT, amountButton(Items.RED_STAINED_GLASS_PANE, "-1 XP", -1L, storedXp));
		this.container.setItem(MINUS_TEN_SLOT, amountButton(Items.RED_STAINED_GLASS, "-10 XP", -10L, storedXp));
		this.container.setItem(MINUS_HUNDRED_SLOT, amountButton(Items.RED_WOOL, "-100 XP", -100L, storedXp));
		this.container.setItem(MINUS_THOUSAND_SLOT, amountButton(Items.RED_CONCRETE, "-1000 XP", -1000L, storedXp));
		this.container.setItem(PLUS_ONE_SLOT, amountButton(Items.GREEN_STAINED_GLASS_PANE, "+1 XP", 1L, storedXp));
		this.container.setItem(PLUS_TEN_SLOT, amountButton(Items.GREEN_STAINED_GLASS, "+10 XP", 10L, storedXp));
		this.container.setItem(PLUS_HUNDRED_SLOT, amountButton(Items.GREEN_WOOL, "+100 XP", 100L, storedXp));
		this.container.setItem(PLUS_THOUSAND_SLOT, amountButton(Items.GREEN_CONCRETE, "+1000 XP", 1000L, storedXp));
	}

	private ItemStack amountButton(Item item, String label, long delta, long storedXp) {
		long previewAmount = clamp(this.selectedAmount + delta, storedXp);
		boolean changesValue = previewAmount != this.selectedAmount;
		return item(
			item,
			Component.literal(label).withStyle(delta > 0L ? (changesValue ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY) : (changesValue ? ChatFormatting.RED : ChatFormatting.DARK_GRAY)),
			Component.literal("After click: " + CollectorMessages.formatXp(previewAmount) + " XP").withStyle(ChatFormatting.GRAY)
		);
	}

	private long getStoredXp() {
		return ExpCollectorManager.getCollector(this.level, this.pos)
			.map(record -> record.storedXp())
			.orElse(0L);
	}

	private static long defaultSelection(long storedXp) {
		if (storedXp <= 0L) {
			return 0L;
		}
		return Math.min(storedXp, 100L);
	}

	private static long clamp(long amount, long storedXp) {
		return Math.max(0L, Math.min(amount, Math.max(0L, storedXp)));
	}

	private static ItemStack item(Item item, Component name, Component... loreLines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, ExpCollectorMenuComponents.plain(name));
		if (loreLines.length > 0) {
			List<Component> lines = new ArrayList<>(loreLines.length);
			for (Component line : loreLines) {
				lines.add(ExpCollectorMenuComponents.plain(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}
}
