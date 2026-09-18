package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Server selectors and permissions remain authoritative, including when called from a client command tree. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID)
public final class YsmPresentationCommands {

	private YsmPresentationCommands() { }

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		registerCurrent(event);
	}

	private static void registerCurrent(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm").requires(source -> source.hasPermission(2))
				.then(Commands.literal("debug")
						.then(Commands.literal("reload").executes(YsmPresentationCommands::reloadPresets)))
				.then(buildSetCommand()));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildSetCommand() {
		var targets = Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
				.then(Commands.literal("animation")
						.then(Commands.argument("clip", StringArgumentType.string()).suggests(YsmCommandSuggestions.CLIPS)
								.executes(ctx -> play(ctx, YHModel.defaultDuration()))
								.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
										.executes(ctx -> play(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))))
				.then(Commands.literal("parameter")
						.then(Commands.argument("parameter", StringArgumentType.string()).suggests(YsmCommandSuggestions.PARAMETERS)
								.then(Commands.argument("value", DoubleArgumentType.doubleArg()).suggests(YsmCommandSuggestions.VALUES)
										.executes(ctx -> setParameter(ctx, YHModel.defaultDuration()))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
												.executes(ctx -> setParameter(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))))
				.then(Commands.literal("model")
					.then(Commands.argument("model", StringArgumentType.string()).suggests(YsmCommandSuggestions.MODELS)
							.executes(ctx -> setModelBinding(ctx, "default"))
							.then(Commands.argument("texture", StringArgumentType.string())
									.executes(ctx -> setModelBinding(ctx, StringArgumentType.getString(ctx, "texture"))))))
				.then(Commands.literal("preset")
						.then(Commands.argument("model", StringArgumentType.string()).suggests(YsmCommandSuggestions.MODELS)
								.then(Commands.argument("preset", StringArgumentType.string()).suggests(YsmCommandSuggestions.PRESETS)
										.executes(ctx -> applyPreset(ctx, -1))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
												.executes(ctx -> applyPreset(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))))
				.then(Commands.literal("clear")
						.executes(ctx -> mutate(ctx, target -> YHModel.currentForMutation(target).stop().clearParameters())))
				.then(Commands.literal("state").executes(YsmPresentationCommands::state));
		return Commands.literal("set").then(targets);
	}

	private static int setModelBinding(CommandContext<CommandSourceStack> ctx, String texture) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String model = StringArgumentType.getString(ctx, "model");
		if (model.isBlank() || texture.isBlank()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] Model and texture must not be blank."));
			return 0;
		}
		List<UUID> uuids = EntityArgument.getEntities(ctx, "targets").stream().map(Entity::getUUID).toList();
		if (uuids.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[YH/YSM] No entity targets."));
			return 0;
		}
		var request = new YsmOverrideRequestToServer("entity_set", "", model, texture,
				uuids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
		YsmOverrideServerHandler.handle(player, request);
		return uuids.size();
	}

	private static int reloadPresets(CommandContext<CommandSourceStack> ctx) {
		try {
			var server = ctx.getSource().getServer();
			var data = YsmProfileData.get(server);
			data.reload();
			YsmProfileServerHandler.syncToAll(server);
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.youkaishomecoming.model.profiles_reloaded", data.entries().size()), true);
			return 1;
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.profiles_reload_failed", ex.getMessage()));
			return 0;
		}
	}

	private static int applyPreset(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
		return mutate(ctx, target -> YHModel.presetRequest(target, StringArgumentType.getString(ctx, "model"),
				StringArgumentType.getString(ctx, "preset"), ticks, YsmPresentationState.Source.COMMAND));
	}

	private static int play(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
		String clip = StringArgumentType.getString(ctx, "clip");
		return mutate(ctx, target -> YHModel.animationRequest(target, clip, ticks, YsmPresentationState.Source.COMMAND));
	}

	private static int setParameter(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "parameter");
		double value = DoubleArgumentType.getDouble(ctx, "value");
		return mutate(ctx, target -> YHModel.parameterRequest(target, name, value, ticks, YsmPresentationState.Source.COMMAND));
	}

	private static int mutate(CommandContext<CommandSourceStack> ctx, Function<YsmRenderOverrideTarget, YsmPresentationState> request) throws CommandSyntaxException {
		Map<YsmRenderOverrideTarget, YsmPresentationState> changes = new LinkedHashMap<>();
		int skipped = 0;
		try {
			// Validate all supported targets before changing any; one full parameter map cannot cause a partial batch.
			for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
				if (entity instanceof YsmRenderOverrideTarget target) changes.put(target, request.apply(target));
				else skipped++;
			}
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.invalid", ex.getMessage()));
			return 0;
		}
		if (changes.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.no_targets"));
			return 0;
		}
		changes.forEach(YsmRenderOverrideTarget::setYsmPresentation);
		int ignored = skipped;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.youkaishomecoming.model.accepted", changes.size(), ignored), true);
		return changes.size();
	}

	private static int state(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		int count = 0;
		for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
			if (!(entity instanceof YsmRenderOverrideTarget target)) continue;
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.youkaishomecoming.model.state", entity.getDisplayName(),
					target.getYsmPresentation().expire(target.getYsmPresentationTime()).toTag().toString()), false);
			count++;
		}
		if (count == 0) ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.no_targets"));
		return count;
	}
}
