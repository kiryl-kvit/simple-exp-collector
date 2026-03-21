package com.kvit.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record ExpCollectorRecord(int id, int x, int y, int z, long storedXp, String name) {
	private static final String FIELD_ID = "id";
	private static final String FIELD_X = "x";
	private static final String FIELD_Y = "y";
	private static final String FIELD_Z = "z";
	private static final String FIELD_STORED_XP = "storedXp";
	private static final String FIELD_NAME = "name";

	public static final Codec<ExpCollectorRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf(FIELD_ID, 0).forGetter(ExpCollectorRecord::id),
		Codec.INT.fieldOf(FIELD_X).forGetter(ExpCollectorRecord::x),
		Codec.INT.fieldOf(FIELD_Y).forGetter(ExpCollectorRecord::y),
		Codec.INT.fieldOf(FIELD_Z).forGetter(ExpCollectorRecord::z),
		Codec.LONG.optionalFieldOf(FIELD_STORED_XP, 0L).forGetter(ExpCollectorRecord::storedXp),
		Codec.STRING.optionalFieldOf(FIELD_NAME, "").forGetter(ExpCollectorRecord::name)
	).apply(instance, ExpCollectorRecord::new));

	public ExpCollectorRecord {
		name = name == null ? "" : name;
		storedXp = Math.max(0L, storedXp);
	}

	public static long key(BlockPos pos) {
		return pos.asLong();
	}

	public BlockPos blockPos() {
		return new BlockPos(this.x, this.y, this.z);
	}

	public boolean hasName() {
		return !this.name.isBlank();
	}

	public ExpCollectorRecord withId(int id) {
		return new ExpCollectorRecord(id, this.x, this.y, this.z, this.storedXp, this.name);
	}

	public ExpCollectorRecord withStoredXp(long storedXp) {
		return new ExpCollectorRecord(this.id, this.x, this.y, this.z, storedXp, this.name);
	}

	public ExpCollectorRecord withName(String name) {
		return new ExpCollectorRecord(this.id, this.x, this.y, this.z, this.storedXp, name);
	}
}
