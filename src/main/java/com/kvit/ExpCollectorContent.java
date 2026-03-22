package com.kvit;

import com.kvit.blocks.expCollector.ExpCollectorBlock;
import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.items.ExpCollectorBlockItem;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import xyz.nucleoid.packettweaker.PacketContext;

public final class ExpCollectorContent {
	private static final Identifier POLYMER_SYNC_ITEMS = Identifier.fromNamespaceAndPath("polymer", "sync/items");

	private static Block expCollector;
	private static Item expCollectorItem;
	private static BlockEntityType<ExpCollectorBlockEntity> expCollectorBlockEntity;

	private ExpCollectorContent() {
	}

	public static boolean isModdedClient(PacketContext context) {
		var player = context.getPlayer();
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		return PolymerServerNetworking.getSupportedVersion(serverPlayer.connection, POLYMER_SYNC_ITEMS) >= 0;
	}

	public static Block expCollector() {
		return expCollector;
	}

	public static Item expCollectorItem() {
		return expCollectorItem;
	}

	public static BlockEntityType<ExpCollectorBlockEntity> expCollectorBlockEntity() {
		return expCollectorBlockEntity;
	}

	public static Component expCollectorName() {
		return Component.translatable("block." + SimpleExpCollector.MOD_ID + ".exp_collector");
	}

	public static Component menuTitle() {
		return Component.translatable("container." + SimpleExpCollector.MOD_ID + ".exp_collector");
	}

	public static void register() {
		expCollector = Registry.register(BuiltInRegistries.BLOCK, SimpleExpCollector.id("exp_collector"),
			new ExpCollectorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SCULK_CATALYST)
				.setId(ResourceKey.create(Registries.BLOCK, SimpleExpCollector.id("exp_collector")))));

		expCollectorItem = Registry.register(BuiltInRegistries.ITEM, SimpleExpCollector.id("exp_collector"),
			new ExpCollectorBlockItem(
				expCollector,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SimpleExpCollector.id("exp_collector"))),
				Items.SCULK_CATALYST,
				false
			));

		expCollectorBlockEntity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SimpleExpCollector.id("exp_collector"),
			FabricBlockEntityTypeBuilder.create(ExpCollectorBlockEntity::new, expCollector).build());
		PolymerBlockUtils.registerBlockEntity(expCollectorBlockEntity, new PolymerSyncedObject<>() {
			@Override
			public BlockEntityType<?> getPolymerReplacement(BlockEntityType<?> obj, PacketContext context) {
				return isModdedClient(context) ? obj : null;
			}

			@Override
			public boolean canSyncRawToClient(PacketContext context) {
				return isModdedClient(context);
			}
		});

		CreativeModeTab tab = PolymerItemGroupUtils.builder()
			.title(Component.translatable("itemGroup.simple-exp-collector.exp_collector"))
			.icon(() -> new ItemStack(Items.EXPERIENCE_BOTTLE))
			.displayItems((params, output) -> output.accept(expCollectorItem))
			.build();
		PolymerItemGroupUtils.registerPolymerItemGroup(SimpleExpCollector.id("exp_collector"), tab);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(expCollectorItem));
	}
}
