package com.kvit.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ExpCollectorSavedData extends SavedData {
	private static final String FIELD_COLLECTORS = "collectors";
	private static final String DATA_NAME = "simple_exp_collector";

	private static final Codec<ExpCollectorSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(Codec.STRING, ExpCollectorRecord.CODEC)
			.optionalFieldOf(FIELD_COLLECTORS, Map.of())
			.forGetter(data -> {
				Map<String, ExpCollectorRecord> out = new HashMap<>(data.collectors.size() * 2);
				data.collectors.forEach((k, v) -> out.put(Long.toString(k), v));
				return out;
			})
	).apply(instance, encodedCollectors -> {
		Map<Long, ExpCollectorRecord> collectors = new HashMap<>();
		encodedCollectors.values().forEach(record -> collectors.put(ExpCollectorRecord.key(record.blockPos()), record));
		return new ExpCollectorSavedData(collectors);
	}));

	public static final SavedDataType<ExpCollectorSavedData> TYPE = new SavedDataType<>(
		DATA_NAME,
		ExpCollectorSavedData::new,
		CODEC,
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private final Map<Long, ExpCollectorRecord> collectors;

	public ExpCollectorSavedData() {
		this(new HashMap<>());
	}

	private ExpCollectorSavedData(Map<Long, ExpCollectorRecord> collectors) {
		this.collectors = new HashMap<>(collectors);
	}

	public Collection<ExpCollectorRecord> getCollectors() {
		return Collections.unmodifiableCollection(this.collectors.values());
	}

	public Optional<ExpCollectorRecord> get(BlockPos pos) {
		return Optional.ofNullable(this.collectors.get(ExpCollectorRecord.key(pos)));
	}

	public boolean putIfChanged(ExpCollectorRecord record) {
		long key = ExpCollectorRecord.key(record.blockPos());
		ExpCollectorRecord previous = this.collectors.get(key);
		if (record.equals(previous)) {
			return false;
		}
		this.collectors.put(key, record);
		this.setDirty();
		return true;
	}

	public void remove(BlockPos pos) {
		if (this.collectors.remove(ExpCollectorRecord.key(pos)) != null) {
			this.setDirty();
		}
	}
}
