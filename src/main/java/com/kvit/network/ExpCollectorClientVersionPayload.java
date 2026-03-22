package com.kvit.network;

import com.kvit.SimpleExpCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ExpCollectorClientVersionPayload(String version) implements CustomPacketPayload {
	public static final Type<ExpCollectorClientVersionPayload> TYPE = new Type<>(SimpleExpCollector.id("client_version"));
	public static final StreamCodec<FriendlyByteBuf, ExpCollectorClientVersionPayload> CODEC = CustomPacketPayload.codec(
		(payload, buf) -> buf.writeUtf(payload.version()),
		buf -> new ExpCollectorClientVersionPayload(buf.readUtf())
	);

	@Override
	public Type<ExpCollectorClientVersionPayload> type() {
		return TYPE;
	}
}
