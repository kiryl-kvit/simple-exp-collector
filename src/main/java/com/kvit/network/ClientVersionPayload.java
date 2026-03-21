package com.kvit.network;

import com.kvit.SimpleExpCollector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientVersionPayload(String version) implements CustomPacketPayload {
	public static final Type<ClientVersionPayload> TYPE = new Type<>(SimpleExpCollector.id("client_version"));
	public static final StreamCodec<FriendlyByteBuf, ClientVersionPayload> CODEC = CustomPacketPayload.codec(
		(payload, buf) -> buf.writeUtf(payload.version()),
		buf -> new ClientVersionPayload(buf.readUtf())
	);

	@Override
	public Type<ClientVersionPayload> type() {
		return TYPE;
	}
}
