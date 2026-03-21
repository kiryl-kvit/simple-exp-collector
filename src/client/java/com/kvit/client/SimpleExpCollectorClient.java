package com.kvit.client;

import com.kvit.SimpleExpCollector;
import com.kvit.network.ClientVersionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleExpCollectorClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleExpCollector.MOD_ID);

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (ClientPlayNetworking.canSend(ClientVersionPayload.TYPE)) {
				ClientPlayNetworking.send(new ClientVersionPayload(SimpleExpCollector.getModVersion()));
			}
		});

		LOGGER.info("{} client initialized", SimpleExpCollector.MOD_ID);
	}
}
