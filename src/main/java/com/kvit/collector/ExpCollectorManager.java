package com.kvit.collector;

import com.kvit.ExpCollectorContent;
import com.kvit.SimpleExpCollector;
import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.data.ExpCollectorRecord;
import com.kvit.data.ExpCollectorSavedData;
import com.kvit.mixin.ExperienceOrbAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ExpCollectorManager {
	public static final int CHUNK_SIZE = 16;
	public static final int MAX_NAME_LENGTH = 50;

	private ExpCollectorManager() {
	}

	public static void tickWorld(ServerLevel level) {
		if (level.getGameTime() % SimpleExpCollector.getConfig().orbCollectionIntervalTicks() != 0L) {
			return;
		}

		for (ExpCollectorRecord record : List.copyOf(getData(level).getCollectors())) {
			BlockPos pos = record.blockPos();
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (!level.getBlockState(pos).is(ExpCollectorContent.expCollector())) {
				remove(level, pos);
				continue;
			}
			collectExperienceOrbs(level, pos);
		}
	}

	public static void handleWorldLoad(ServerLevel level) {
		ExpCollectorSavedData data = getData(level);
		List<BlockPos> toRemove = new ArrayList<>();
		for (ExpCollectorRecord record : data.getCollectors()) {
			BlockPos pos = record.blockPos();
			if (level.isLoaded(pos) && !level.getBlockState(pos).is(ExpCollectorContent.expCollector())) {
				toRemove.add(pos);
			}
		}
		for (BlockPos pos : toRemove) {
			data.remove(pos);
		}

		ensureStableIds(level.getServer());
	}

	public static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
		ChunkPos chunkPos = new ChunkPos(pos);
		for (ExpCollectorRecord record : List.copyOf(getData(level).getCollectors())) {
			BlockPos recordPos = record.blockPos();
			if (!new ChunkPos(recordPos).equals(chunkPos) || recordPos.equals(pos)) {
				continue;
			}
			if (level.isLoaded(recordPos) && !level.getBlockState(recordPos).is(ExpCollectorContent.expCollector())) {
				remove(level, recordPos);
				continue;
			}
			return false;
		}
		return true;
	}

	public static Optional<ExpCollectorRecord> getCollector(ServerLevel level, BlockPos pos) {
		return getData(level).get(pos);
	}

	public static Optional<ExpCollectorRecord> getCollectorInChunk(ServerLevel level, ChunkPos chunkPos) {
		for (ExpCollectorRecord record : List.copyOf(getData(level).getCollectors())) {
			BlockPos recordPos = record.blockPos();
			if (!new ChunkPos(recordPos).equals(chunkPos)) {
				continue;
			}
			if (level.isLoaded(recordPos) && !level.getBlockState(recordPos).is(ExpCollectorContent.expCollector())) {
				remove(level, recordPos);
				continue;
			}
			if (new ChunkPos(recordPos).equals(chunkPos)) {
				return Optional.of(record);
			}
		}
		return Optional.empty();
	}

	public static boolean areMobDropsDisabledInChunk(ServerLevel level, ChunkPos chunkPos) {
		return getCollectorInChunk(level, chunkPos)
			.map(ExpCollectorRecord::mobDropsDisabled)
			.orElse(false);
	}

	public static void syncLoadedBlockEntity(ServerLevel level, ExpCollectorBlockEntity blockEntity) {
		ExpCollectorSavedData data = getData(level);
		BlockPos pos = blockEntity.getBlockPos();
		Optional<ExpCollectorRecord> existing = data.get(pos);
		if (existing.isPresent()) {
			blockEntity.setStoredXpSilently(existing.get().storedXp());
			return;
		}

		ExpCollectorRecord record = new ExpCollectorRecord(allocateNextId(level.getServer()), pos.getX(), pos.getY(), pos.getZ(), blockEntity.getStoredXp(), "", false);
		data.putIfChanged(record);
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		getData(level).remove(pos);
	}

	public static boolean rename(ServerLevel level, BlockPos pos, String name) {
		ExpCollectorSavedData data = getData(level);
		ExpCollectorRecord record = data.get(pos).orElse(null);
		if (record == null) {
			return false;
		}

		ExpCollectorRecord updated = record.withName(normalizeName(name));
		boolean changed = data.putIfChanged(updated);
		syncLoadedBlockEntity(level, pos, updated);
		return changed;
	}

	public static Optional<ExpCollectorRecord> toggleMobDrops(ServerLevel level, BlockPos pos) {
		ExpCollectorSavedData data = getData(level);
		ExpCollectorRecord record = data.get(pos).orElse(null);
		if (record == null) {
			return Optional.empty();
		}

		ExpCollectorRecord updated = record.withMobDropsDisabled(!record.mobDropsDisabled());
		data.putIfChanged(updated);
		return Optional.of(updated);
	}

	public static long addXp(ServerLevel level, BlockPos pos, long amount) {
		if (amount <= 0L) {
			return 0L;
		}

		ExpCollectorSavedData data = getData(level);
		ExpCollectorRecord record = data.get(pos).orElseGet(() -> createRecord(level, pos));
		long updatedXp = Long.MAX_VALUE - record.storedXp() < amount ? Long.MAX_VALUE : record.storedXp() + amount;
		ExpCollectorRecord updated = record.withStoredXp(updatedXp);
		data.putIfChanged(updated);
		syncLoadedBlockEntity(level, pos, updated);
		return updatedXp - record.storedXp();
	}

	public static long drainXp(ServerLevel level, BlockPos pos, long amount) {
		if (amount <= 0L) {
			return 0L;
		}

		ExpCollectorSavedData data = getData(level);
		ExpCollectorRecord record = data.get(pos).orElse(null);
		if (record == null || record.storedXp() <= 0L) {
			return 0L;
		}

		long drained = Math.min(record.storedXp(), amount);
		ExpCollectorRecord updated = record.withStoredXp(record.storedXp() - drained);
		data.putIfChanged(updated);
		syncLoadedBlockEntity(level, pos, updated);
		return drained;
	}

	public static long collectToPlayer(ServerLevel level, BlockPos pos, Player player, long amount) {
		long drained = drainXp(level, pos, amount);
		long remaining = drained;
		while (remaining > 0L) {
			int portion = (int) Math.min(Integer.MAX_VALUE, remaining);
			player.giveExperiencePoints(portion);
			remaining -= portion;
		}
		return drained;
	}

	public static List<CollectorReference> getAllCollectors(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		ensureStableIds(server);

		List<CollectorReference> collectors = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ExpCollectorRecord record : getData(level).getCollectors()) {
				collectors.add(new CollectorReference(level, record));
			}
		}

		collectors.sort(Comparator
			.comparingInt((CollectorReference collector) -> collector.record().id())
			.thenComparing(collector -> collector.level().dimension().toString())
			.thenComparingInt(collector -> collector.record().x())
			.thenComparingInt(collector -> collector.record().y())
			.thenComparingInt(collector -> collector.record().z()));
		return collectors;
	}

	public static Optional<CollectorReference> getCollector(MinecraftServer server, int id) {
		if (id <= 0) {
			return Optional.empty();
		}

		return getAllCollectors(server).stream()
			.filter(collector -> collector.record().id() == id)
			.findFirst();
	}

	public static List<CollectorReference> getCollectorsByName(MinecraftServer server, String name) {
		String normalized = normalizeName(name);
		if (normalized.isEmpty()) {
			return List.of();
		}

		String lowered = normalized.toLowerCase(Locale.ROOT);
		return getAllCollectors(server).stream()
			.filter(collector -> collector.record().hasName())
			.filter(collector -> collector.record().name().toLowerCase(Locale.ROOT).equals(lowered))
			.toList();
	}

	public static String normalizeName(String name) {
		String normalized = name == null ? "" : name.trim();
		if (normalized.length() > MAX_NAME_LENGTH) {
			return normalized.substring(0, MAX_NAME_LENGTH);
		}
		return normalized;
	}

	public static ChunkBounds getChunkBounds(BlockPos pos) {
		int chunkX = Math.floorDiv(pos.getX(), CHUNK_SIZE);
		int chunkZ = Math.floorDiv(pos.getZ(), CHUNK_SIZE);
		return new ChunkBounds(chunkX, chunkX, chunkZ, chunkZ);
	}

	public static void ensureStableIds(MinecraftServer server) {
		Objects.requireNonNull(server, "server");

		List<CollectorReference> collectors = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ExpCollectorRecord record : getData(level).getCollectors()) {
				collectors.add(new CollectorReference(level, record));
			}
		}

		collectors.sort(Comparator
			.comparingInt((CollectorReference collector) -> collector.record().id() > 0 ? collector.record().id() : Integer.MAX_VALUE)
			.thenComparing(collector -> collector.level().dimension().toString())
			.thenComparingInt(collector -> collector.record().x())
			.thenComparingInt(collector -> collector.record().y())
			.thenComparingInt(collector -> collector.record().z()));

		int nextId = 1;
		for (CollectorReference collector : collectors) {
			ExpCollectorRecord record = collector.record();
			if (record.id() == nextId) {
				nextId++;
				continue;
			}

			ExpCollectorRecord updated = record.withId(nextId);
			getData(collector.level()).putIfChanged(updated);
			syncLoadedBlockEntity(collector.level(), collector.blockPos(), updated);
			nextId++;
		}
	}

	private static ExpCollectorRecord createRecord(ServerLevel level, BlockPos pos) {
		ExpCollectorRecord record = new ExpCollectorRecord(allocateNextId(level.getServer()), pos.getX(), pos.getY(), pos.getZ(), 0L, "", false);
		getData(level).putIfChanged(record);
		return record;
	}

	private static void collectExperienceOrbs(ServerLevel level, BlockPos pos) {
		ChunkPos chunkPos = new ChunkPos(pos);
		AABB bounds = new AABB(
			chunkPos.getMinBlockX(),
			level.getMinY(),
			chunkPos.getMinBlockZ(),
			chunkPos.getMaxBlockX() + 1.0D,
			level.getMaxY() + 1.0D,
			chunkPos.getMaxBlockZ() + 1.0D
		);

		long total = 0L;
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, bounds)) {
			if (!chunkPos.contains(orb.blockPosition())) {
				continue;
			}
			int count = Math.max(((ExperienceOrbAccessor) orb).simpleExpCollector$getCount(), 1);
			total += (long) orb.getValue() * count;
			orb.discard();
		}

		if (total > 0L) {
			addXp(level, pos, total);
		}
	}

	private static int allocateNextId(MinecraftServer server) {
		return computeMaxId(server) + 1;
	}

	private static int computeMaxId(MinecraftServer server) {
		int maxId = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (ExpCollectorRecord record : getData(level).getCollectors()) {
				if (record.id() > 0) {
					maxId = Math.max(maxId, record.id());
				}
			}
		}
		return maxId;
	}

	private static void syncLoadedBlockEntity(ServerLevel level, BlockPos pos, ExpCollectorRecord record) {
		if (level.getBlockEntity(pos) instanceof ExpCollectorBlockEntity blockEntity) {
			blockEntity.setStoredXpSilently(record.storedXp());
		}
	}

	private static ExpCollectorSavedData getData(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(ExpCollectorSavedData.TYPE);
	}

	public record CollectorReference(ServerLevel level, ExpCollectorRecord record) {
		public BlockPos blockPos() {
			return this.record.blockPos();
		}
	}
}
