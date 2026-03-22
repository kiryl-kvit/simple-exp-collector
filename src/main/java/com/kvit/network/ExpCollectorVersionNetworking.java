package com.kvit.network;

import com.kvit.SimpleExpCollector;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

public final class ExpCollectorVersionNetworking {
	private ExpCollectorVersionNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ExpCollectorClientVersionPayload.TYPE, ExpCollectorClientVersionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExpCollectorClientVersionPayload.TYPE, (payload, context) -> {
			String serverVersion = SimpleExpCollector.getModVersion();
			String clientVersion = payload.version();

			if (serverVersion.equals(clientVersion)) {
				return;
			}

			context.player().sendSystemMessage(Component.literal(buildMismatchMessage(serverVersion, clientVersion)), false);
		});
	}

	private static String buildMismatchMessage(String serverVersion, String clientVersion) {
		return "Your version of " + SimpleExpCollector.MOD_ID + " does not match one installed on the server, this could lead to issues with the mod's client-side. Please, install matching version.\n"
			+ "Server version - " + serverVersion + "\n"
			+ "Client version - " + clientVersion;
	}
}
