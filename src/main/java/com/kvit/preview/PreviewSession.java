package com.kvit.preview;

import com.kvit.collector.ChunkBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

record PreviewSession(ResourceKey<Level> dimension, BlockPos pos, ChunkBounds bounds) {
}
