package com.kvit.command;

import com.kvit.ModContent;
import com.kvit.collector.CollectorMessages;
import com.kvit.collector.ExperiencePreview;
import com.kvit.collector.ExpCollectorManager;
import com.kvit.collector.ExpCollectorManager.CollectorReference;
import com.kvit.menu.ExpCollectorMenu;
import com.kvit.menu.MenuComponents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ExpCommand {
	private static final int PAGE_SIZE = 8;
	private static final SuggestionProvider<CommandSourceStack> COLLECTOR_ID_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(
			ExpCollectorManager.getAllCollectors(context.getSource().getServer()).stream()
				.map(collector -> Integer.toString(collector.record().id())),
			builder
		);
	private static final SuggestionProvider<CommandSourceStack> COLLECTOR_TARGET_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(
			ExpCollectorManager.getAllCollectors(context.getSource().getServer()).stream()
				.flatMap(collector -> java.util.stream.Stream.of(
					Integer.toString(collector.record().id()),
					collector.record().name()
				))
				.filter(target -> !target.isBlank())
				.distinct(),
			builder
		);

	private ExpCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("exp")
				.then(
					Commands.literal("list")
						.executes(context -> listCollectors(context.getSource(), 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
							.executes(context -> listCollectors(context.getSource(), IntegerArgumentType.getInteger(context, "page"))))
				)
				.then(
					Commands.literal("rename")
						.then(Commands.argument("id", IntegerArgumentType.integer(1))
							.suggests(COLLECTOR_ID_SUGGESTIONS)
							.executes(context -> renameCollector(context.getSource(), IntegerArgumentType.getInteger(context, "id"), ""))
							.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(context -> renameCollector(
									context.getSource(),
									IntegerArgumentType.getInteger(context, "id"),
									StringArgumentType.getString(context, "name")
								))))
				)
				.then(
					Commands.literal("toggle-drop")
						.then(Commands.argument("target", StringArgumentType.greedyString())
							.suggests(COLLECTOR_TARGET_SUGGESTIONS)
							.executes(context -> toggleCollectorMobDrops(context.getSource(), StringArgumentType.getString(context, "target"))))
				)
				.then(
					Commands.literal("tp")
						.then(Commands.argument("target", StringArgumentType.greedyString())
							.suggests(COLLECTOR_TARGET_SUGGESTIONS)
							.executes(context -> teleportToCollector(context, StringArgumentType.getString(context, "target"))))
				)
				.then(
					Commands.literal("collect")
						.then(Commands.argument("input", StringArgumentType.greedyString())
							.suggests(COLLECTOR_TARGET_SUGGESTIONS)
							.executes(context -> collectFromCollectorInput(context, StringArgumentType.getString(context, "input"))))
				)
		);
	}

	private static int listCollectors(CommandSourceStack source, int page) {
		List<CollectorReference> collectors = ExpCollectorManager.getAllCollectors(source.getServer());
		if (collectors.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No exp collectors are placed yet.").withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}

		int totalPages = Math.max(1, Math.ceilDiv(collectors.size(), PAGE_SIZE));
		if (page > totalPages) {
			source.sendFailure(Component.literal("Only " + totalPages + " page(s) available.").withStyle(ChatFormatting.YELLOW));
			return 0;
		}

		int fromIndex = (page - 1) * PAGE_SIZE;
		int toIndex = Math.min(fromIndex + PAGE_SIZE, collectors.size());
		MutableComponent message = Component.empty();
		appendLine(message, Component.literal("Exp Collectors").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		appendLine(message, Component.literal(collectors.size() + " total  |  Page " + page + "/" + totalPages).withStyle(ChatFormatting.GRAY));

		for (CollectorReference collector : collectors.subList(fromIndex, toIndex)) {
			appendLine(message, formatCollectorLine(collector));
		}

		if (totalPages > 1) {
			appendLine(message, Component.literal("Use /exp list <page> to browse more.").withStyle(ChatFormatting.DARK_GRAY));
		}

		source.sendSuccess(() -> message, false);
		return toIndex - fromIndex;
	}

	private static int renameCollector(CommandSourceStack source, int id, String requestedName) {
		CollectorReference collector = resolveCollectorOrNotify(source, id).orElse(null);
		if (collector == null) {
			return 0;
		}

		String name = ExpCollectorManager.normalizeName(requestedName);
		if (collector.record().name().equals(name)) {
			source.sendSuccess(() -> Component.empty()
				.append(Component.literal(name.isEmpty() ? "Collector #" + id + " already has no custom name." : "Collector #" + id + " is already named ").withStyle(ChatFormatting.YELLOW))
				.append(name.isEmpty() ? Component.empty() : CollectorMessages.namedComponent(name)), false);
			return 1;
		}

		boolean changed = ExpCollectorManager.rename(collector.level(), collector.blockPos(), name);
		if (!changed) {
			source.sendFailure(Component.literal("Failed to rename collector #" + id + ".").withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> CollectorMessages.renameResult(id, name), false);
		return 1;
	}

	private static int toggleCollectorMobDrops(CommandSourceStack source, String target) {
		CollectorReference collector = resolveCollectorTargetOrNotify(source, target).orElse(null);
		if (collector == null) {
			return 0;
		}

		Optional<com.kvit.data.ExpCollectorRecord> updated = ExpCollectorManager.toggleMobDrops(collector.level(), collector.blockPos());
		if (updated.isEmpty()) {
			source.sendFailure(Component.literal("Failed to toggle mob drops for that collector.").withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> CollectorMessages.mobDropsToggleResult(collectorLabelComponent(collector), updated.get().mobDropsDisabled()), false);
		return 1;
	}

	private static int teleportToCollector(CommandContext<CommandSourceStack> context, String target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		CollectorReference collector = resolveCollectorTargetOrNotify(source, target).orElse(null);
		if (collector == null) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		Optional<Vec3> teleportTarget = findTeleportTarget(collector.level(), collector.blockPos());
		if (teleportTarget.isEmpty()) {
			source.sendFailure(Component.empty()
				.append(Component.literal("Could not find a safe spot next to collector ").withStyle(ChatFormatting.RED))
				.append(collectorLabelComponent(collector))
				.append(Component.literal(".").withStyle(ChatFormatting.RED)));
			return 0;
		}

		Vec3 position = teleportTarget.get();
		boolean teleported = player.teleportTo(
			collector.level(),
			position.x,
			position.y,
			position.z,
			Set.<Relative>of(),
			player.getYRot(),
			player.getXRot(),
			true
		);
		if (!teleported) {
			source.sendFailure(Component.empty()
				.append(Component.literal("Teleport to collector ").withStyle(ChatFormatting.RED))
				.append(collectorLabelComponent(collector))
				.append(Component.literal(" failed.").withStyle(ChatFormatting.RED)));
			return 0;
		}

		source.sendSuccess(() -> Component.empty()
			.append(Component.literal("Teleported to collector ").withStyle(ChatFormatting.GREEN))
			.append(collectorLabelComponent(collector))
			.append(Component.literal(" ").withStyle(ChatFormatting.GREEN))
			.append(locationComponent(collector)), false);
		return 1;
	}

	private static int collectFromCollectorInput(CommandContext<CommandSourceStack> context, String input) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ParsedCollectRequest request = parseCollectRequest(context.getSource(), input).orElse(null);
		if (request == null) {
			return 0;
		}
		return collectFromCollector(context, request.target(), request.requestedAmount());
	}

	private static int collectFromCollector(CommandContext<CommandSourceStack> context, String target, Long requestedAmount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		CollectorReference collector = resolveCollectorTargetOrNotify(source, target).orElse(null);
		if (collector == null) {
			return 0;
		}

		ServerPlayer player = source.getPlayerOrException();
		long storedXp = collector.record().storedXp();
		if (storedXp <= 0L) {
			source.sendSuccess(() -> Component.empty()
				.append(Component.literal("Collector ").withStyle(ChatFormatting.YELLOW))
				.append(collectorLabelComponent(collector))
				.append(Component.literal(" is empty.").withStyle(ChatFormatting.YELLOW)), false);
			return 1;
		}

		long amount = requestedAmount == null ? storedXp : requestedAmount;
		ExperiencePreview preview = ExperiencePreview.simulate(player, Math.min(amount, storedXp));
		long collected = ExpCollectorManager.collectToPlayer(collector.level(), collector.blockPos(), player, amount);
		if (collected <= 0L) {
			source.sendFailure(Component.literal("No experience could be collected.").withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendSuccess(() -> CollectorMessages.collectionResult(collectorLabelComponent(collector), collected, preview), false);
		return 1;
	}

	private static Optional<ParsedCollectRequest> parseCollectRequest(CommandSourceStack source, String input) {
		String trimmed = input == null ? "" : input.trim();
		if (trimmed.isEmpty()) {
			source.sendFailure(Component.literal("Collector target must not be blank.").withStyle(ChatFormatting.RED));
			return Optional.empty();
		}

		int splitIndex = trimmed.lastIndexOf(' ');
		if (splitIndex > 0) {
			String amountPart = trimmed.substring(splitIndex + 1).trim();
			Long parsedAmount = tryParseAmount(amountPart);
			if (parsedAmount != null && parsedAmount > 0L) {
				String targetPart = trimmed.substring(0, splitIndex).trim();
				if (!targetPart.isEmpty() && resolveCollectorTarget(source, targetPart).isPresent()) {
					return Optional.of(new ParsedCollectRequest(targetPart, parsedAmount));
				}
			}
		}

		return Optional.of(new ParsedCollectRequest(trimmed, null));
	}

	private static Long tryParseAmount(String input) {
		if (input.isEmpty()) {
			return null;
		}
		for (int i = 0; i < input.length(); i++) {
			if (!Character.isDigit(input.charAt(i))) {
				return null;
			}
		}
		try {
			return Long.parseLong(input);
		} catch (NumberFormatException exception) {
			return Long.MAX_VALUE;
		}
	}

	private static Optional<CollectorReference> resolveCollectorTargetOrNotify(CommandSourceStack source, String target) {
		String normalized = ExpCollectorManager.normalizeName(target);
		if (normalized.isEmpty()) {
			source.sendFailure(Component.literal("Collector target must not be blank.").withStyle(ChatFormatting.RED));
			return Optional.empty();
		}

		List<CollectorReference> namedMatches = resolveNamedCollectors(source, normalized);
		if (!namedMatches.isEmpty()) {
			if (namedMatches.size() > 1) {
				source.sendFailure(Component.empty()
					.append(Component.literal("Multiple exp collectors are named ").withStyle(ChatFormatting.RED))
					.append(CollectorMessages.namedComponent(normalized))
					.append(Component.literal(". Use /exp list to pick its id: ").withStyle(ChatFormatting.RED))
					.append(namedMatchIdsComponent(namedMatches)));
				return Optional.empty();
			}
			return Optional.of(namedMatches.get(0));
		}

		try {
			int id = Integer.parseInt(normalized);
			return resolveCollectorOrNotify(source, id);
		} catch (NumberFormatException ignored) {
			String lowered = normalized.toLowerCase(Locale.ROOT);
			source.sendFailure(Component.empty()
				.append(Component.literal("No exp collector named ").withStyle(ChatFormatting.RED))
				.append(CollectorMessages.namedComponent(normalized))
				.append(Component.literal(" was found.").withStyle(ChatFormatting.RED))
				.append(suggestSimilarNames(source, lowered)));
			return Optional.empty();
		}
	}

	private static Optional<CollectorReference> resolveCollectorTarget(CommandSourceStack source, String target) {
		String normalized = ExpCollectorManager.normalizeName(target);
		if (normalized.isEmpty()) {
			return Optional.empty();
		}

		List<CollectorReference> namedMatches = resolveNamedCollectors(source, normalized);
		if (namedMatches.size() == 1) {
			return Optional.of(namedMatches.get(0));
		}
		if (!namedMatches.isEmpty()) {
			return Optional.empty();
		}

		try {
			return resolveCollector(source, Integer.parseInt(normalized));
		} catch (NumberFormatException ignored) {
			return Optional.empty();
		}
	}

	private static Optional<CollectorReference> resolveCollectorOrNotify(CommandSourceStack source, int id) {
		Optional<CollectorReference> collector = resolveCollector(source, id);
		if (collector.isPresent()) {
			return collector;
		}

		source.sendFailure(Component.literal("No exp collector with id #" + id + " was found.").withStyle(ChatFormatting.RED));
		return Optional.empty();
	}

	private static Optional<CollectorReference> resolveCollector(CommandSourceStack source, int id) {
		Optional<CollectorReference> optionalCollector = ExpCollectorManager.getCollector(source.getServer(), id);
		if (optionalCollector.isEmpty()) {
			return Optional.empty();
		}

		CollectorReference collector = optionalCollector.get();
		if (collector.level().isLoaded(collector.blockPos()) && !collector.level().getBlockState(collector.blockPos()).is(ModContent.expCollector())) {
			ExpCollectorManager.remove(collector.level(), collector.blockPos());
			return Optional.empty();
		}

		return Optional.of(collector);
	}

	private static List<CollectorReference> resolveNamedCollectors(CommandSourceStack source, String name) {
		return ExpCollectorManager.getCollectorsByName(source.getServer(), name).stream()
			.filter(collector -> resolveCollector(source, collector.record().id()).isPresent())
			.toList();
	}

	private static MutableComponent formatCollectorLine(CollectorReference collector) {
		return Component.empty()
			.append(Component.literal("#" + collector.record().id()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.append(formatOptionalName(collector))
			.append(Component.literal("  "))
			.append(Component.literal(displayDimension(collector.level())).withStyle(ChatFormatting.AQUA))
			.append(Component.literal("  "))
			.append(Component.literal(formatCoordinates(collector.blockPos())).withStyle(ChatFormatting.GRAY))
			.append(Component.literal("  "))
			.append(Component.literal("XP " + ExpCollectorMenu.formatXp(collector.record().storedXp())).withStyle(ChatFormatting.GREEN));
	}

	private static Component formatOptionalName(CollectorReference collector) {
		if (!collector.record().hasName()) {
			return Component.empty();
		}

		return Component.empty()
			.append(Component.literal(" "))
			.append(CollectorMessages.namedComponent(collector.record().name()));
	}

	private static Component collectorLabelComponent(CollectorReference collector) {
		MutableComponent label = Component.literal("#" + collector.record().id()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		if (!collector.record().hasName()) {
			return label;
		}

		return label.append(Component.literal(" ")).append(CollectorMessages.namedComponent(collector.record().name()));
	}

	private static Component namedMatchIdsComponent(List<CollectorReference> matches) {
		MutableComponent component = Component.empty();
		for (int index = 0; index < matches.size(); index++) {
			if (index > 0) {
				component.append(Component.literal(", ").withStyle(ChatFormatting.RED));
			}
			component.append(Component.literal("#" + matches.get(index).record().id()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		return component;
	}

	private static Component suggestSimilarNames(CommandSourceStack source, String loweredTarget) {
		List<String> similarNames = ExpCollectorManager.getAllCollectors(source.getServer()).stream()
			.map(collector -> collector.record().name())
			.filter(name -> !name.isBlank())
			.distinct()
			.filter(name -> name.toLowerCase(Locale.ROOT).contains(loweredTarget))
			.limit(3)
			.toList();
		if (similarNames.isEmpty()) {
			return Component.empty();
		}

		MutableComponent component = Component.literal(" Similar names: ").withStyle(ChatFormatting.GRAY);
		for (int index = 0; index < similarNames.size(); index++) {
			if (index > 0) {
				component.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			}
			component.append(CollectorMessages.namedComponent(similarNames.get(index)));
		}
		return component;
	}

	private static MutableComponent locationComponent(CollectorReference collector) {
		return Component.empty()
			.append(Component.literal("at ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(displayDimension(collector.level())).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(formatCoordinates(collector.blockPos())).withStyle(ChatFormatting.GRAY));
	}

	private static Optional<Vec3> findTeleportTarget(ServerLevel level, BlockPos pos) {
		for (int verticalOffset : new int[]{1, 0, 2, -1, 3}) {
			Vec3 safe = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, pos.offset(0, verticalOffset, 0), true);
			if (safe != null) {
				return Optional.of(safe);
			}
		}

		for (int radius = 1; radius <= 4; radius++) {
			for (int verticalOffset : new int[]{0, 1, -1, 2, 3}) {
				for (int xOffset = -radius; xOffset <= radius; xOffset++) {
					for (int zOffset = -radius; zOffset <= radius; zOffset++) {
						if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
							continue;
						}

						BlockPos candidate = pos.offset(xOffset, verticalOffset, zOffset);
						Vec3 safe = DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, candidate, true);
						if (safe != null) {
							return Optional.of(safe);
						}
					}
				}
			}
		}

		return Optional.empty();
	}

	private static String displayDimension(ServerLevel level) {
		String id = level.dimension().identifier().toString();
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> id;
		};
	}

	private static String formatCoordinates(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private static void appendLine(MutableComponent message, Component line) {
		if (!message.getSiblings().isEmpty() || message.getString().length() > 0) {
			message.append(Component.literal("\n"));
		}
		message.append(MenuComponents.plain(line));
	}

	private record ParsedCollectRequest(String target, Long requestedAmount) {
	}
}
