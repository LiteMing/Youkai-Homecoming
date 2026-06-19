package dev.xkmc.youkaishomecoming.events;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.fastprojectileapi.entity.ParallelTicker;
import dev.xkmc.youkaishomecoming.compat.stg.StgCombatMode;
import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgResourceEvent;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
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

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YHCommands {

	private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggestResource(
					SpellRegistry.getAll().keySet(),
					builder);
	private static final SuggestionProvider<CommandSourceStack> STG_MODE_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(StgCombatMode.commandNames(), builder);
	private static final SuggestionProvider<CommandSourceStack> STG_RESOURCE_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.List.of("life", "bomb", "power", "points"), builder);

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
								.then(argument("life", DoubleArgumentType.doubleArg(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											double life = DoubleArgumentType.getDouble(ctx, "life");
											YHStgApi.setLife(player, life);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setBomb")
								.requires(e -> e.hasPermission(2))
								.then(argument("bomb", DoubleArgumentType.doubleArg(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											double bomb = DoubleArgumentType.getDouble(ctx, "bomb");
											YHStgApi.setBomb(player, bomb);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setPower")
								.requires(e -> e.hasPermission(2))
								.then(argument("power", DoubleArgumentType.doubleArg(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											double power = DoubleArgumentType.getDouble(ctx, "power");
											YHStgApi.setPower(player, power);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setPoints")
								.requires(e -> e.hasPermission(2))
								.then(argument("points", DoubleArgumentType.doubleArg(0, 0.99))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											double points = DoubleArgumentType.getDouble(ctx, "points");
											YHStgApi.setPoints(player, points);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))

				));

		event.getDispatcher().register(literal("yhstg")
				.requires(e -> e.hasPermission(2))
				.then(literal("mode")
						.then(argument("player", EntityArgument.player())
								.then(argument("mode", StringArgumentType.word())
										.suggests(STG_MODE_SUGGESTIONS)
										.executes(ctx -> {
											ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
											String modeName = StringArgumentType.getString(ctx, "mode");
											StgCombatMode mode;
											try {
												mode = StgCombatMode.fromName(modeName);
											} catch (IllegalArgumentException e) {
												ctx.getSource().sendFailure(Component.literal(e.getMessage()));
												return 0;
											}
											YHStgApi.setMode(player, mode);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"Set STG mode for " + player.getName().getString() + " to " + mode.commandName()), true);
											return 1;
										}))))
				.then(literal("resource")
						.then(argument("player", EntityArgument.player())
								.then(literal("set")
										.then(argument("resource", StringArgumentType.word())
												.suggests(STG_RESOURCE_SUGGESTIONS)
												.then(argument("amount", DoubleArgumentType.doubleArg(0))
														.executes(ctx -> setStgResource(ctx, false)))))
								.then(literal("add")
										.then(argument("resource", StringArgumentType.word())
												.suggests(STG_RESOURCE_SUGGESTIONS)
												.then(argument("amount", DoubleArgumentType.doubleArg())
														.executes(ctx -> setStgResource(ctx, true)))))))
				.then(literal("bomb")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
									if (!YHStgApi.tryManualBomb(player)) {
										ctx.getSource().sendFailure(Component.literal("Manual Bomb failed: no Bomb available"));
										return 0;
									}
									ctx.getSource().sendSuccess(() -> Component.literal(
											"Manual Bomb used by " + player.getName().getString()), true);
									return 1;
								})))
				.then(literal("combat")
						.then(argument("player", EntityArgument.player())
								.then(argument("enabled", BoolArgumentType.bool())
										.executes(ctx -> {
											ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
											boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
											YHStgApi.setDanmakuCombat(player, enabled);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"Set STG combat for " + player.getName().getString() + " to " + enabled), true);
											return 1;
										}))))
				.then(literal("erase")
						.then(argument("player", EntityArgument.player())
								.then(argument("radius", DoubleArgumentType.doubleArg(0))
										.executes(ctx -> {
											ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
											double radius = DoubleArgumentType.getDouble(ctx, "radius");
											int erased = YHStgApi.eraseActiveDanmaku(player, radius, false);
											ctx.getSource().sendSuccess(() -> Component.literal(
													"Erased " + erased + " active danmaku for " + player.getName().getString()), true);
											return erased;
										})))));

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
				.then(literal("editor")
						.executes(ctx -> {
							if (FMLEnvironment.dist.isClient()) {
								net.minecraft.client.Minecraft.getInstance().execute(() -> {
									net.minecraft.client.Minecraft.getInstance().setScreen(
											dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen.createDraftEditor());
								});
							}
							ctx.getSource().sendSuccess(() -> Component.literal("Opening spell editor"), false);
							return 1;
						}))
				.then(literal("reapply")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									int count;
									try {
										count = SpellRuntimeAccess.reapply(ctx.getSource().getServer(), spellId, true);
									} catch (Exception e) {
										ctx.getSource().sendFailure(Component.literal(e.getMessage()));
										return 0;
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
				.then(literal("patch")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.then(argument("json_pointer", StringArgumentType.string())
										.then(argument("json_value", StringArgumentType.greedyString())
												.executes(ctx -> {
													ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
													String pointer = StringArgumentType.getString(ctx, "json_pointer");
													String jsonValue = StringArgumentType.getString(ctx, "json_value");
													try {
														int applied = SpellRuntimeAccess.patch(
																ctx.getSource().getServer(),
																spellId.toString(),
																pointer,
																jsonValue,
																true,
																true);
														int finalApplied = applied;
														ctx.getSource().sendSuccess(
																() -> Component.literal("Patched " + spellId + " and reapplied to "
																		+ finalApplied + " entities"), true);
														return 1;
													} catch (Exception e) {
														String msg = e.getMessage();
														if (msg == null) msg = e.getClass().getSimpleName();
														ctx.getSource().sendFailure(Component.literal("Patch failed: " + msg));
														return 0;
													}
												})))))
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
										int applied = SpellRuntimeAccess.importJson(ctx.getSource().getServer(), content, true, true);
										com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(content);
										SpellDefinition def = SpellDefinition.CODEC.parse(
												com.mojang.serialization.JsonOps.INSTANCE, json).result().orElseThrow();
										int finalApplied = applied;
										ctx.getSource().sendSuccess(
												() -> Component.literal("Imported spell: " + def.id
														+ " and reapplied to " + finalApplied + " entities"), true);
										return 1;
									} catch (Exception e) {
										String msg = e.getMessage();
										if (msg == null) msg = e.getClass().getSimpleName();
										ctx.getSource().sendFailure(Component.literal("Import failed: " + msg));
										e.printStackTrace();
										return 0;
									}
								})))
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
					.then(argument("spell_id", ResourceLocationArgument.id())
							.suggests(SPELL_SUGGESTIONS)
							.executes(ctx -> {
								// Natural end mode (default)
								ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
								SpellDefinition def = SpellRegistry.get(spellId);
								if (def == null) {
									ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
									return 0;
								}
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								ItemStack stack = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId);
								if (!player.getInventory().add(stack)) {
									player.drop(stack, false);
								}
								ctx.getSource().sendSuccess(
										() -> Component.literal("Gave spell item [" + spellId + "] to " + player.getName().getString()), true);
								return 1;
							})
							.then(argument("ticks", IntegerArgumentType.integer(1))
									.executes(ctx -> {
										// Fixed duration mode
										ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
										int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
										SpellDefinition def = SpellRegistry.get(spellId);
										if (def == null) {
											ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
											return 0;
										}
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										ItemStack stack = DynamicSpellItem.createStackWithDuration(
												YHDanmaku.DYNAMIC_SPELL.get(), spellId, ticks);
										if (!player.getInventory().add(stack)) {
											player.drop(stack, false);
										}
										ctx.getSource().sendSuccess(
												() -> Component.literal("Gave spell item [" + spellId + "] (" + ticks + "t) to " + player.getName().getString()), true);
										return 1;
									}))))
				.then(literal("log")
						.then(literal("on")
								.executes(ctx -> {
									ParallelTicker.ENABLE_LOG = true;
									ParallelTicker.LOG_INTERVAL = 1;
									ctx.getSource().sendSuccess(() -> Component.literal("Ticker log enabled (every tick)"), true);
									return 1;
								})
								.then(argument("tickinterval", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											int interval = IntegerArgumentType.getInteger(ctx, "tickinterval");
											ParallelTicker.ENABLE_LOG = true;
											ParallelTicker.LOG_INTERVAL = interval;
											ctx.getSource().sendSuccess(() -> Component.literal("Ticker log enabled (every " + interval + " ticks)"), true);
											return 1;
										})
								)
						)
						.then(literal("off").executes(ctx -> {
							ParallelTicker.ENABLE_LOG = false;
							ctx.getSource().sendSuccess(() -> Component.literal("Ticker log disabled"), true);
							return 1;
						}))
				)
				.then(literal("async")
						.then(literal("on").executes(ctx -> {
							ParallelTicker.ENABLE_ASYNC = true;
							ctx.getSource().sendSuccess(() -> Component.literal("Async dispatch enabled"), true);
							return 1;
						}))
						.then(literal("off").executes(ctx -> {
							ParallelTicker.ENABLE_ASYNC = false;
							ctx.getSource().sendSuccess(() -> Component.literal("Async dispatch disabled"), true);
							return 1;
						}))
				)
		);
	}

	protected static LiteralArgumentBuilder<CommandSourceStack> literal(String str) {
		return LiteralArgumentBuilder.literal(str);
	}

	protected static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	private static int setStgResource(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String resourceName = StringArgumentType.getString(ctx, "resource");
		double amount = DoubleArgumentType.getDouble(ctx, "amount");
		StgResourceEvent.Resource resource;
		try {
			resource = parseStgResource(resourceName);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		if (add) {
			switch (resource) {
				case LIFE -> YHStgApi.addLife(player, amount);
				case BOMB -> YHStgApi.addBomb(player, amount);
				case POWER -> YHStgApi.addPower(player, amount);
				case POINTS -> YHStgApi.addPoints(player, amount);
			}
		} else {
			switch (resource) {
				case LIFE -> YHStgApi.setLife(player, amount);
				case BOMB -> YHStgApi.setBomb(player, amount);
				case POWER -> YHStgApi.setPower(player, amount);
				case POINTS -> YHStgApi.setPoints(player, amount);
			}
		}
		double value = switch (resource) {
			case LIFE -> YHStgApi.getLife(player);
			case BOMB -> YHStgApi.getBomb(player);
			case POWER -> YHStgApi.getPower(player);
			case POINTS -> YHStgApi.getPoints(player);
		};
		String action = add ? "Added " + formatStgValue(resource, amount) + " to" : "Set";
		ctx.getSource().sendSuccess(() -> Component.literal(
				action + " STG " + resource.name().toLowerCase(java.util.Locale.ROOT) +
						" for " + player.getName().getString() + " (now " + formatStgValue(resource, value) + ")"), true);
		return 1;
	}

	private static StgResourceEvent.Resource parseStgResource(String name) {
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "life" -> StgResourceEvent.Resource.LIFE;
			case "bomb" -> StgResourceEvent.Resource.BOMB;
			case "power" -> StgResourceEvent.Resource.POWER;
			case "points", "point" -> StgResourceEvent.Resource.POINTS;
			default -> throw new IllegalArgumentException("Unknown STG resource: " + name);
		};
	}

	private static String formatStgValue(StgResourceEvent.Resource resource, double value) {
		int decimals = switch (resource) {
			case LIFE, BOMB -> 1;
			case POWER, POINTS -> 2;
		};
		return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
	}

}
