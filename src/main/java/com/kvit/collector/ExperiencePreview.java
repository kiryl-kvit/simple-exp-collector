package com.kvit.collector;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public record ExperiencePreview(ExperienceState current, ExperienceState expected, long xpGranted) {
	public static ExperienceState capture(Player player) {
		return new ExperienceState(
			Math.max(0, player.experienceLevel),
			sanitizeProgress(player.experienceProgress)
		);
	}

	public static ExperiencePreview simulate(Player player, long xpGranted) {
		return simulate(capture(player), xpGranted);
	}

	public static ExperiencePreview simulate(ExperienceState current, long xpGranted) {
		return new ExperiencePreview(current, simulateGrant(current, xpGranted), Math.max(0L, xpGranted));
	}

	public int gainedLevels() {
		return Math.max(0, this.expected.level() - this.current.level());
	}

	public String currentDisplay() {
		return this.current.display();
	}

	public String expectedDisplay() {
		return this.expected.display();
	}

	private static ExperienceState simulateGrant(ExperienceState start, long xpGranted) {
		int level = Math.max(0, start.level());
		float progress = sanitizeProgress(start.progress());
		long remaining = Math.max(0L, xpGranted);

		while (remaining > 0L) {
			int portion = (int)Math.min(Integer.MAX_VALUE, remaining);
			progress = progress + (float)portion / xpNeededForLevel(level);

			while (progress >= 1.0F) {
				progress = (progress - 1.0F) * xpNeededForLevel(level);
				level++;
				progress = progress / xpNeededForLevel(level);
			}

			remaining -= portion;
		}

		return new ExperienceState(level, sanitizeProgress(progress));
	}

	private static float sanitizeProgress(float progress) {
		if (Float.isNaN(progress) || Float.isInfinite(progress)) {
			return 0.0F;
		}
		return Mth.clamp(progress, 0.0F, 0.999999F);
	}

	private static int xpNeededForLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	public record ExperienceState(int level, float progress) {
		public ExperienceState {
			level = Math.max(0, level);
			progress = sanitizeProgress(progress);
		}

		public int progressPercent() {
			return Math.min(99, Math.max(0, (int)Math.floor(this.progress * 100.0F + 0.0001F)));
		}

		public String display() {
			return "Lv " + this.level + " (" + this.progressPercent() + "%)";
		}
	}
}
