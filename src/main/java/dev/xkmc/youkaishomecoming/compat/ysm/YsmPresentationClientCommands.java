package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Read-only catalogs. Clicks suggest server commands; they never bypass server permissions or selectors. */
final class YsmPresentationClientCommands {

	private static final int PAGE_SIZE = 8;

	private YsmPresentationClientCommands() { }

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, ArgumentType<String> targets,
			SuggestionProvider<CommandSourceStack> suggestions) {
		YsmCommandSuggestions.client(YsmPresentationClientCommands::nativeSuggestions);
		dispatcher.register(Commands.literal("yhysm")
				.then(Commands.literal("inspect")
						.executes(ctx -> inspect(ctx, YSMClientCompat.getPointedEntityOrSelected()))
						.then(Commands.argument("entities", targets).suggests(suggestions)
								.executes(ctx -> inspect(ctx, YSMClientCompat.getFirstResolvedEntity(ctx)))))
				.then(Commands.literal("anim").then(list("list", targets, suggestions, YsmPresentationClientCommands::animations)))
				.then(list("wheel", targets, suggestions, YsmPresentationClientCommands::wheel))
				.then(Commands.literal("param")
						.then(list("list", targets, suggestions, YsmPresentationClientCommands::parameters))
						.then(Commands.literal("get").then(Commands.argument("entities", targets).suggests(suggestions)
								.then(Commands.argument("parameter", StringArgumentType.string())
										.suggests((ctx, builder) -> nativeSuggestions(YsmCommandSuggestions.Kind.PARAMETER, ctx, builder))
										.executes(YsmPresentationClientCommands::parameterValue))))));
	}

	/** Invoked by the named provider on the actual server command node, not a shadow client command. */
	private static <S extends SharedSuggestionProvider> CompletableFuture<Suggestions> nativeSuggestions(
			YsmCommandSuggestions.Kind kind, CommandContext<S> ctx, SuggestionsBuilder builder) {
		var mc = Minecraft.getInstance();
		if (kind == YsmCommandSuggestions.Kind.TARGETS) {
			// Suggestions.merge sorts lexicographically, which made UUID candidates appear
			// in 1-9/a-z order. Build the client candidates directly in distance order so
			// the nearest entity is offered first, matching the editor's entity picker.
			String remaining = builder.getRemainingLowerCase();
			if (remaining.startsWith("@")) {
				return EntityArgument.entities().listSuggestions(ctx, builder);
			}
			List<Entity> entities = new ArrayList<>();
			if (mc.level != null) {
				for (Entity entity : mc.level.entitiesForRendering())
					if (entity instanceof YsmRenderOverrideTarget) entities.add(entity);
				if (mc.player != null && mc.player instanceof YsmRenderOverrideTarget && !entities.contains(mc.player))
					entities.add(mc.player);
			}
			if (mc.player != null) entities.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)));
			StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
			List<Suggestion> ordered = new ArrayList<>();
			for (Entity entity : entities) {
				String uuid = entity.getUUID().toString();
				if (!uuid.startsWith(remaining)) continue;
				String distance = mc.player == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f", entity.distanceTo(mc.player));
				ordered.add(new Suggestion(range, uuid,
						Component.translatable("commands.youkaishomecoming.model.entity_candidate",
								net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), distance)));
			}
			if (!remaining.isEmpty()) return CompletableFuture.completedFuture(new Suggestions(range, ordered));
			return EntityArgument.entities().listSuggestions(ctx, new SuggestionsBuilder(builder.getInput(), builder.getStart()))
					.thenApply(vanilla -> {
						var seen = new java.util.HashSet<String>();
						ordered.forEach(suggestion -> seen.add(suggestion.getText()));
						for (Suggestion suggestion : vanilla.getList())
							if (seen.add(suggestion.getText())) ordered.add(suggestion);
						return new Suggestions(range, ordered);
					});
		}
		String targetText = ctx.getNodes().stream().filter(node -> node.getNode().getName().equals("targets") || node.getNode().getName().equals("entities"))
				.map(node -> node.getRange().get(ctx.getInput())).findFirst().orElse("");
		var targets = targetText.isEmpty() ? List.<Entity>of() : YSMClientCompat.entitiesForSuggestions(targetText);
		var models = new TreeSet<String>();
		String requestedModel = YsmCommandSuggestions.argument(ctx, "model");
		if (!requestedModel.isEmpty()) models.add(requestedModel);
		else for (var entity : targets) if (entity instanceof LivingEntity living) {
			String model = YSMClientCompat.effectiveModel(living);
			if (!model.isEmpty()) models.add(model);
		}
		if (models.isEmpty() || kind == YsmCommandSuggestions.Kind.MODEL) {
			models.addAll(YSMClientCompat.loadedModelIds());
			models.addAll(YsmClientProfiles.models());
		}
		Map<String, Component> values = new TreeMap<>();
		String parameter = YsmCommandSuggestions.argument(ctx, "parameter");
		try { parameter = YsmPresentationState.normalizeParameter(parameter); }
		catch (IllegalArgumentException ignored) { }
		for (String model : models) {
			var catalog = YsmClientPresentationBridge.catalog(model);
			if (kind == YsmCommandSuggestions.Kind.MODEL) values.put(model, Component.literal(model));
			if (kind == YsmCommandSuggestions.Kind.CLIP) for (String clip : catalog.animations()) {
				String label = catalog.wheel().stream().filter(entry -> entry.id().equals(clip)).map(YsmModelCatalog.WheelEntry::label).findFirst().orElse(clip);
				values.put(clip, Component.literal(label));
			}
			for (var control : catalog.controls()) {
				if (kind == YsmCommandSuggestions.Kind.PARAMETER && !control.parameter().isEmpty())
					values.put(control.parameter(), Component.literal(control.title()));
				if (kind != YsmCommandSuggestions.Kind.VALUE || !control.parameter().equals(parameter)) continue;
				if (control.type().equals("checkbox")) {
					values.put("0", Component.translatable("options.off"));
					values.put("1", Component.translatable("options.on"));
				} else if (control.type().equals("radio")) {
					for (var choice : control.choices()) if (choice.numericValue() != null)
						values.put(Float.toString(choice.numericValue()), Component.literal(choice.label()));
				} else if (control.type().equals("range") && Double.isFinite(control.min()) && Double.isFinite(control.max())) {
					values.put(Double.toString(control.min()), Component.literal(control.title()));
					values.put(Double.toString(control.max()), Component.literal(control.title()));
				}
			}
			for (var entry : YsmClientProfiles.entry(model).profile().presets().entrySet()) {
				var preset = entry.getValue();
				if (kind == YsmCommandSuggestions.Kind.PRESET) values.put(entry.getKey(), Component.literal(preset.description()));
				if (kind == YsmCommandSuggestions.Kind.CLIP && !preset.clip().isEmpty()) values.putIfAbsent(preset.clip(), Component.literal(preset.description()));
				if (kind == YsmCommandSuggestions.Kind.PARAMETER) preset.parameters().keySet().forEach(name -> values.putIfAbsent(name, Component.literal(preset.description())));
			}
		}
		if (kind == YsmCommandSuggestions.Kind.PARAMETER) for (var entity : targets) if (entity instanceof YsmRenderOverrideTarget target)
			target.getYsmPresentation().parameters().keySet().forEach(name -> values.putIfAbsent(name, entity.getDisplayName()));
		values.forEach((value, label) -> {
			String escaped = StringArgumentType.escapeIfRequired(value);
			if (escaped.toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase())) builder.suggest(escaped, label);
		});
		return builder.buildFuture();
	}

	private static LiteralArgumentBuilder<CommandSourceStack> list(String literal, ArgumentType<String> targets,
			SuggestionProvider<CommandSourceStack> suggestions,
			BiFunction<LivingEntity, YsmModelCatalog, List<Component>> lines) {
		return Commands.literal(literal)
				.executes(ctx -> show(ctx, YSMClientCompat.getPointedEntityOrSelected(), 1, lines))
				.then(Commands.argument("entities", targets).suggests(suggestions)
						.executes(ctx -> show(ctx, YSMClientCompat.getFirstResolvedEntity(ctx), 1, lines))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
								.executes(ctx -> show(ctx, YSMClientCompat.getFirstResolvedEntity(ctx), IntegerArgumentType.getInteger(ctx, "page"), lines))));
	}

	private static int inspect(CommandContext<CommandSourceStack> ctx, Entity entity) {
		if (!(entity instanceof LivingEntity living)) return noTarget(ctx);
		YSMClientCompat.RenderRequest request = YSMClientCompat.resolveRenderRequest(living);
		if (request == null) return noBinding(ctx);
		var catalog = YsmClientPresentationBridge.catalog(request.modelId());
		ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.inspect",
				living.getDisplayName(), request.modelId(), request.textureName()));
		ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.catalog",
				Component.translatable("commands.youkaishomecoming.model.catalog." + catalog.status().name().toLowerCase(java.util.Locale.ROOT)),
				catalog.animations().size(), catalog.wheel().size(), catalog.controls().size()));
		if (!catalog.detail().isEmpty()) ctx.getSource().sendSystemMessage(Component.literal(catalog.detail()));
		if (living instanceof YsmRenderOverrideTarget target) {
			ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.request",
					YHModel.getAnimation(target), YHModel.getAnimationTicksRemaining(target), YSMClientCompat.isBeatenProjection(living)));
			ctx.getSource().sendSystemMessage(Component.literal("signals: " + target.getYsmSignals().toTag()));
			ctx.getSource().sendSystemMessage(Component.literal("composition: " + request.presentation()));
		}
		var nativeState = YSMClientCompat.getYsmDebugSnapshot(living);
		ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.resolved",
				nativeState.getOrDefault("special.resolved", ""), nativeState.getOrDefault("controller.player.cap", "")));
		YsmClientPresentationBridge.diagnostics(living).forEach((key, value) ->
				ctx.getSource().sendSystemMessage(Component.literal(key + ": " + value)));
		ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.catalog_help"));
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> ctx, Entity entity, int page,
			BiFunction<LivingEntity, YsmModelCatalog, List<Component>> lines) {
		if (!(entity instanceof LivingEntity living)) return noTarget(ctx);
		YSMClientCompat.RenderRequest request = YSMClientCompat.resolveRenderRequest(living);
		if (request == null) return noBinding(ctx);
		var catalog = YsmClientPresentationBridge.catalog(request.modelId());
		if (catalog.status() != YsmModelCatalog.Status.READY) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.catalog." + catalog.status().name().toLowerCase(java.util.Locale.ROOT)));
			return 0;
		}
		List<Component> entries = lines.apply(living, catalog);
		int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int selected = Math.min(page, pages);
		ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.page", request.modelId(), selected, pages, entries.size()));
		if (!catalog.detail().isEmpty()) ctx.getSource().sendSystemMessage(Component.literal(catalog.detail()));
		for (int i = (selected - 1) * PAGE_SIZE; i < Math.min(entries.size(), selected * PAGE_SIZE); i++) {
			ctx.getSource().sendSystemMessage(entries.get(i));
		}
		return entries.size();
	}

	private static List<Component> animations(LivingEntity entity, YsmModelCatalog catalog) {
		List<Component> lines = new ArrayList<>();
		for (String clip : catalog.animations()) {
			String labels = catalog.wheel().stream().filter(entry -> entry.id().equals(clip) && entry.configGroup().isEmpty())
					.map(YsmModelCatalog.WheelEntry::label).distinct().reduce((a, b) -> a + " / " + b).orElse("");
			lines.add(suggest(Component.literal(clip + (labels.isEmpty() || labels.equals(clip) ? "" : " — " + labels)),
					"/yhysm anim play " + entity.getUUID() + " " + StringArgumentType.escapeIfRequired(clip)));
		}
		return lines;
	}

	private static List<Component> wheel(LivingEntity entity, YsmModelCatalog catalog) {
		List<Component> lines = new ArrayList<>();
		for (var entry : catalog.wheel()) {
			MutableComponent line = Component.literal((entry.group().isEmpty() ? "" : entry.group() + " / ") + entry.id() + " — " + entry.label());
			if (!entry.submenu().isEmpty()) line.append(Component.translatable("commands.youkaishomecoming.model.submenu", entry.submenu()));
			if (!entry.configGroup().isEmpty()) line.append(Component.translatable("commands.youkaishomecoming.model.config_group", entry.configGroup()));
			if (entry.clipAvailable()) suggest(line, "/yhysm anim play " + entity.getUUID() + " " + StringArgumentType.escapeIfRequired(entry.id()));
			else line.append(Component.translatable("commands.youkaishomecoming.model.no_clip"));
			lines.add(line);
		}
		return lines;
	}

	private static List<Component> parameters(LivingEntity entity, YsmModelCatalog catalog) {
		List<Component> lines = new ArrayList<>();
		for (var control : catalog.controls()) {
			String group = control.groupLabel().isEmpty() ? control.group() : control.groupLabel();
			MutableComponent line = Component.literal(group + " / " + control.title() + " — " + control.expression() + " [" + control.type() + "]");
			if (!control.description().isEmpty()) line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(control.description()))));
			String prefix = "/yhysm param set " + entity.getUUID() + " " + control.parameter() + " ";
			if (control.type().equals("checkbox") && !control.parameter().isEmpty()) {
				line.append(" ").append(suggest(Component.literal("0"), prefix + "0"));
				line.append(" / ").append(suggest(Component.literal("1"), prefix + "1"));
			} else if (control.type().equals("range") && !control.parameter().isEmpty()) {
				line.append(" ").append(suggest(Component.literal(control.min() + " .. " + control.max() + " (step " + control.step() + ")"), prefix + control.min()));
			} else {
				for (var choice : control.choices()) {
					MutableComponent option = Component.literal(choice.label() + "=" + (choice.numericValue() == null ? choice.expression() : choice.numericValue()));
					if (choice.numericValue() != null) suggest(option, prefix + choice.numericValue());
					else option.withStyle(ChatFormatting.GRAY);
					line.append(" | ").append(option);
				}
			}
			lines.add(line);
		}
		return lines;
	}

	private static int parameterValue(CommandContext<CommandSourceStack> ctx) {
		Entity entity = YSMClientCompat.getFirstResolvedEntity(ctx);
		if (!(entity instanceof LivingEntity living)) return noTarget(ctx);
		try {
			String name = YsmPresentationState.normalizeParameter(StringArgumentType.getString(ctx, "parameter"));
			Object base = YsmClientPresentationBridge.parameterBaseValue(living, name);
			Float requested = living instanceof YsmRenderOverrideTarget target ? YHModel.getParameter(target, name) : null;
			ctx.getSource().sendSystemMessage(Component.translatable("commands.youkaishomecoming.model.parameter_value", name, String.valueOf(base), String.valueOf(requested)));
			return 1;
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.invalid", ex.getMessage()));
			return 0;
		}
	}

	private static MutableComponent suggest(MutableComponent label, String command) {
		return label.withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
	}

	private static int noTarget(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.no_client_target"));
		return 0;
	}

	private static int noBinding(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.no_binding"));
		return 0;
	}
}
