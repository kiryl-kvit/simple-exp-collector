package com.kvit;

import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.collector.ExpCollectorManager;
import com.kvit.command.ExpCommand;
import com.kvit.config.SimpleExpCollectorConfig;
import com.kvit.network.ExpCollectorVersionNetworking;
import com.kvit.preview.CollectorPreviewManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class SimpleExpCollector implements ModInitializer {
	public static final String MOD_ID = "simple-exp-collector";
	public static final String MOD_VERSION = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.orElseThrow(() -> new IllegalStateException("Missing mod container for " + MOD_ID))
		.getMetadata()
		.getVersion()
		.getFriendlyString();
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static SimpleExpCollectorConfig config;

	@Override
	public void onInitialize() {
		config = SimpleExpCollectorConfig.load();
		ExpCollectorContent.register();
		ExpCollectorVersionNetworking.register();

		ServerWorldEvents.LOAD.register((server, world) -> ExpCollectorManager.handleWorldLoad(world));
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
			if (blockEntity instanceof ExpCollectorBlockEntity collector) {
				ExpCollectorManager.queueLoadedBlockEntity(world, collector);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ExpCommand.register(dispatcher));
		ServerTickEvents.END_WORLD_TICK.register(ExpCollectorManager::tickWorld);
		ServerTickEvents.END_SERVER_TICK.register(CollectorPreviewManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CollectorPreviewManager.clearAll();
			ExpCollectorManager.clearPendingLoadedBlockEntities();
		});

		LOGGER.info(
			"{} initialized with preview_refresh_ticks={} orb_collection_interval_ticks={}",
			MOD_ID,
			config.previewRefreshTicks(),
			config.orbCollectionIntervalTicks()
		);
	}

	public static String getModVersion() {
		return MOD_VERSION;
	}

	public static SimpleExpCollectorConfig getConfig() {
		return Objects.requireNonNull(config, "Config accessed before mod initialization");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
