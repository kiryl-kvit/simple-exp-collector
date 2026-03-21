package com.kvit.preview;

import com.kvit.SimpleExpCollector;
import com.kvit.blocks.expCollector.entity.ExpCollectorBlockEntity;
import com.kvit.collector.ChunkBounds;
import com.kvit.collector.ExpCollectorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CollectorPreviewManager {
	private static final DustParticleOptions PARTICLE_EMPTY = new DustParticleOptions(0x33CCFF, 2.5F);
	private static final DustParticleOptions PARTICLE_STORED = new DustParticleOptions(0x66FF88, 2.5F);
	private static final int PILLAR_HEIGHT = 6;

	private static final Map<UUID, PreviewSession> ACTIVE_PREVIEWS = new HashMap<>();

	private CollectorPreviewManager() {
	}

	public static void toggle(ServerPlayer player, ExpCollectorBlockEntity blockEntity) {
		if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}

		BlockPos pos = blockEntity.getBlockPos().immutable();
		PreviewSession current = ACTIVE_PREVIEWS.get(player.getUUID());
		if (current != null && current.dimension().equals(serverLevel.dimension()) && current.pos().equals(pos)) {
			ACTIVE_PREVIEWS.remove(player.getUUID());
			return;
		}

		ChunkBounds bounds = ExpCollectorManager.getChunkBounds(pos);
		ACTIVE_PREVIEWS.put(player.getUUID(), new PreviewSession(serverLevel.dimension(), pos, bounds));
	}

	public static boolean isPreviewing(UUID playerId, ServerLevel level, BlockPos pos) {
		PreviewSession session = ACTIVE_PREVIEWS.get(playerId);
		return session != null && session.dimension().equals(level.dimension()) && session.pos().equals(pos);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % SimpleExpCollector.getConfig().previewRefreshTicks() != 0) {
			return;
		}

		Iterator<Map.Entry<UUID, PreviewSession>> iterator = ACTIVE_PREVIEWS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PreviewSession> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			ServerLevel level = server.getLevel(entry.getValue().dimension());
			if (level == null || player.level() != level) {
				continue;
			}

			if (!(level.getBlockEntity(entry.getValue().pos()) instanceof ExpCollectorBlockEntity blockEntity)) {
				iterator.remove();
				continue;
			}

			ChunkBounds bounds = ExpCollectorManager.getChunkBounds(blockEntity.getBlockPos());
			render(level, player, blockEntity, bounds, SimpleExpCollector.getConfig().previewParticleStep());
		}
	}

	public static void clearAll() {
		ACTIVE_PREVIEWS.clear();
	}

	private static void render(ServerLevel level, ServerPlayer player, ExpCollectorBlockEntity blockEntity, ChunkBounds bounds, int step) {
		DustParticleOptions particle = blockEntity.getStoredXp() > 0L ? PARTICLE_STORED : PARTICLE_EMPTY;
		double y = blockEntity.getBlockPos().getY() + 1.1D;

		double minX = bounds.minBlockX();
		double maxX = bounds.maxBlockXExclusive();
		double minZ = bounds.minBlockZ();
		double maxZ = bounds.maxBlockZExclusive();

		send(level, player, particle, minX, y, minZ);
		send(level, player, particle, minX, y, maxZ);
		send(level, player, particle, maxX, y, minZ);
		send(level, player, particle, maxX, y, maxZ);

		int lastBlockX = bounds.maxBlockXExclusive() - 1;
		for (int x = bounds.minBlockX() + step; x <= lastBlockX; x += step) {
			send(level, player, particle, x + 0.5D, y, minZ);
			send(level, player, particle, x + 0.5D, y, maxZ);
		}

		int lastBlockZ = bounds.maxBlockZExclusive() - 1;
		for (int z = bounds.minBlockZ() + step; z <= lastBlockZ; z += step) {
			send(level, player, particle, minX, y, z + 0.5D);
			send(level, player, particle, maxX, y, z + 0.5D);
		}

		for (double pillarY = y + 1.0D; pillarY <= y + PILLAR_HEIGHT; pillarY += 1.0D) {
			send(level, player, particle, minX, pillarY, minZ);
			send(level, player, particle, minX, pillarY, maxZ);
			send(level, player, particle, maxX, pillarY, minZ);
			send(level, player, particle, maxX, pillarY, maxZ);
		}
	}

	private static void send(ServerLevel level, ServerPlayer player, DustParticleOptions particle, double x, double y, double z) {
		level.sendParticles(player, particle, true, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
	}
}
