package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig.RenderBinding;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import rip.ysm.api.client.ExternalLivingRenderAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YSMClientCompat {
	private static final double UUID_SUGGESTION_RANGE_SQR = 16 * 16;

	private static final String MOD_ID = "yes_steve_model";
	private static final String MODEL_REMILIA = "YH内置/remilia";
	private static final String TEXTURE_DEFAULT = "default";
	private static final ArgumentType<String> YSM_ID_ARGUMENT = new TokenArgument("Expected YSM id", List.of(MODEL_REMILIA, "namespace:path/model"));
	private static final ArgumentType<String> ENTITY_TARGET_ARGUMENT = new TokenArgument("Expected entity target", List.of("@e[limit=1,sort=nearest]", "@a", "00000000-0000-0000-0000-000000000000"));
	private static final ArgumentType<String> BINDING_TARGET_ARGUMENT = new TokenArgument("Expected entity type, UUID or selector", List.of("youkaishomecoming:remilia_scarlet", "@e[limit=1,sort=nearest]", "00000000-0000-0000-0000-000000000000"));
	private static final boolean LOADED = ModList.get().isLoaded(MOD_ID) && YsmOpenYsmSupport.available();
	private static final Map<ResourceLocation, RenderBinding> TYPE_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final Map<UUID, RenderBinding> ENTITY_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static long bindingRevision;

	public static long bindingRevision() { return bindingRevision; }
	static void setBindingRevision(long revision) { bindingRevision = revision; }
	public static Map<ResourceLocation, RenderBinding> typeBindings() { return Map.copyOf(TYPE_DEBUG_OVERRIDES); }
	public static Map<UUID, RenderBinding> entityBindings() { return Map.copyOf(ENTITY_DEBUG_OVERRIDES); }
	public static String effectiveModel(LivingEntity entity) {
		var request = resolveRenderRequest(entity);
		return request == null ? "" : request.modelId();
	}
	public static String bindingSource(LivingEntity entity) { return resolveBindingWithSource(entity).source(); }
	private static final int DEBUG_TEXT_COLOR = 0xffffffff;
	private static final int DEBUG_LABEL_COLOR = 0xffb8e6ff;
	private static final int DEBUG_BG_A = 0xa0000000;
	private static final int DEBUG_BG_B = 0x90000000;
	private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(YsmEditorCatalog.modelIds(), builder);
	private static final SuggestionProvider<CommandSourceStack> TARGET_ENTITY_SUGGESTIONS = (ctx, builder) -> {
		Entity pointed = getPointedEntity();
		if (pointed != null) {
			builder.suggest(pointed.getUUID().toString(), Component.literal("pointed entity"));
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSourceStack> BINDING_TARGET_SUGGESTIONS = YSMClientCompat::bindingTargetSuggestions;

	private static int delegatedRenderDepth;
	private static boolean debugOverlay;
	private static UUID debugTarget;
	private static final long CATALOG_CACHE_NANOS = 500_000_000L;
	private static long loadedModelIdsCacheAt;
	private static List<String> loadedModelIdsCache = List.of();
	private static final Map<String, CachedList> textureNameCache = new LinkedHashMap<>();
	private static final Map<String, CachedList> animationNameCache = new LinkedHashMap<>();
	private static final Map<String, CachedString> defaultTextureCache = new LinkedHashMap<>();

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean delegateRender(LivingEntity e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		if (delegatedRenderDepth > 0) {
			return false;
		}
		YsmClientPresentationBridge.renderOutcome(e, false);
		RenderRequest request = resolveRenderRequest(e);
		if (!LOADED || request == null) {
			return false;
		}
		delegatedRenderDepth++;
		try (var frame = YsmClientPresentationBridge.beforeRender(e, request.modelId(), request.presentation())) {
			boolean rendered = ExternalLivingRenderAPI.render(e, request.modelId(), request.textureName(), request.animationHint(), yaw, pTick, pose, buffer, light);
			YsmClientPresentationBridge.renderOutcome(e, rendered);
			return rendered;
		} finally {
			delegatedRenderDepth--;
		}
	}

	public static boolean isDelegatingRender() {
		return delegatedRenderDepth > 0;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void renderMappedLivingEntity(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();
		// This event fires before the entity renderer and can bypass
		// GeneralYoukaiRenderer's invisible guard.
		if (entity.isInvisible()) {
			return;
		}
		float yaw = Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
		if (delegateRender(entity, yaw, event.getPartialTick(), event.getPoseStack(),
				event.getMultiBufferSource(), event.getPackedLight())) {
			event.setCanceled(true);
		}
	}

	private static void clearYsmDebug() {
		if (LOADED) ExternalLivingRenderAPI.clearDebug();
	}

	static Map<String, String> getYsmDebugSnapshot(LivingEntity entity) {
		if (!LOADED) {
			return Map.of("loaded", "false");
		}
		return ExternalLivingRenderAPI.getDebugSnapshot(entity);
	}

	public static List<String> loadedModelIds() {
		long now = System.nanoTime();
		if (isCacheFresh(loadedModelIdsCacheAt, now)) {
			return new ArrayList<>(loadedModelIdsCache);
		}
		// Do not advertise the YH built-in model when OpenYSM is absent. Callers
		// use this list for live editor/command choices and an unavailable ID would
		// only create a guaranteed fallback path.
		List<String> result = LOADED ? new ArrayList<>(List.of(MODEL_REMILIA)) : new ArrayList<>();
		if (!LOADED) {
			loadedModelIdsCache = List.copyOf(result);
			loadedModelIdsCacheAt = now;
			return result;
		}
		for (String id : ExternalLivingRenderAPI.getLoadedModelIds()) if (!id.isBlank() && !result.contains(id)) result.add(id);
		loadedModelIdsCache = List.copyOf(result);
		loadedModelIdsCacheAt = now;
		return result;
	}

	static RenderBinding resolveBinding(LivingEntity e) {
		BindingResolution resolution = resolveBindingWithSource(e);
		RenderBinding binding = resolution.binding();
		return binding != null && binding.enabled() ? binding : null;
	}

	static RenderRequest resolveRenderRequest(LivingEntity e) {
		BindingResolution resolution = resolveBindingWithSource(e);
		RenderBinding binding = resolution.binding();
		if (binding != null && !binding.enabled()) {
			return null;
		}
		String modelOverride = e instanceof YsmRenderOverrideTarget target ? target.getYsmModelOverride() : "";
		String textureOverride = e instanceof YsmRenderOverrideTarget target ? target.getYsmTextureOverride() : "";
		String modelId = !modelOverride.isBlank() ? modelOverride : binding == null ? "" : binding.modelId();
		if (modelId.isBlank()) {
			return null;
		}
		String textureName = !textureOverride.isBlank() ? textureOverride :
				!modelOverride.isBlank() ? TEXTURE_DEFAULT :
						binding == null ? TEXTURE_DEFAULT : binding.textureName();
		var presentation = YsmClientProfiles.resolve(e, modelId);
		return new RenderRequest(modelId, textureName, selectAnimation(e, modelId, presentation), presentation);
	}

	private static BindingResolution resolveBindingWithSource(LivingEntity e) {
		RenderBinding entityOverride = ENTITY_DEBUG_OVERRIDES.get(e.getUUID());
		if (entityOverride != null) {
			return new BindingResolution(entityOverride, "entity override");
		}
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
		if (entityId == null) {
			return new BindingResolution(null, "unknown type");
		}
		RenderBinding binding = YSMCompatConfig.defaultBinding(entityId);
		return new BindingResolution(binding, binding == null ? "none" : "default");
	}

	private static String selectAnimation(LivingEntity e, String modelId, YsmPresentationResolver.Resolved presentation) {
		String mappedBeaten = presentation.beaten() && presentation.body() != null ? presentation.body().clip() + "+" : "";
		if (e instanceof YoukaiEntity youkai && youkai.isBeaten()) {
			// Route beaten poses through the special= group: the base-predicate keys
			// (defeat/falling/climbing) are only consumed when OpenYSM's base controller runs,
			// which model state machines can replace. The cap controller resolves special=
			// via resolveSpecialAnimationHint with a + fallback chain, so any model works:
			// own beaten_* animations first, then ubiquitous generic ones.
			// The prone phase also carries the sleep token: OpenYSM reads it as the external
			// virtual sleep state (ctrl.sleep / v.is_sleep / q.is_sleeping and the sleep base
			// animation). Models that rotate the body inside the clip need that state; the
			// renderer never applies a lying-down rotation on the delegated path.
			String fallback = switch (youkai.getBeatenPhase()) {
				case YoukaiEntity.BEATEN_DEFEAT -> "special=beaten_defeat+defeat+death+die+attacked";
				case YoukaiEntity.BEATEN_FALLING -> "special=beaten_falling+falling+fall+jump+fly";
				case YoukaiEntity.BEATEN_PRONE -> "special=beaten_prone+prone+climbing+climb+sleep sleep";
				default -> "special=beaten_defeat+defeat+death+die+attacked";
			};
			return "special=" + mappedBeaten + fallback.substring("special=".length());
		}
		if (e.hasEffect(YHEffects.BEATEN.get())) {
			return "special=" + mappedBeaten + "beaten_prone+prone+climbing+climb+sleep sleep";
		}
		// Isolated previews use the same signal composition without assigning effects or physical poses.
		if (presentation.beaten() && e instanceof YsmRenderOverrideTarget target) {
			return "special=" + mappedBeaten + switch (target.getYsmSignals().state()) {
				case DEFEAT -> "beaten_defeat+defeat+death+die+attacked";
				case FALLING -> "beaten_falling+falling+fall+jump+fly";
				default -> "beaten_prone+prone+climbing+climb+sleep sleep";
			};
		}
		Vec3 motion = e.getDeltaMovement();
		double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
		boolean flying = e.isNoGravity();
		if (e instanceof GeneralYoukaiEntity youkai) {
			flying |= youkai.isFlying();
			if (youkai instanceof BossYoukaiEntity && youkai.isAggressive()) {
				flying = true;
			}
		}
		boolean angry = isAngryExpression(e);
		String overrideHint = YsmSpellHintClient.animationOverride(e);
		if (presentation.body() != null) {
			// Exact native clip; declared absence falls back to the ordinary movement/legacy hints.
			var catalog = YsmClientPresentationBridge.catalog(modelId);
			if (catalog.status() != YsmModelCatalog.Status.READY || catalog.animations().contains(presentation.body().clip()))
				overrideHint = "special=" + presentation.body().clip();
		}
		String actionHint = actionAnimationHint(modelId, overrideHint);
		List<String> hints = new ArrayList<>(3);
		if (flying) {
			hints.add("fly");
		} else if (e.onGround()) {
			if (horizontalSpeedSqr > 0.0025) {
				hints.add("walk");
			} else {
				hints.add("calm");
			}
		}
		if (angry && !presentation.combatExpressionRouted() && !YsmAnimationHints.overridesPassiveExpression(overrideHint)) {
			hints.add(YSMCompatConfig.expressionToken(modelId, "angry"));
		}
		if (!actionHint.isBlank()) {
			hints.add(actionHint);
		}
		return hints.isEmpty() ? null : String.join(" ", hints);
	}

	static boolean isBeatenProjection(LivingEntity entity) {
		return entity instanceof YoukaiEntity youkai && youkai.isBeaten() || entity.hasEffect(YHEffects.BEATEN.get());
	}

	private static String actionAnimationHint(String modelId, String animation) {
		return YsmAnimationHints.normalize(animation, key -> YSMCompatConfig.expressionToken(modelId, key));
	}

	private static List<String> splitAnimationHint(String animation) {
		if (animation.isBlank()) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (String token : animation.split("[,;|\\s]+")) {
			if (!token.isBlank()) {
				result.add(token.trim());
			}
		}
		return result;
	}

	public static List<String> loadedTextureNames(String modelId) {
		String cacheKey = modelId == null ? "" : modelId;
		long now = System.nanoTime();
		CachedList cached = textureNameCache.get(cacheKey);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return new ArrayList<>(cached.values());
		}
		List<String> result = new ArrayList<>();
		if (modelId == null || modelId.isBlank()) {
			for (String id : loadedModelIds()) {
				addAllUnique(result, loadedTextureNames(id));
			}
			if (result.isEmpty()) {
				result.add(TEXTURE_DEFAULT);
			}
			textureNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
			return result;
		}
		String defaultTexture = defaultTextureName(modelId);
		if (!defaultTexture.isBlank()) {
			result.add(defaultTexture);
		}
		if (LOADED) addAllUnique(result, ExternalLivingRenderAPI.getModelTextureNames(modelId));
		if (!result.contains(TEXTURE_DEFAULT)) {
			result.add(TEXTURE_DEFAULT);
		}
		textureNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
		return result;
	}

	public static List<String> loadedAnimationNames(String modelId) {
		String cacheKey = modelId == null ? "" : modelId;
		long now = System.nanoTime();
		CachedList cached = animationNameCache.get(cacheKey);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return new ArrayList<>(cached.values());
		}
		List<String> result = new ArrayList<>(List.of(
				"special", "cast", "charge", "angry", "fly", "walk", "calm",
				"defeat", "falling", "climb", "climbing", "beaten",
				"beaten_defeat", "beaten_falling", "beaten_prone"
		));
		if (modelId == null || modelId.isBlank()) {
			for (String id : loadedModelIds()) {
				addAllUnique(result, loadedAnimationNames(id));
			}
			animationNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
			return result;
		}
		if (LOADED) addAllUnique(result, ExternalLivingRenderAPI.getModelAnimationNames(modelId));
		animationNameCache.put(cacheKey, new CachedList(List.copyOf(result), now));
		return result;
	}

	public static String defaultTextureName(String modelId) {
		if (modelId == null || modelId.isBlank()) {
			return TEXTURE_DEFAULT;
		}
		if (!LOADED) {
			return TEXTURE_DEFAULT;
		}
		long now = System.nanoTime();
		CachedString cached = defaultTextureCache.get(modelId);
		if (cached != null && isCacheFresh(cached.loadedAt(), now)) {
			return cached.value();
		}
		String name = ExternalLivingRenderAPI.getModelDefaultTextureName(modelId);
		name = name == null || name.isBlank() ? TEXTURE_DEFAULT : name;
		defaultTextureCache.put(modelId, new CachedString(name, now));
		return name;
	}

	private static boolean isCacheFresh(long loadedAt, long now) {
		return loadedAt > 0 && now - loadedAt < CATALOG_CACHE_NANOS;
	}

	private static void addAllUnique(List<String> result, Iterable<String> values) {
		for (String value : values) {
			if (value != null && !value.isBlank() && !result.contains(value)) {
				result.add(value);
			}
		}
	}

	private static String hintKey(String token) {
		int equals = token.indexOf('=');
		return (equals >= 0 ? token.substring(0, equals) : token).trim();
	}

	private static boolean hasHintToken(String animationHint, String expected) {
		for (String token : splitAnimationHint(animationHint)) {
			if (expected.equals(hintKey(token))) {
				return true;
			}
		}
		return false;
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm")
				.then(Commands.literal("debug")
						.then(Commands.literal("on")
								.executes(ctx -> setDebugTarget(ctx, getPointedEntity()))
								.then(Commands.argument("entities", ENTITY_TARGET_ARGUMENT)
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> setDebugTarget(ctx, getFirstResolvedEntity(ctx)))))
						.then(Commands.literal("off")
								.executes(YSMClientCompat::disableDebug))
						.then(Commands.literal("status")
								.executes(YSMClientCompat::showDebugStatus))
						.then(Commands.literal("models")
								.executes(YSMClientCompat::showLoadedModels))
						.then(Commands.literal("reset")
								.executes(ctx -> {
									sendOverrideRequest(ctx, "reset", null, "", "", List.of());
									return 1;
								})))
				.then(buildSetCommand()));
		YsmPresentationClientCommands.register(event.getDispatcher(), ENTITY_TARGET_ARGUMENT, TARGET_ENTITY_SUGGESTIONS);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSetCommand() {
		var target = Commands.argument("entities", BINDING_TARGET_ARGUMENT).suggests(BINDING_TARGET_SUGGESTIONS)
				.then(Commands.literal("model")
						.then(Commands.argument("model", YSM_ID_ARGUMENT).suggests(MODEL_SUGGESTIONS)
								.executes(ctx -> setBindingMapping(ctx, TEXTURE_DEFAULT))
								.then(Commands.argument("texture", YSM_ID_ARGUMENT)
										.executes(ctx -> setBindingMapping(ctx, getYsmId(ctx, "texture"))))))
				.then(Commands.literal("off").executes(YSMClientCompat::disableBinding))
				.then(Commands.literal("unset").executes(YSMClientCompat::unsetBinding));
		return Commands.literal("set").then(target);
	}

	private static int showStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSystemMessage(Component.literal("[YH/YSM] Yes Steve Model loaded: " + LOADED));
		source.sendSystemMessage(Component.literal("[YH/YSM] Default mappings:"));
		YSMCompatConfig.defaultBindings().forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		if (TYPE_DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Legacy type overrides: none (use config)"));
		} else {
			source.sendSystemMessage(Component.literal("[YH/YSM] Legacy type overrides (ignored; use config):"));
			TYPE_DEBUG_OVERRIDES.forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		}
		if (ENTITY_DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Entity overrides: none"));
		} else {
			source.sendSystemMessage(Component.literal("[YH/YSM] Entity overrides:"));
			ENTITY_DEBUG_OVERRIDES.forEach((uuid, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(uuid, binding))));
		}
		return 1;
	}

	private static int showLoadedModels(CommandContext<CommandSourceStack> ctx) {
		List<String> ids = loadedModelIds();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Loaded YSM model ids (" + ids.size() + "):"));
		for (String id : ids) {
			ctx.getSource().sendSystemMessage(Component.literal("  " + id));
		}
		return ids.size();
	}

	private static int setDebugTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Point at a living entity or pass a visible client entity selector/UUID."));
			return 0;
		}
		debugTarget = living.getUUID();
		debugOverlay = true;
		clearYsmDebug();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug target: " + entityDebugName(living)));
		return 1;
	}

	private static int disableDebug(CommandContext<CommandSourceStack> ctx) {
		debugOverlay = false;
		debugTarget = null;
		clearYsmDebug();
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug overlay disabled."));
		return 1;
	}

	private static int showDebugStatus(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug overlay: " + debugOverlay));
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Target: " + (debugTarget == null ? "none" : debugTarget)));
		Entity entity = getDebugTargetEntity();
		if (entity != null) {
			return inspectDebugTarget(ctx, entity);
		}
		return 1;
	}

	private static int inspectDebugTarget(CommandContext<CommandSourceStack> ctx, Entity entity) {
		if (entity == null) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] No visible debug target. Point at an entity or pass a selector/UUID."));
			return 0;
		}
		for (DebugLine line : collectDebugLines(entity)) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + line.label() + ": " + line.value()));
		}
		return 1;
	}

	public static void renderDebugOverlay(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
		if (!debugOverlay || Minecraft.getInstance().screen != null) {
			return;
		}
		Entity entity = getDebugTargetEntity();
		if (entity == null) {
			return;
		}
		List<DebugLine> lines = collectDebugLines(entity);
		if (lines.isEmpty()) {
			return;
		}
		Font font = gui.getFont();
		int labelWidth = 0;
		int valueWidth = 0;
		for (DebugLine line : lines) {
			labelWidth = Math.max(labelWidth, font.width(line.label()));
			valueWidth = Math.max(valueWidth, font.width(line.value()));
		}
		int rowHeight = font.lineHeight + 2;
		int x = 5;
		int y = 5;
		int panelWidth = Math.min(width - 10, labelWidth + valueWidth + 22);
		for (int i = 0; i < lines.size(); i++) {
			DebugLine line = lines.get(i);
			graphics.fill(x - 2, y - 1, x + panelWidth, y + rowHeight - 1, i % 2 == 0 ? DEBUG_BG_A : DEBUG_BG_B);
			graphics.drawString(font, line.label(), x, y, DEBUG_LABEL_COLOR, false);
			graphics.drawString(font, line.value(), x + labelWidth + 12, y, DEBUG_TEXT_COLOR, false);
			y += rowHeight;
			if (y + rowHeight > height - 5) {
				break;
			}
		}
	}

	private static List<DebugLine> collectDebugLines(Entity entity) {
		List<DebugLine> lines = new ArrayList<>();
		ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		lines.add(new DebugLine("entity", entityDebugName(entity)));
		lines.add(new DebugLine("type", String.valueOf(typeId)));
		lines.add(new DebugLine("uuid", entity.getUUID().toString()));
		if (entity instanceof GeneralYoukaiEntity youkai) {
			BindingResolution resolution = resolveBindingWithSource(youkai);
			RenderBinding binding = resolution.binding();
			RenderRequest request = resolveRenderRequest(youkai);
			lines.add(new DebugLine("binding", formatBinding(resolution.source(), binding)));
			String modelId = request != null ? request.modelId() : binding != null && binding.enabled() ? binding.modelId() : "";
			lines.add(new DebugLine("yh.render", request == null ? "none" : request.modelId() + " / " + request.textureName()));
			lines.add(new DebugLine("yh.spellYsm", youkai.describeYsmRenderOverride()));
			lines.add(new DebugLine("yh.hint", request == null ? "null" : String.valueOf(request.animationHint())));
			lines.add(new DebugLine("yh.expression", isAngryExpression(youkai) ? "angry" : "none"));
			if (!modelId.isBlank()) {
				lines.add(new DebugLine("yh.expressionMap", YSMCompatConfig.debugExpressionMapping(modelId, "angry")));
			}
			lines.add(new DebugLine("yh.flags", formatYoukaiFlags(youkai)));
			lines.add(new DebugLine("yh.motion", formatMotion(youkai.getDeltaMovement())));
			lines.add(new DebugLine("yh.target", youkai.getTarget() == null ? "none" : entityDebugName(youkai.getTarget())));
		} else if (entity instanceof LivingEntity living) {
			BindingResolution resolution = resolveBindingWithSource(living);
			RenderBinding binding = resolution.binding();
			RenderRequest request = resolveRenderRequest(living);
			lines.add(new DebugLine("binding", formatBinding(resolution.source(), binding)));
			lines.add(new DebugLine("yh.render", request == null ? "none" : request.modelId() + " / " + request.textureName()));
			lines.add(new DebugLine("yh.hint", request == null ? "null" : String.valueOf(request.animationHint())));
			if (living instanceof YsmRenderOverrideTarget target) {
				lines.add(new DebugLine("yh.spellYsm", target.describeYsmRenderOverride()));
			}
		}
		if (entity instanceof LivingEntity living) {
			lines.add(new DebugLine("held.mainhand", describeHeldItem(living.getMainHandItem())));
			lines.add(new DebugLine("held.offhand", describeHeldItem(living.getOffhandItem())));
			lines.add(new DebugLine("yh.spellHint", YsmSpellHintClient.animationOverride(living)));
			if (living instanceof YsmRenderOverrideTarget target) {
				lines.add(new DebugLine("yh.presentation", target.getYsmPresentation().expire(target.getYsmPresentationTime()).toTag().toString()));
			}
			YsmClientPresentationBridge.diagnostics(living).forEach((key, value) -> lines.add(new DebugLine(key, value)));
			lines.addAll(collectYsmDebugLines(living));
		}
		return lines;
	}

	private static String describeHeldItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return "empty";
		}
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
		return String.valueOf(id) + " x" + stack.getCount();
	}

	private static List<DebugLine> collectYsmDebugLines(LivingEntity entity) {
		List<DebugLine> lines = new ArrayList<>();
		if (!LOADED) {
			lines.add(new DebugLine("ysm.loaded", "false"));
			return lines;
		}
		Map<String, String> snapshot = getYsmDebugSnapshot(entity);
		snapshot.forEach((key, value) -> lines.add(new DebugLine("ysm." + key, value)));
		return lines;
	}

	private static String formatBinding(String source, RenderBinding binding) {
		if (binding == null) {
			return source + " -> none";
		}
		if (!binding.enabled()) {
			return source + " -> off";
		}
		return source + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static boolean isAngryExpression(LivingEntity entity) {
		if (entity instanceof GeneralYoukaiEntity youkai) {
			return youkai.isAggressive() ||
					youkai.getTarget() != null ||
					youkai instanceof BossYoukaiEntity boss && boss.isChaotic();
		}
		return entity.hurtTime > 0;
	}

	private static String formatYoukaiFlags(GeneralYoukaiEntity entity) {
		boolean boss = entity instanceof BossYoukaiEntity;
		boolean chaotic = entity instanceof BossYoukaiEntity bossEntity && bossEntity.isChaotic();
		return "ground=" + entity.onGround() +
				", flying=" + entity.isFlying() +
				", noGravity=" + entity.isNoGravity() +
				", aggressive=" + entity.isAggressive() +
				", boss=" + boss +
				", chaotic=" + chaotic +
				", hurt=" + entity.hurtTime;
	}

	private static String formatMotion(Vec3 motion) {
		double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		return String.format(Locale.ROOT, "x=%.3f y=%.3f z=%.3f h=%.3f", motion.x, motion.y, motion.z, horizontal);
	}

	private static String entityDebugName(Entity entity) {
		return entity.getDisplayName().getString() + " (" + entity.getId() + ")";
	}

	private static String formatStatusLine(ResourceLocation entityId, RenderBinding binding) {
		if (!binding.enabled()) {
			return "  " + entityId + " -> off";
		}
		return "  " + entityId + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static String formatStatusLine(UUID uuid, RenderBinding binding) {
		if (!binding.enabled()) {
			return "  " + uuid + " -> off";
		}
		return "  " + uuid + " -> " + binding.modelId() + " / " + binding.textureName();
	}

	private static int setBindingMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		ResourceLocation entityId = parseBindingType(ctx);
		if (entityId != null) {
			return saveTypeConfig(ctx, entityId, RenderBinding.enabled(modelId, textureName));
		}
		if (getYsmId(ctx, "entities").contains(":")) return 0;
		return setEntityMapping(ctx, textureName);
	}

	private static int disableBinding(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseBindingType(ctx);
		if (entityId != null) {
			return saveTypeConfig(ctx, entityId, RenderBinding.disabled());
		}
		if (getYsmId(ctx, "entities").contains(":")) return 0;
		return setEntityDisabled(ctx);
	}

	private static int unsetBinding(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseBindingType(ctx);
		if (entityId != null) {
			return saveTypeConfig(ctx, entityId, null);
		}
		if (getYsmId(ctx, "entities").contains(":")) return 0;
		return unsetEntityMapping(ctx);
	}

	private static int saveTypeConfig(CommandContext<CommandSourceStack> ctx, ResourceLocation entityId, RenderBinding binding) {
		try {
			YSMCompatConfig.saveExternalBinding(entityId, binding);
			ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " configuration updated."));
			return 1;
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Could not save type configuration: " + ex.getMessage()));
			return 0;
		}
	}

	private static int setEntityMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		sendOverrideRequest(ctx, "entity_set", null, modelId, textureName,
				entities.stream().map(Entity::getUUID).toList());
		return entities.size();
	}

	private static int setEntityDisabled(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		sendOverrideRequest(ctx, "entity_off", null, "", "",
				entities.stream().map(Entity::getUUID).toList());
		return entities.size();
	}

	private static int unsetEntityMapping(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		if (entities.isEmpty()) {
			return 0;
		}
		sendOverrideRequest(ctx, "entity_unset", null, "", "",
				entities.stream().map(Entity::getUUID).toList());
		return entities.size();
	}

	/**
	 * Send a manual model override request to the server. The server validates
	 * permissions and targets, persists the change and broadcasts the resulting
	 * table (with a confirmation message) to all clients.
	 */
	private static void sendOverrideRequest(CommandContext<CommandSourceStack> ctx, String action,
											@org.jetbrains.annotations.Nullable String entityType,
											String modelId, String textureName, List<UUID> entities) {
		if (Minecraft.getInstance().getConnection() == null) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Not connected to a server; cannot apply overrides."));
			return;
		}
		String uuidList = entities.isEmpty() ? ""
				: entities.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
		YoukaisHomecoming.HANDLER.toServer(new YsmOverrideRequestToServer(action, entityType, modelId, textureName, uuidList));
	}

	/**
	 * Apply the server-authoritative override table (from {@link YsmOverrideSyncToClient}).
	 * Called on the network thread; runs on the client thread.
	 */
	public static void applySyncedOverrides(CompoundTag typeTag, CompoundTag entityTag, String message) {
		Minecraft.getInstance().execute(() -> {
			TYPE_DEBUG_OVERRIDES.clear();
			for (String key : typeTag.getAllKeys()) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id != null) {
					TYPE_DEBUG_OVERRIDES.put(id, YsmOverrideData.bindingFromTag(typeTag.getCompound(key)));
				}
			}
			ENTITY_DEBUG_OVERRIDES.clear();
			for (String key : entityTag.getAllKeys()) {
				try {
					ENTITY_DEBUG_OVERRIDES.put(UUID.fromString(key), YsmOverrideData.bindingFromTag(entityTag.getCompound(key)));
				} catch (IllegalArgumentException ignored) {
				}
			}
			if (!message.isBlank() && Minecraft.getInstance().player != null) {
				Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
			}
		});
	}

	private static ResourceLocation parseBindingType(CommandContext<CommandSourceStack> ctx) {
		String target = getYsmId(ctx, "entities");
		if (target.startsWith("@")) {
			return null;
		}
		try {
			UUID.fromString(target);
			return null;
		} catch (IllegalArgumentException ignored) {
		}
		if (!target.contains(":")) {
			return null;
		}
		ResourceLocation entityId = ResourceLocation.tryParse(target);
		if (entityId == null || (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)
				&& !YSMCompatConfig.defaultBindings().containsKey(entityId))) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unknown entity type: " + target));
			return null;
		}
		return entityId;
	}

	private static java.util.concurrent.CompletableFuture<Suggestions> bindingTargetSuggestions(CommandContext<CommandSourceStack> ctx,
			SuggestionsBuilder builder) {
		String remaining = builder.getRemainingLowerCase();
		if (remaining.startsWith("@")) {
			return EntityArgument.entities().listSuggestions(ctx, builder);
		}
		StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
		List<Suggestion> ordered = new ArrayList<>();
		var configured = YSMCompatConfig.defaultBindings().keySet();
		java.util.stream.Stream.concat(ForgeRegistries.ENTITY_TYPES.getKeys().stream(), configured.stream())
				.filter(id -> ForgeRegistries.ENTITY_TYPES.containsKey(id) || configured.contains(id))
				.distinct()
				.sorted()
				.forEach(id -> {
					String value = id.toString();
					if (value.startsWith(remaining)) {
						var type = ForgeRegistries.ENTITY_TYPES.getValue(id);
						ordered.add(new Suggestion(range, value, type == null ? Component.literal(value) : Component.translatable(type.getDescriptionId())));
					}
				});
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			List<Entity> entities = collectClientEntities(minecraft).stream()
					.filter(entity -> entity instanceof YsmRenderOverrideTarget)
					.filter(entity -> isWithinUuidSuggestionRange(entity, minecraft.player))
					.sorted(Comparator.comparingDouble(entity -> minecraft.player == null ? 0 : entity.distanceToSqr(minecraft.player)))
					.toList();
			for (Entity entity : entities) {
				String uuid = entity.getUUID().toString();
				if (!uuid.startsWith(remaining)) continue;
				String distance = minecraft.player == null ? "?" : String.format(Locale.ROOT, "%.1f", entity.distanceTo(minecraft.player));
				ordered.add(new Suggestion(range, uuid, Component.translatable("commands.youkaishomecoming.model.entity_candidate",
						ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), distance)));
			}
		}
		if (!remaining.isEmpty()) {
			return java.util.concurrent.CompletableFuture.completedFuture(new Suggestions(range, ordered));
		}
		SuggestionsBuilder vanillaBuilder = new SuggestionsBuilder(builder.getInput(), builder.getStart());
		return EntityArgument.entities().listSuggestions(ctx, vanillaBuilder).thenApply(vanilla -> {
			var seen = new java.util.HashSet<String>();
			for (Suggestion suggestion : ordered) seen.add(suggestion.getText());
			for (Suggestion suggestion : vanilla.getList()) if (seen.add(suggestion.getText())) ordered.add(suggestion);
			return new Suggestions(range, ordered);
		});
	}

	static boolean isWithinUuidSuggestionRange(Entity entity, Entity player) {
		return player == null || entity.distanceToSqr(player) <= UUID_SUGGESTION_RANGE_SQR;
	}

	private static Entity getPointedEntity() {
		HitResult hitResult = Minecraft.getInstance().hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			return entityHitResult.getEntity();
		}
		return null;
	}

	static Entity getPointedEntityOrSelected() {
		Entity pointed = getPointedEntity();
		return pointed != null ? pointed : getDebugTargetEntity();
	}

	private static Entity getDebugTargetEntity() {
		if (debugTarget == null) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		for (Entity entity : collectClientEntities(minecraft)) {
			if (entity.getUUID().equals(debugTarget)) {
				return entity;
			}
		}
		return null;
	}

	static Entity getFirstResolvedEntity(CommandContext<CommandSourceStack> ctx) {
		List<Entity> entities = resolveClientEntities(ctx);
		return entities.isEmpty() ? null : entities.get(0);
	}

	private static String getYsmId(CommandContext<CommandSourceStack> ctx, String name) {
		return ctx.getArgument(name, String.class);
	}

	private static List<Entity> resolveClientEntities(CommandContext<CommandSourceStack> ctx) {
		String target = ctx.getArgument("entities", String.class);
		return resolveClientEntities(target, ctx.getSource()::sendFailure);
	}

	/** Read-only completion lookup: never emits chat errors while the user is typing. */
	static List<Entity> entitiesForSuggestions(String target) {
		return resolveClientEntities(target, ignored -> { });
	}

	private static List<Entity> resolveClientEntities(String target, java.util.function.Consumer<Component> error) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			error.accept(Component.literal("[YH/YSM] No client level is loaded."));
			return List.of();
		}
		if (target.startsWith("@")) {
			return resolveClientSelector(error, target, minecraft);
		}
		try {
			UUID uuid = UUID.fromString(target);
			for (Entity entity : collectClientEntities(minecraft)) {
				if (entity.getUUID().equals(uuid)) {
					return List.of(entity);
				}
			}
			error.accept(Component.literal("[YH/YSM] Entity UUID is not visible on the client: " + uuid));
			return List.of();
		} catch (IllegalArgumentException ignored) {
			for (Entity entity : collectClientEntities(minecraft)) {
				if (entity.getName().getString().equals(target)) {
					return List.of(entity);
				}
			}
			error.accept(Component.literal("[YH/YSM] Unsupported entity target or invisible entity: " + target));
			return List.of();
		}
	}

	private static List<Entity> resolveClientSelector(java.util.function.Consumer<Component> error, String target, Minecraft minecraft) {
		char selector = target.length() > 1 ? target.charAt(1) : '\0';
		Map<String, String> options = parseSelectorOptions(target);
		List<Entity> result = new ArrayList<>();
		switch (selector) {
			case 's' -> {
				if (minecraft.player != null) {
					result.add(minecraft.player);
				}
			}
			case 'p' -> {
				for (Entity entity : collectClientEntities(minecraft)) {
					if (entity instanceof net.minecraft.world.entity.player.Player) {
						result.add(entity);
					}
				}
				sortEntities(result, "nearest", minecraft);
				limitEntities(result, 1);
			}
			case 'a' -> {
				for (Entity entity : collectClientEntities(minecraft)) {
					if (entity instanceof net.minecraft.world.entity.player.Player) {
						result.add(entity);
					}
				}
			}
			case 'e' -> result.addAll(collectClientEntities(minecraft));
			default -> {
				error.accept(Component.literal("[YH/YSM] Unsupported client selector: @" + selector));
				return List.of();
			}
		}
		filterByType(result, options.get("type"));
		sortEntities(result, options.get("sort"), minecraft);
		if (options.containsKey("limit")) {
			try {
				limitEntities(result, Math.max(0, Integer.parseInt(options.get("limit"))));
			} catch (NumberFormatException ex) {
				error.accept(Component.literal("[YH/YSM] Invalid selector limit: " + options.get("limit")));
				return List.of();
			}
		}
		if (result.isEmpty()) {
			error.accept(Component.literal("[YH/YSM] No visible client entities matched: " + target));
		}
		return result;
	}

	private static List<Entity> collectClientEntities(Minecraft minecraft) {
		Map<UUID, Entity> entities = new LinkedHashMap<>();
		if (minecraft.level != null) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				entities.put(entity.getUUID(), entity);
			}
			for (Entity entity : minecraft.level.players()) {
				entities.put(entity.getUUID(), entity);
			}
		}
		if (minecraft.player != null) {
			entities.put(minecraft.player.getUUID(), minecraft.player);
		}
		return new ArrayList<>(entities.values());
	}

	private static Map<String, String> parseSelectorOptions(String target) {
		int start = target.indexOf('[');
		int end = target.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return Map.of();
		}
		Map<String, String> options = new LinkedHashMap<>();
		for (String part : target.substring(start + 1, end).split(",")) {
			int equals = part.indexOf('=');
			if (equals > 0) {
				options.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
			}
		}
		return options;
	}

	private static void filterByType(List<Entity> entities, String typeOption) {
		if (typeOption == null || typeOption.isBlank()) {
			return;
		}
		boolean negate = typeOption.startsWith("!");
		String expected = negate ? typeOption.substring(1) : typeOption;
		entities.removeIf(entity -> {
			ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
			boolean matches = id != null && id.toString().equals(expected);
			return negate ? matches : !matches;
		});
	}

	private static void sortEntities(List<Entity> entities, String sort, Minecraft minecraft) {
		if (sort == null || sort.isBlank() || "arbitrary".equals(sort)) {
			return;
		}
		Entity origin = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
		if (origin == null) {
			return;
		}
		Comparator<Entity> comparator = Comparator.comparingDouble(origin::distanceToSqr);
		if ("furthest".equals(sort)) {
			comparator = comparator.reversed();
		} else if (!"nearest".equals(sort)) {
			return;
		}
		entities.sort(comparator);
	}

	private static void limitEntities(List<Entity> entities, int limit) {
		while (entities.size() > limit) {
			entities.remove(entities.size() - 1);
		}
	}

	private static class TokenArgument implements ArgumentType<String> {

		private final SimpleCommandExceptionType emptyInput;
		private final Collection<String> examples;

		private TokenArgument(String errorMessage, Collection<String> examples) {
			this.emptyInput = new SimpleCommandExceptionType(new LiteralMessage(errorMessage));
			this.examples = examples;
		}

		@Override
		public String parse(StringReader reader) throws CommandSyntaxException {
			int start = reader.getCursor();
			while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
				reader.skip();
			}
			if (reader.getCursor() == start) {
				throw emptyInput.createWithContext(reader);
			}
			// Chat copy-paste delivers quoted tokens (inspect prints ids inside quotes) and
			// older completions escaped any id containing '/' or non-ASCII. A raw token parse
			// would keep the quotes and silently create a new id.
			return unquoteToken(reader.getString().substring(start, reader.getCursor()));
		}

		/**
		 * Mirrors brigadier's quotable string semantics: one matching pair of double quotes
		 * is removed and {@code \"} / {@code \\} are unescaped. Tokens that are not fully
		 * quoted, or with a dangling escape, stay literal so nothing is silently mangled.
		 */
		private static String unquoteToken(String token) {
			if (token.length() < 2 || token.charAt(0) != '"' || token.charAt(token.length() - 1) != '"') {
				return token;
			}
			StringBuilder value = new StringBuilder(token.length() - 2);
			boolean escaped = false;
			for (int i = 1; i < token.length() - 1; i++) {
				char c = token.charAt(i);
				if (escaped) {
					if (c == '"' || c == '\\') value.append(c);
					else value.append('\\').append(c);
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else {
					value.append(c);
				}
			}
			return escaped ? token : value.toString();
		}

		@Override
		public Collection<String> getExamples() {
			return examples;
		}
	}

	private record BindingResolution(RenderBinding binding, String source) {
	}

	static void clearSessionState() {
		bindingRevision = 0;
		YsmClientProfiles.clear();
		TYPE_DEBUG_OVERRIDES.clear();
		ENTITY_DEBUG_OVERRIDES.clear();
		textureNameCache.clear();
		animationNameCache.clear();
		defaultTextureCache.clear();
		loadedModelIdsCache = List.of();
		loadedModelIdsCacheAt = 0;
		debugTarget = null;
		debugOverlay = false;
	}

	static record RenderRequest(String modelId, String textureName, String animationHint, YsmPresentationResolver.Resolved presentation) {
	}

	private record DebugLine(String label, String value) {
	}

	private record CachedList(List<String> values, long loadedAt) {
	}

	private record CachedString(String value, long loadedAt) {
	}
}
