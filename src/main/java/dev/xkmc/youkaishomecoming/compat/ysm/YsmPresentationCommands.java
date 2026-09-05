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
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Server selectors and permissions remain authoritative, including when called from a client command tree. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID)
public final class YsmPresentationCommands {

	private YsmPresentationCommands() { }

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("yhysm").requires(source -> source.hasPermission(2))
				.then(Commands.literal("anim")
						.then(Commands.literal("play").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
								.then(Commands.argument("clip", StringArgumentType.string()).suggests(YsmCommandSuggestions.CLIPS)
										.executes(ctx -> play(ctx, YHModel.defaultDuration()))
										.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
												.executes(ctx -> play(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))))
						.then(Commands.literal("stop").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
								.executes(ctx -> mutate(ctx, target -> YHModel.currentForMutation(target).stop())))))
				.then(Commands.literal("param")
						.then(Commands.literal("set").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
								.then(Commands.argument("parameter", StringArgumentType.string()).suggests(YsmCommandSuggestions.PARAMETERS)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg()).suggests(YsmCommandSuggestions.VALUES)
												.executes(ctx -> setParameter(ctx, YHModel.defaultDuration()))
												.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
														.executes(ctx -> setParameter(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))))))
						.then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
								.executes(ctx -> mutate(ctx, target -> YHModel.currentForMutation(target).clearParameters()))
								.then(Commands.argument("parameter", StringArgumentType.string()).suggests(YsmCommandSuggestions.PARAMETERS)
										.executes(ctx -> mutate(ctx, target -> YHModel.currentForMutation(target)
												.clearParameter(StringArgumentType.getString(ctx, "parameter"))))))))
				.then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
						.executes(ctx -> mutate(ctx, target -> YHModel.currentForMutation(target).stop().clearParameters()))))
				.then(Commands.literal("state").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
						.executes(YsmPresentationCommands::state)))
				.then(Commands.literal("preset")
						.then(Commands.literal("list").then(Commands.argument("model", StringArgumentType.string()).suggests(YsmCommandSuggestions.MODELS)
								.executes(YsmPresentationCommands::listPresets)))
						.then(Commands.literal("apply").then(Commands.argument("targets", EntityArgument.entities()).suggests(YsmCommandSuggestions.TARGETS)
								.then(Commands.argument("model", StringArgumentType.string()).suggests(YsmCommandSuggestions.MODELS)
										.then(Commands.argument("preset", StringArgumentType.string()).suggests(YsmCommandSuggestions.PRESETS)
												.executes(ctx -> applyPreset(ctx, -1))
												.then(Commands.argument("ticks", IntegerArgumentType.integer(0))
														.executes(ctx -> applyPreset(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))))))));
	}

	private static int listPresets(CommandContext<CommandSourceStack> ctx) {
		try {
			var profile = YsmProfileData.get(ctx.getSource().getServer()).entry(StringArgumentType.getString(ctx, "model")).profile();
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.youkaishomecoming.model.presets", profile.model(), profile.presets().size()), false);
			profile.presets().forEach((id, preset) -> ctx.getSource().sendSuccess(() -> Component.literal(id + " — " + preset.description() + " [" + preset.clip() + "]"), false));
			return profile.presets().size();
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendFailure(Component.translatable("commands.youkaishomecoming.model.invalid", ex.getMessage()));
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
