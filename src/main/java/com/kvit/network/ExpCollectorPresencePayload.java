package com.kvit.network;

import com.kvit.SimpleExpCollector;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ExpCollectorPresencePayload() implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final Identifier CHANNEL_ID = SimpleExpCollector.id("presence");
	public static final ExpCollectorPresencePayload INSTANCE = new ExpCollectorPresencePayload();
	public static final Type<ExpCollectorPresencePayload> TYPE = new Type<>(CHANNEL_ID);
	public static final StreamCodec<ContextByteBuf, ExpCollectorPresencePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ExpCollectorPresencePayload> type() {
		return TYPE;
	}
}
