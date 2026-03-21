package com.kvit.collector;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class CollectorMessages {
	private CollectorMessages() {
	}

	public static MutableComponent namedComponent(String name) {
		return Component.literal("\"" + name + "\"")
			.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC);
	}

	public static MutableComponent collectorIdComponent(int id) {
		return Component.literal("#" + id).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	public static MutableComponent renameResult(int id, String name) {
		return Component.empty()
			.append(Component.literal(name.isEmpty() ? "Cleared name for collector #" + id : "Renamed collector #" + id + " to ")
				.withStyle(ChatFormatting.GREEN))
			.append(name.isEmpty() ? Component.empty() : namedComponent(name));
	}

	public static MutableComponent xpComponent(long xp) {
		return Component.literal(formatXp(xp)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
	}

	public static MutableComponent levelComponent(ExperiencePreview.ExperienceState state) {
		return Component.literal(state.display()).withStyle(ChatFormatting.AQUA);
	}

	public static MutableComponent levelTransitionComponent(ExperiencePreview preview) {
		return Component.empty()
			.append(levelComponent(preview.current()))
			.append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
			.append(levelComponent(preview.expected()));
	}

	public static MutableComponent collectionResult(int id, long xp, ExperiencePreview preview) {
		return collectionResult(collectorIdComponent(id), xp, preview);
	}

	public static MutableComponent collectionResult(Component collectorLabel, long xp, ExperiencePreview preview) {
		return Component.empty()
			.append(Component.literal("Collected ").withStyle(ChatFormatting.GREEN))
			.append(xpComponent(xp))
			.append(Component.literal(" XP from collector ").withStyle(ChatFormatting.GREEN))
			.append(collectorLabel.copy())
			.append(Component.literal(". ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal("Level: ").withStyle(ChatFormatting.GRAY))
			.append(levelTransitionComponent(preview));
	}

	public static String formatXp(long xp) {
		return String.format(Locale.ROOT, "%,d", xp);
	}
}
