package dev.xkmc.youkaishomecoming.events;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YHCommands {

	private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					SpellRegistry.getAll().keySet().stream().map(ResourceLocation::toString),
					builder);

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(literal("danmaku")
				.requires(e -> e.hasPermission(2))
				.then(literal("resetRender")
						.requires(e -> e.hasPermission(2))
						.executes(ctx -> {
							if (FMLEnvironment.dist.isClient()) {
								net.minecraft.client.Minecraft.getInstance().execute(() -> {
									DanmakuItem.resetRenderCache();
									var player = net.minecraft.client.Minecraft.getInstance().player;
									if (player != null) {
										player.displayClientMessage(
												Component.literal("[YH] Danmaku render cache reset."), true);
									}
								});
							}
							ctx.getSource().sendSystemMessage(Component.literal("[YH] Danmaku render cache reset scheduled."));
							return 1;
						}))
				.then(argument("player", EntityArgument.players())
						.then(literal("setLife")
								.requires(e -> e.hasPermission(2))
								.then(argument("life", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int life = ctx.getArgument("life", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setLife(life * 5);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setBomb")
								.requires(e -> e.hasPermission(2))
								.then(argument("bomb", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int bomb = ctx.getArgument("bomb", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setBomb(bomb * 5);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setPower")
								.requires(e -> e.hasPermission(2))
								.then(argument("power", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int power = ctx.getArgument("power", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setPower(power * 100);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))

				));

		// /yhspell commands
		event.getDispatcher().register(literal("yhspell")
				.requires(e -> e.hasPermission(2))
				.then(literal("set")
						.then(argument("entity", EntityArgument.entity())
								.then(argument("spell_id", StringArgumentType.string())
										.suggests(SPELL_SUGGESTIONS)
										.executes(ctx -> {
											var entity = EntityArgument.getEntity(ctx, "entity");
											String idStr = StringArgumentType.getString(ctx, "spell_id");
											ResourceLocation spellId = new ResourceLocation(idStr);
											if (!(entity instanceof YoukaiEntity youkai)) {
												ctx.getSource().sendFailure(Component.literal("Entity is not a YoukaiEntity"));
												return 0;
											}
											SpellDefinition def = SpellRegistry.get(spellId);
											if (def == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown spell: " + idStr));
												return 0;
											}
											youkai.setSpellRuntime(new SpellRuntime(def));
											ctx.getSource().sendSuccess(() -> Component.literal("Set spell to " + idStr), true);
											return 1;
										}))))
				.then(literal("phase")
						.then(argument("entity", EntityArgument.entity())
								.then(argument("phase_id", StringArgumentType.string())
										.executes(ctx -> {
											var entity = EntityArgument.getEntity(ctx, "entity");
											String idStr = StringArgumentType.getString(ctx, "phase_id");
											ResourceLocation phaseId = new ResourceLocation(idStr);
											if (!(entity instanceof YoukaiEntity youkai) || youkai.spellRuntime == null) {
												ctx.getSource().sendFailure(Component.literal("Entity has no spell runtime"));
												return 0;
											}
											var def = youkai.spellRuntime.getDefinition();
											if (def.getPhase(phaseId) == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown phase: " + idStr));
												return 0;
											}
											youkai.spellRuntime.forceTransition(
													new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext(
															youkai, def, youkai.spellRuntime,
															dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers.DEFAULT
													), phaseId);
											ctx.getSource().sendSuccess(() -> Component.literal("Forced phase to " + idStr), true);
											return 1;
										}))))
				.then(literal("variable")
						.then(argument("entity", EntityArgument.entity())
								.then(argument("key", StringArgumentType.string())
										.then(argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
												.executes(ctx -> {
													var entity = EntityArgument.getEntity(ctx, "entity");
													String key = StringArgumentType.getString(ctx, "key");
													double value = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
													if (!(entity instanceof YoukaiEntity youkai) || youkai.spellRuntime == null) {
														ctx.getSource().sendFailure(Component.literal("Entity has no spell runtime"));
														return 0;
													}
													youkai.spellRuntime.setVariable(key, value);
													ctx.getSource().sendSuccess(() -> Component.literal("Set " + key + " = " + value), true);
													return 1;
												})))))
				.then(literal("reset")
						.then(argument("entity", EntityArgument.entity())
								.executes(ctx -> {
									var entity = EntityArgument.getEntity(ctx, "entity");
									if (!(entity instanceof YoukaiEntity youkai) || youkai.spellRuntime == null) {
										ctx.getSource().sendFailure(Component.literal("Entity has no spell runtime"));
										return 0;
									}
									youkai.spellRuntime.reset();
									youkai.syncSpellState();
									ctx.getSource().sendSuccess(() -> Component.literal("Spell reset"), true);
									return 1;
								})))
				.then(literal("debug")
						.then(argument("entity", EntityArgument.entity())
								.executes(ctx -> {
									var entity = EntityArgument.getEntity(ctx, "entity");
									if (!(entity instanceof YoukaiEntity youkai)) {
										ctx.getSource().sendFailure(Component.literal("Entity is not a YoukaiEntity"));
										return 0;
									}
									var sb = new StringBuilder();
									sb.append("=== Spell Debug ===\n");
									if (youkai.spellRuntime != null) {
										var rt = youkai.spellRuntime;
										sb.append("Runtime: ").append(rt.getDefinition().id).append("\n");
										sb.append("Phase: ").append(rt.getCurrentPhaseId()).append("\n");
										sb.append("PhaseTick: ").append(rt.getPhaseTick()).append("\n");
										sb.append("TotalTick: ").append(rt.getTotalTick()).append("\n");
										sb.append("HitCount: ").append(rt.getHitCount()).append("\n");
										if (!rt.getVariables().isEmpty()) {
											sb.append("Variables: ").append(rt.getVariables()).append("\n");
										}
									} else if (youkai.spellCard != null) {
										sb.append("Legacy: modelId=").append(youkai.spellCard.modelId)
												.append(", spellId=").append(youkai.spellCard.spellId).append("\n");
									} else {
										sb.append("No spell\n");
									}
									sb.append("Health: ").append(youkai.getHealth()).append("/").append(youkai.getMaxHealth());
									ctx.getSource().sendSystemMessage(Component.literal(sb.toString()));
									return 1;
								})))
				.then(literal("list")
						.executes(ctx -> {
							var all = SpellRegistry.getAll();
							if (all.isEmpty()) {
								ctx.getSource().sendSystemMessage(Component.literal("No spells registered."));
								return 0;
							}
							ctx.getSource().sendSystemMessage(Component.literal("Registered spells (" + all.size() + "):"));
							for (var id : all.keySet()) {
								ctx.getSource().sendSystemMessage(Component.literal("  - " + id));
							}
							return all.size();
						}))
		);
	}

	protected static LiteralArgumentBuilder<CommandSourceStack> literal(String str) {
		return LiteralArgumentBuilder.literal(str);
	}

	protected static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

}
