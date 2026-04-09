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
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Collection;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YHCommands {

	private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggestResource(
					SpellRegistry.getAll().keySet(),
					builder);

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		CustomSpellStorage.loadAllIntoRegistry(event.getServer());
	}

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
								.then(argument("spell_id", ResourceLocationArgument.id())
										.suggests(SPELL_SUGGESTIONS)
										.executes(ctx -> {
											var entity = EntityArgument.getEntity(ctx, "entity");
											ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
											if (!(entity instanceof YoukaiEntity youkai)) {
												ctx.getSource().sendFailure(Component.literal("Entity is not a YoukaiEntity"));
												return 0;
											}
											SpellDefinition def = SpellRegistry.get(spellId);
											if (def == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
												return 0;
											}
											youkai.setSpellRuntime(new SpellRuntime(def));
											ctx.getSource().sendSuccess(() -> Component.literal("Set spell to " + spellId), true);
											return 1;
										}))))
				.then(literal("phase")
						.then(argument("entity", EntityArgument.entity())
								.then(argument("phase_id", ResourceLocationArgument.id())
										.executes(ctx -> {
											var entity = EntityArgument.getEntity(ctx, "entity");
											ResourceLocation phaseId = ResourceLocationArgument.getId(ctx, "phase_id");
											if (!(entity instanceof YoukaiEntity youkai) || youkai.spellRuntime == null) {
												ctx.getSource().sendFailure(Component.literal("Entity has no spell runtime"));
												return 0;
											}
											var def = youkai.spellRuntime.getDefinition();
											if (def.getPhase(phaseId) == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown phase: " + phaseId));
												return 0;
											}
											youkai.spellRuntime.forceTransition(
													new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext(
															youkai, def, youkai.spellRuntime,
															dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers.DEFAULT
													), phaseId);
											ctx.getSource().sendSuccess(() -> Component.literal("Forced phase to " + phaseId), true);
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
							}))
					.then(literal("all")
							.executes(ctx -> {
								var server = ctx.getSource().getServer();
								int restored = 0;
								int deleted = 0;
								// Restore all built-in spells to their defaults
								for (var entry : SpellRegistry.getAll().entrySet()) {
									var id = entry.getKey();
									var defaultDef = SpellRegistry.getDefault(id);
									if (defaultDef != null) {
										SpellRegistry.register(defaultDef);
										CustomSpellStorage.deleteSpell(server, id);
										restored++;
									} else {
										// Custom spell with no built-in default: delete from disk and registry
										CustomSpellStorage.deleteSpell(server, id);
										deleted++;
									}
								}
								// Remove custom-only spells from registry
								if (deleted > 0) {
									var allIds = new java.util.ArrayList<>(SpellRegistry.getAll().keySet());
									for (var id : allIds) {
										if (SpellRegistry.getDefault(id) == null) {
											SpellRegistry.remove(id);
										}
									}
								}
								int finalRestored = restored;
								int finalDeleted = deleted;
								ctx.getSource().sendSuccess(() -> Component.literal(
										"Reset all spells: " + finalRestored + " restored to default, " + finalDeleted + " custom spells removed"), true);
								return finalRestored + finalDeleted;
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
				.then(literal("preview")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									SpellDefinition def = SpellRegistry.get(spellId);
									if (def == null) {
										ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
										return 0;
									}
									if (FMLEnvironment.dist.isClient()) {
										net.minecraft.client.Minecraft.getInstance().execute(() -> {
											net.minecraft.client.Minecraft.getInstance().setScreen(
													new dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen(def));
										});
									}
									ctx.getSource().sendSuccess(() -> Component.literal("Opening preview for " + spellId), false);
									return 1;
								})))
				.then(literal("reapply")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									SpellDefinition def = SpellRegistry.get(spellId);
									if (def == null) {
										ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
										return 0;
									}
									String spellIdStr = spellId.toString();
									int count = 0;
									for (var level : ctx.getSource().getServer().getAllLevels()) {
										for (var entity : level.getAllEntities()) {
											if (!(entity instanceof YoukaiEntity youkai)) continue;
											boolean match = false;
											if (youkai.spellRuntime != null
													&& youkai.spellRuntime.getDefinition().id.equals(spellId)) {
												match = true;
											} else if (youkai.spellCard != null
													&& spellIdStr.equals(youkai.spellCard.modelId)) {
												match = true;
											}
											if (match) {
												youkai.setSpellRuntime(new SpellRuntime(def));
												count++;
											}
										}
									}
									int finalCount = count;
									ctx.getSource().sendSuccess(
											() -> Component.literal("Reapplied " + spellId + " to " + finalCount + " entities"), true);
									return count;
								})))
				.then(literal("export")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									SpellDefinition def = SpellRegistry.get(spellId);
									if (def == null) {
										ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
										return 0;
									}
									try {
										com.google.gson.JsonElement json = SpellDefinition.CODEC.encodeStart(
												com.mojang.serialization.JsonOps.INSTANCE, def).getOrThrow(false, s -> {});
										com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
										String jsonStr = gson.toJson(json);

										java.io.File dir = new java.io.File(
												ctx.getSource().getServer().getServerDirectory(),
												"youkaishomecoming_exports/" + spellId.getNamespace());
										dir.mkdirs();
										java.io.File file = new java.io.File(dir,
												spellId.getPath().replace('/', '_') + ".json");
										try (var writer = new java.io.FileWriter(file)) {
											writer.write(jsonStr);
										}
										ctx.getSource().sendSuccess(
												() -> Component.literal("Exported " + spellId + " to " + file.getPath()), true);
										return 1;
									} catch (Exception e) {
										ctx.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
										return 0;
									}
								})))
				.then(literal("import")
						.then(argument("file_path", StringArgumentType.greedyString())
								.executes(ctx -> {
									String filePath = StringArgumentType.getString(ctx, "file_path");
									try {
										java.io.File file = new java.io.File(filePath);
										if (!file.exists()) {
											// Try relative to server directory
											file = new java.io.File(
													ctx.getSource().getServer().getServerDirectory(), filePath);
										}
										if (!file.exists()) {
											// Try in exports directory
											file = new java.io.File(
													ctx.getSource().getServer().getServerDirectory(),
													"youkaishomecoming_exports/" + filePath);
										}
										if (!file.exists()) {
											ctx.getSource().sendFailure(Component.literal("File not found: " + filePath));
											return 0;
										}
										String content = java.nio.file.Files.readString(file.toPath());
										com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(content);
										var parseResult = SpellDefinition.CODEC.parse(
												com.mojang.serialization.JsonOps.INSTANCE, json);
										if (parseResult.error().isPresent()) {
											String errMsg = parseResult.error().get().message();
											ctx.getSource().sendFailure(Component.literal("Parse error: " + errMsg));
											return 0;
										}
										SpellDefinition def = parseResult.result().orElse(null);
										if (def == null) {
											ctx.getSource().sendFailure(Component.literal("Parse returned empty result"));
											return 0;
										}
										SpellRegistry.register(def);
										CustomSpellStorage.saveSpell(ctx.getSource().getServer(), def);
										ctx.getSource().sendSuccess(
												() -> Component.literal("Imported spell: " + def.id), true);
										return 1;
									} catch (Exception e) {
										String msg = e.getMessage();
										if (msg == null) msg = e.getClass().getSimpleName();
										ctx.getSource().sendFailure(Component.literal("Import failed: " + msg));
										e.printStackTrace();
										return 0;
									}
								})))
				.then(literal("reload")
						.executes(ctx -> {
							var server = ctx.getSource().getServer();
							var source = ctx.getSource();
							var selectedPacks = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
							source.sendSuccess(() -> Component.literal(
									"Reloading all datapacks (equivalent to /reload) to refresh spell definitions..."), true);
							server.reloadResources(selectedPacks).whenComplete((unused, error) -> server.execute(() -> {
								if (error != null) {
									Throwable cause = error.getCause() != null ? error.getCause() : error;
									source.sendFailure(Component.literal("Reload failed: " + cause.getMessage()));
								} else {
									source.sendSuccess(() -> Component.literal(
											"Full datapack reload complete; spell definitions refreshed"), true);
								}
							}));
							return 1;
						}))
				.then(literal("new")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									if (SpellRegistry.get(spellId) != null) {
										ctx.getSource().sendFailure(Component.literal("Spell already exists: " + spellId));
										return 0;
									}
									ResourceLocation phaseId = new ResourceLocation(spellId.getNamespace(), spellId.getPath() + "/main");
									var phase = new dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition(
											phaseId,
											java.util.List.of(),
											java.util.List.of(),
											java.util.List.of(),
											java.util.List.of(),
											java.util.List.of()
									);
									var def = new SpellDefinition(
											spellId,
											new dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay(
													spellId.getPath(), "", java.util.Optional.empty(), java.util.Optional.empty()),
											dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm.NONE,
											phaseId,
											java.util.Map.of(phaseId, phase),
											dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile.DEFAULT
									);
									SpellRegistry.register(def);
									CustomSpellStorage.saveSpell(ctx.getSource().getServer(), def);
									ctx.getSource().sendSuccess(
											() -> Component.literal("Created new spell: " + spellId + " (use /yhspell preview to edit)"), true);
									if (FMLEnvironment.dist.isClient()) {
										net.minecraft.client.Minecraft.getInstance().execute(() -> {
											net.minecraft.client.Minecraft.getInstance().setScreen(
													new dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen(
															SpellRegistry.get(spellId)));
										});
									}
									return 1;
								})))
			.then(literal("give")
					.then(argument("player", EntityArgument.players())
							.then(argument("spell_id", ResourceLocationArgument.id())
									.suggests(SPELL_SUGGESTIONS)
									.executes(ctx -> giveSpellItem(
											ctx.getSource(),
											EntityArgument.getPlayers(ctx, "player"),
											ResourceLocationArgument.getId(ctx, "spell_id"),
											null))
									.then(argument("ticks", IntegerArgumentType.integer(1))
											.executes(ctx -> giveSpellItem(
													ctx.getSource(),
													EntityArgument.getPlayers(ctx, "player"),
													ResourceLocationArgument.getId(ctx, "spell_id"),
													IntegerArgumentType.getInteger(ctx, "ticks")))))))
		);
	}

	protected static LiteralArgumentBuilder<CommandSourceStack> literal(String str) {
		return LiteralArgumentBuilder.literal(str);
	}

	protected static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	private static int giveSpellItem(CommandSourceStack source, Collection<ServerPlayer> players,
									 ResourceLocation spellId, Integer ticks) {
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def == null) {
			source.sendFailure(Component.literal("Unknown spell: " + spellId));
			return 0;
		}

		for (ServerPlayer player : players) {
			ItemStack stack = ticks == null
					? DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId)
					: DynamicSpellItem.createStackWithDuration(YHDanmaku.DYNAMIC_SPELL.get(), spellId, ticks);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}

		String suffix = ticks == null ? "" : " (" + ticks + "t)";
		if (players.size() == 1) {
			ServerPlayer player = players.iterator().next();
			source.sendSuccess(
					() -> Component.literal("Gave spell item [" + spellId + "]" + suffix + " to " + player.getName().getString()),
					true);
		} else {
			source.sendSuccess(
					() -> Component.literal("Gave spell item [" + spellId + "]" + suffix + " to " + players.size() + " players"),
					true);
		}
		return players.size();
	}

}
