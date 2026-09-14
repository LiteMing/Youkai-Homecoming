package dev.xkmc.youkaishomecoming.events;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPermission;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellPermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Player permissions have one grammar, separate from global capability policy. */
public final class SpellPermissionCommands {

	private SpellPermissionCommands() {}

	public static LiteralArgumentBuilder<CommandSourceStack> create() {
		return literal("permission").requires(source -> source.hasPermission(2))
				.executes(ctx -> {
					ctx.getSource().sendSystemMessage(Component.translatable("youkaishomecoming.spell.permission.usage"));
					return 1;
				})
				.then(argument("targets", EntityArgument.players())
						.executes(SpellPermissionCommands::query)
						.then(argument("level", IntegerArgumentType.integer(0, 4))
								.suggests((ctx, builder) -> {
									for (SpellCapabilityPermission permission : SpellCapabilityPermission.values()) {
										if (Integer.toString(permission.level()).startsWith(builder.getRemaining())) {
											builder.suggest(permission.level(), permission.displayName());
										}
									}
									return builder.buildFuture();
								})
								.executes(SpellPermissionCommands::set))
						.then(literal("reset").executes(SpellPermissionCommands::reset)));
	}

	private static int query(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(ctx, "targets");
		for (var player : targets) {
			var capability = GrazeCapability.HOLDER.get(player);
			var permission = SpellCapabilityPermission.byLevel(SpellPermissionService.effectiveLevel(player));
			Component origin = Component.translatable("youkaishomecoming.spell.permission."
					+ (capability.getSpellPermissionOverride() >= 0 ? "manual" : "automatic"));
			ctx.getSource().sendSystemMessage(Component.translatable("youkaishomecoming.spell.permission.query",
					player.getDisplayName(), permission.displayName(), origin));
		}
		return targets.size();
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(ctx, "targets");
		var permission = SpellCapabilityPermission.byLevel(IntegerArgumentType.getInteger(ctx, "level"));
		for (var player : targets) {
			GrazeCapability.HOLDER.get(player).setSpellPermissionOverride(permission.level());
			ctx.getSource().sendSuccess(() -> Component.translatable("youkaishomecoming.spell.permission.set",
					player.getDisplayName(), permission.displayName()), true);
		}
		return targets.size();
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(ctx, "targets");
		for (var player : targets) {
			GrazeCapability.HOLDER.get(player).clearSpellPermissionOverride();
			var permission = SpellCapabilityPermission.byLevel(SpellPermissionService.effectiveLevel(player));
			ctx.getSource().sendSuccess(() -> Component.translatable("youkaishomecoming.spell.permission.reset",
					player.getDisplayName(), permission.displayName()), true);
		}
		return targets.size();
	}
}
