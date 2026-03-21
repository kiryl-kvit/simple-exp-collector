package com.kvit.config;

import com.kvit.SimpleExpCollector;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public record SimpleExpCollectorConfig(int previewRefreshTicks, int previewParticleStep, int orbCollectionIntervalTicks) {
	private static final String KEY_PREVIEW_REFRESH_TICKS = "preview_refresh_ticks";
	private static final String KEY_PREVIEW_PARTICLE_STEP = "preview_particle_step";
	private static final String KEY_ORB_COLLECTION_INTERVAL_TICKS = "orb_collection_interval_ticks";

	private static final int DEFAULT_PREVIEW_REFRESH_TICKS = 10;
	private static final int DEFAULT_PREVIEW_PARTICLE_STEP = 1;
	private static final int DEFAULT_ORB_COLLECTION_INTERVAL_TICKS = 5;

	public static SimpleExpCollectorConfig load() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configPath = configDir.resolve(SimpleExpCollector.MOD_ID + ".toml");

		try {
			Files.createDirectories(configDir);
			if (Files.notExists(configPath)) {
				Files.writeString(configPath, defaultConfigContents(), StandardCharsets.UTF_8);
			}

			int[] previewRefreshTicks = {DEFAULT_PREVIEW_REFRESH_TICKS};
			int[] previewParticleStep = {DEFAULT_PREVIEW_PARTICLE_STEP};
			int[] orbCollectionIntervalTicks = {DEFAULT_ORB_COLLECTION_INTERVAL_TICKS};

			try (Stream<String> lines = Files.lines(configPath, StandardCharsets.UTF_8)) {
				lines.forEach(rawLine -> {
					String line = stripComment(rawLine).strip();
					if (line.isEmpty()) {
						return;
					}

					int separator = line.indexOf('=');
					if (separator < 0) {
						return;
					}

					String key = line.substring(0, separator).strip();
					String value = line.substring(separator + 1).strip();
					try {
						switch (key) {
							case KEY_PREVIEW_REFRESH_TICKS -> previewRefreshTicks[0] = Math.max(2, Integer.parseInt(value));
							case KEY_PREVIEW_PARTICLE_STEP -> previewParticleStep[0] = Math.max(1, Integer.parseInt(value));
							case KEY_ORB_COLLECTION_INTERVAL_TICKS -> orbCollectionIntervalTicks[0] = Math.max(1, Integer.parseInt(value));
							default -> {
							}
						}
					} catch (NumberFormatException exception) {
						SimpleExpCollector.LOGGER.warn("Ignoring invalid config value '{}' for key '{}'", value, key);
					}
				});
			}

			return new SimpleExpCollectorConfig(previewRefreshTicks[0], previewParticleStep[0], orbCollectionIntervalTicks[0]);
		} catch (IOException exception) {
			SimpleExpCollector.LOGGER.error("Failed to load config, using defaults", exception);
			return new SimpleExpCollectorConfig(
				DEFAULT_PREVIEW_REFRESH_TICKS,
				DEFAULT_PREVIEW_PARTICLE_STEP,
				DEFAULT_ORB_COLLECTION_INTERVAL_TICKS
			);
		}
	}

	private static String stripComment(String line) {
		int commentIndex = line.indexOf('#');
		return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
	}

	private static String defaultConfigContents() {
		return """
			# Preview particle refresh interval in ticks.
			%s = %d

			# Spacing between preview particles in blocks.
			%s = %d

			# How often collectors vacuum experience orbs from their chunk.
			%s = %d
			""".formatted(
			KEY_PREVIEW_REFRESH_TICKS, DEFAULT_PREVIEW_REFRESH_TICKS,
			KEY_PREVIEW_PARTICLE_STEP, DEFAULT_PREVIEW_PARTICLE_STEP,
			KEY_ORB_COLLECTION_INTERVAL_TICKS, DEFAULT_ORB_COLLECTION_INTERVAL_TICKS
		);
	}
}
