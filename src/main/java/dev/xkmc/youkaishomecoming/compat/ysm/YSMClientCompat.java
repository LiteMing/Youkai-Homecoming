package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YSMClientCompat {

	private static final String MOD_ID = "yes_steve_model";
	private static final String MODEL_REMILIA = "yh/remilia";
	private static final String MODEL_FLANDRE = "yh/flandre";
	private static final String TEXTURE_DEFAULT = "default";
	private static final ArgumentType<String> YSM_ID_ARGUMENT = new YsmIdArgument();
	private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
	private static final ResourceLocation REMILIA_ENTITY = YoukaisHomecoming.loc("remilia_scarlet");
	private static final Map<ResourceLocation, RenderBinding> DEFAULT_BINDINGS = Map.of(
			REMILIA_ENTITY, RenderBinding.enabled(MODEL_REMILIA, TEXTURE_DEFAULT)
	);
	private static final Map<ResourceLocation, RenderBinding> TYPE_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final Map<UUID, RenderBinding> ENTITY_DEBUG_OVERRIDES = new LinkedHashMap<>();
	private static final SuggestionProvider<CommandSourceStack> ENTITY_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ForgeRegistries.ENTITY_TYPES.getKeys().stream()
					.filter(id -> Objects.equals(id.getNamespace(), YoukaisHomecoming.MODID))
					.map(ResourceLocation::toString), builder);
	private static final SuggestionProvider<CommandSourceStack> MODEL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(MODEL_REMILIA, MODEL_FLANDRE), builder);
	private static final SuggestionProvider<CommandSourceStack> TARGET_ENTITY_SUGGESTIONS = (ctx, builder) -> {
		Entity pointed = getPointedEntity();
		if (pointed != null) {
			builder.suggest(pointed.getUUID().toString(), Component.literal("pointed entity"));
		}
		return SharedSuggestionProvider.suggest(Stream.of(
				"@e[limit=1,sort=nearest]",
				"@e[type=youkaishomecoming:remilia_scarlet,limit=1,sort=nearest]",
				"@a[limit=1,sort=nearest]"
		), builder);
	};

	private static Method renderMethod;
	private static boolean unavailable;

	public static boolean delegateRender(GeneralYoukaiEntity e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		RenderBinding binding = resolveBinding(e);
		if (!LOADED || unavailable || binding == null) {
			return false;
		}
		Method method = getRenderMethod();
		if (method == null) {
			return false;
		}
		try {
			Object result = method.invoke(null, e, binding.modelId(), binding.textureName(), selectAnimation(e), yaw, pTick, pose, buffer, light);
			return result instanceof Boolean value && value;
		} catch (IllegalAccessException | InvocationTargetException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Failed to delegate youkai rendering to Yes Steve Model", ex);
			return false;
		}
	}

	private static Method getRenderMethod() {
		if (renderMethod != null) {
			return renderMethod;
		}
		try {
			renderMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"render",
					LivingEntity.class,
					String.class,
					String.class,
					String.class,
					float.class,
					float.class,
					PoseStack.class,
					MultiBufferSource.class,
					int.class
			);
			return renderMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model external living renderer API is unavailable", ex);
			return null;
		}
	}

	private static RenderBinding resolveBinding(GeneralYoukaiEntity e) {
		RenderBinding entityOverride = ENTITY_DEBUG_OVERRIDES.get(e.getUUID());
		if (entityOverride != null) {
			return entityOverride.enabled() ? entityOverride : null;
		}
		ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
		if (entityId == null) {
			return null;
		}
		RenderBinding override = TYPE_DEBUG_OVERRIDES.get(entityId);
		if (override != null) {
			return override.enabled() ? override : null;
		}
		return DEFAULT_BINDINGS.get(entityId);
	}

	private static String selectAnimation(GeneralYoukaiEntity e) {
		Vec3 motion = e.getDeltaMovement();
		double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
		boolean flying = e.isFlying() || e.isNoGravity();
		if (flying) {
			return "fly";
		}
		if (!e.onGround()) {
			return null;
		}
		if (horizontalSpeedSqr > 0.0025) {
			return "walk";
		}
		if (e instanceof BossYoukaiEntity boss && boss.isChaotic()) {
			return "angry";
		}
		if (e.getTarget() != null) {
			return "angry";
		}
		return "calm";
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm")
				.then(Commands.literal("status")
						.executes(YSMClientCompat::showStatus))
				.then(Commands.literal("reset")
						.executes(ctx -> {
							TYPE_DEBUG_OVERRIDES.clear();
							ENTITY_DEBUG_OVERRIDES.clear();
							ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] Debug render mappings reset."));
							return 1;
						}))
				.then(Commands.literal("type")
						.then(Commands.literal("set")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.then(Commands.argument("model", YSM_ID_ARGUMENT)
												.suggests(MODEL_SUGGESTIONS)
												.executes(ctx -> setTypeMapping(ctx, TEXTURE_DEFAULT))
												.then(Commands.argument("texture", YSM_ID_ARGUMENT)
														.executes(ctx -> setTypeMapping(ctx, getYsmId(ctx, "texture")))))))
						.then(Commands.literal("off")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.executes(ctx -> setTypeDisabled(ctx))))
						.then(Commands.literal("unset")
								.then(Commands.argument("entity_type", ResourceLocationArgument.id())
										.suggests(ENTITY_SUGGESTIONS)
										.executes(ctx -> unsetTypeMapping(ctx)))))
				.then(Commands.literal("entity")
						.then(Commands.literal("set")
								.then(Commands.argument("entities", EntityArgument.entities())
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.then(Commands.argument("model", YSM_ID_ARGUMENT)
												.suggests(MODEL_SUGGESTIONS)
												.executes(ctx -> setEntityMapping(ctx, TEXTURE_DEFAULT))
												.then(Commands.argument("texture", YSM_ID_ARGUMENT)
														.executes(ctx -> setEntityMapping(ctx, getYsmId(ctx, "texture")))))))
						.then(Commands.literal("off")
								.then(Commands.argument("entities", EntityArgument.entities())
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> setEntityDisabled(ctx))))
						.then(Commands.literal("unset")
								.then(Commands.argument("entities", EntityArgument.entities())
										.suggests(TARGET_ENTITY_SUGGESTIONS)
										.executes(ctx -> unsetEntityMapping(ctx)))))
				.then(Commands.literal("unset")
						.then(Commands.argument("entity_type", ResourceLocationArgument.id())
								.suggests(ENTITY_SUGGESTIONS)
								.executes(ctx -> unsetTypeMapping(ctx))))
				.then(Commands.literal("off")
						.then(Commands.argument("entity_type", ResourceLocationArgument.id())
								.suggests(ENTITY_SUGGESTIONS)
								.executes(ctx -> setTypeDisabled(ctx))))
				.then(Commands.literal("set")
						.then(Commands.argument("entity_type", ResourceLocationArgument.id())
								.suggests(ENTITY_SUGGESTIONS)
								.then(Commands.argument("model", YSM_ID_ARGUMENT)
										.suggests(MODEL_SUGGESTIONS)
										.executes(ctx -> setTypeMapping(ctx, TEXTURE_DEFAULT))
										.then(Commands.argument("texture", YSM_ID_ARGUMENT)
												.executes(ctx -> setTypeMapping(ctx, getYsmId(ctx, "texture"))))))));
	}

	private static int showStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSystemMessage(Component.literal("[YH/YSM] Yes Steve Model loaded: " + LOADED));
		source.sendSystemMessage(Component.literal("[YH/YSM] Default mappings:"));
		DEFAULT_BINDINGS.forEach((entityId, binding) -> source.sendSystemMessage(Component.literal(formatStatusLine(entityId, binding))));
		if (TYPE_DEBUG_OVERRIDES.isEmpty()) {
			source.sendSystemMessage(Component.literal("[YH/YSM] Type overrides: none"));
		} else {
			source.sendSystemMessage(Component.literal("[YH/YSM] Type overrides:"));
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

	private static int setTypeMapping(CommandContext<CommandSourceStack> ctx, String textureName) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.put(entityId, RenderBinding.enabled(modelId, textureName));
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " -> " + modelId + " / " + textureName));
		return 1;
	}

	private static int setTypeDisabled(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.put(entityId, RenderBinding.disabled());
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " YSM rendering disabled."));
		return 1;
	}

	private static int unsetTypeMapping(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = parseEntityType(ctx);
		if (entityId == null) {
			return 0;
		}
		TYPE_DEBUG_OVERRIDES.remove(entityId);
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] type " + entityId + " uses its default mapping."));
		return 1;
	}

	private static int setEntityMapping(CommandContext<CommandSourceStack> ctx, String textureName) throws CommandSyntaxException {
		Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "entities");
		String modelId = getYsmId(ctx, "model");
		if (modelId.isBlank() || textureName.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.put(entity.getUUID(), RenderBinding.enabled(modelId, textureName));
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) -> " + modelId + " / " + textureName));
		return entities.size();
	}

	private static int setEntityDisabled(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "entities");
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.put(entity.getUUID(), RenderBinding.disabled());
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) disabled."));
		return entities.size();
	}

	private static int unsetEntityMapping(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "entities");
		for (Entity entity : entities) {
			ENTITY_DEBUG_OVERRIDES.remove(entity.getUUID());
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH/YSM] " + entities.size() + " entity override(s) removed."));
		return entities.size();
	}

	private static ResourceLocation parseEntityType(CommandContext<CommandSourceStack> ctx) {
		ResourceLocation entityId = ResourceLocationArgument.getId(ctx, "entity_type");
		if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Unknown entity type: " + entityId));
			return null;
		}
		if (!Objects.equals(entityId.getNamespace(), YoukaisHomecoming.MODID)) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Entity type is not from Youkai Homecoming: " + entityId));
			return null;
		}
		return entityId;
	}

	private static Entity getPointedEntity() {
		HitResult hitResult = Minecraft.getInstance().hitResult;
		if (hitResult instanceof EntityHitResult entityHitResult) {
			return entityHitResult.getEntity();
		}
		return null;
	}

	private static String getYsmId(CommandContext<CommandSourceStack> ctx, String name) {
		return ctx.getArgument(name, String.class);
	}

	private static class YsmIdArgument implements ArgumentType<String> {

		private static final SimpleCommandExceptionType ERROR_EMPTY_ID =
				new SimpleCommandExceptionType(new LiteralMessage("Expected YSM id"));
		private static final List<String> EXAMPLES = List.of("yh/remilia", "yh/flandre", "namespace:path/model");

		@Override
		public String parse(StringReader reader) throws CommandSyntaxException {
			int start = reader.getCursor();
			while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
				reader.skip();
			}
			if (reader.getCursor() == start) {
				throw ERROR_EMPTY_ID.createWithContext(reader);
			}
			return reader.getString().substring(start, reader.getCursor());
		}

		@Override
		public Collection<String> getExamples() {
			return EXAMPLES;
		}
	}

	private record RenderBinding(String modelId, String textureName, boolean enabled) {

		private static RenderBinding enabled(String modelId, String textureName) {
			return new RenderBinding(modelId, textureName, true);
		}

		private static RenderBinding disabled() {
			return new RenderBinding("", "", false);
		}
	}
}
