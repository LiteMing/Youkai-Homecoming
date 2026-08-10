package dev.xkmc.youkaishomecoming.events;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.fastprojectileapi.entity.ParallelTicker;
import dev.xkmc.fastprojectileapi.spellcircle.CustomSpellCircleStorage;
import dev.xkmc.fastprojectileapi.spellcircle.EntitySpellCircleManager;
import dev.xkmc.youkaishomecoming.compat.stg.StgCombatMode;
import dev.xkmc.youkaishomecoming.compat.stg.YHStgApi;
import dev.xkmc.youkaishomecoming.compat.stg.event.StgResourceEvent;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.SpellCardBlockHelper;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzerSelfCheck;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationManager;

import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellRewardService;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.certification.PendingRewardStorage;
import dev.xkmc.youkaishomecoming.content.spell.certification.SpellCertificate;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.market.OpenSpellMarketToClient;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketServerManager;
import dev.xkmc.youkaishomecoming.content.spell.runtime.CustomSpellStorage;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeAccess;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.content.spell.preview.OpenSpellPreviewToClient;
import dev.xkmc.youkaishomecoming.content.spell.template.SpellTemplates;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YHCommands {

	private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggestResource(
					SpellRegistry.getAll().keySet(),
					builder);
	private static final SuggestionProvider<CommandSourceStack> SPELL_TEMPLATE_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SpellTemplates.names(), builder);
	private static final SuggestionProvider<CommandSourceStack> SPELL_CIRCLE_SUGGESTIONS = (ctx, builder) -> {
		java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
		for (String key : YoukaisHomecoming.SPELL.getMerged().map.keySet()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null) {
				ids.add(id);
			}
		}
		return SharedSuggestionProvider.suggestResource(ids, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> STG_MODE_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(StgCombatMode.commandNames(), builder);
	private static final SuggestionProvider<CommandSourceStack> STG_RESOURCE_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.List.of("life", "bomb", "power", "points"), builder);
	private static final double DEFAULT_SPELL_STOP_RADIUS = 128.0;

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		CustomSpellStorage.loadAllIntoRegistry(event.getServer());
		CustomSpellCircleStorage.loadAllIntoConfig(event.getServer());
		SpellMarketServerManager.start(event.getServer());
		runHeadlessSelfTest(event.getServer());
	}

	/** Re-delivers pending certified rewards when the creator logs in (design doc §16). */
	@SubscribeEvent
	public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		String hash = PendingRewardStorage.peek(player.server, player.getUUID());
		if (hash == null) return;
		SpellCertificate certificate = CertifiedSpellStorage.loadCertificate(player.server, hash);
		SpellDefinition definition = CertifiedSpellStorage.loadDefinition(player.server, hash);
		if (certificate == null || definition == null) return;
		ItemStack stack = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), definition.id, true);
		CertifiedSpellValidator.tagCertified(stack, certificate);
		if (player.getInventory().add(stack)) {
			PendingRewardStorage.claim(player.server, player.getUUID(), hash);
			player.displayClientMessage(Component.literal("[YH] Pending certified spell delivered: " + definition.display.name()), false);
		} else {
			player.displayClientMessage(Component.literal("[YH] Certified reward waiting (inventory full): " + definition.display.name()), false);
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		SpellMarketServerManager.stop();
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(literal("danmaku")				.requires(e -> e.hasPermission(2))
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
										ctx.getSource().sendFailure(Component.literal(
												"Manual Bomb failed: no usable spell card or payment available"));
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
				.then(literal("market")
						.executes(ctx -> openSpellMarket(ctx.getSource()))
						.then(opLiteral("sync")
								.then(argument("tag", StringArgumentType.string())
										.executes(ctx -> syncMarketTag(ctx, false))))
						.then(opLiteral("prune")
								.then(argument("tag", StringArgumentType.string())
										.executes(ctx -> syncMarketTag(ctx, true))))
						.then(opLiteral("status")
								.then(argument("tag", StringArgumentType.string())
										.executes(YHCommands::marketStatus))))
				.then(opLiteral("circle")
						.then(circleSetCommand("set"))
						.then(circleSetCommand("on"))
						.then(literal("off")
								.then(argument("targets", EntityArgument.entities())
										.executes(YHCommands::hideCircleTargets)))
						.then(literal("clear")
								.then(argument("targets", EntityArgument.entities())
										.executes(YHCommands::clearCircleTargets))))
				.then(opLiteral("stop")
						.then(argument("targets", EntityArgument.entities())
								.executes(ctx -> stopSpellTargets(ctx, DEFAULT_SPELL_STOP_RADIUS))
								.then(argument("radius", DoubleArgumentType.doubleArg(0))
										.executes(ctx -> stopSpellTargets(ctx, DoubleArgumentType.getDouble(ctx, "radius"))))))
				.then(opLiteral("set")
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
				.then(opLiteral("phase")
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
				.then(opLiteral("variable")
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
			.then(opLiteral("reset")
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
									if (SpellRegistry.getOrigin(id) == SpellRegistry.Origin.MARKET) continue;
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
										if (SpellRegistry.getDefault(id) == null &&
												SpellRegistry.getOrigin(id) != SpellRegistry.Origin.MARKET) {
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
				.then(opLiteral("debug")
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
				.then(opLiteral("list")
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
				.then(opLiteral("preview")
						.then(argument("spell_id", ResourceLocationArgument.id())
								.suggests(SPELL_SUGGESTIONS)
								.executes(ctx -> {
									ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
									SpellDefinition def = SpellRegistry.get(spellId);
									if (def == null) {
										ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
										return 0;
									}
									openSpellPreview(ctx.getSource(), def);
									ctx.getSource().sendSuccess(() -> Component.literal("Opening preview for " + spellId), false);
									return 1;
								})))
				.then(opLiteral("editor")
						.executes(ctx -> {
							openDraftSpellEditor(ctx.getSource());
							ctx.getSource().sendSuccess(() -> Component.literal("Opening spell editor"), false);
							return 1;
						}))
				.then(opLiteral("reapply")
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
				.then(opLiteral("export")
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
										java.io.File file = CustomSpellStorage.saveGlobalSpell(def);
										ctx.getSource().sendSuccess(
												() -> Component.literal("Exported global spell " + spellId + " to " + file.getPath()), true);
										return 1;
									} catch (Exception e) {
										ctx.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
										return 0;
									}
								})))
				.then(opLiteral("patch")
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
				.then(opLiteral("import")
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
				.then(opLiteral("new")
						.then(argument("spell_or_template", StringArgumentType.word())
								.suggests(SPELL_TEMPLATE_SUGGESTIONS)
								.executes(ctx -> createNewSpell(ctx, StringArgumentType.getString(ctx, "spell_or_template"), null))
								.then(argument("template", StringArgumentType.word())
										.suggests(SPELL_TEMPLATE_SUGGESTIONS)
										.executes(ctx -> createNewSpell(ctx,
												StringArgumentType.getString(ctx, "spell_or_template"),
												StringArgumentType.getString(ctx, "template"))))))
				.then(opLiteral("proxy")
						.then(literal("entity")
								.then(argument("host", EntityArgument.entity())
										.then(argument("spell_id", ResourceLocationArgument.id())
												.suggests(SPELL_SUGGESTIONS)
												.executes(ctx -> spawnEntitySpellProxy(ctx, DynamicSpellItem.DURATION_NATURAL))
												.then(argument("ticks", IntegerArgumentType.integer(1))
														.executes(ctx -> spawnEntitySpellProxy(ctx,
																IntegerArgumentType.getInteger(ctx, "ticks")))))))
						.then(literal("fixed")
								.then(argument("spell_id", ResourceLocationArgument.id())
										.suggests(SPELL_SUGGESTIONS)
										.executes(ctx -> spawnFixedSpellProxy(ctx, DynamicSpellItem.DURATION_NATURAL))
										.then(argument("ticks", IntegerArgumentType.integer(1))
												.executes(ctx -> spawnFixedSpellProxy(ctx,
														IntegerArgumentType.getInteger(ctx, "ticks")))))))
			.then(opLiteral("give")
					.then(argument("spell_id", ResourceLocationArgument.id())
							.suggests(SPELL_SUGGESTIONS)
							.executes(ctx -> giveDynamicSpell(ctx, DynamicSpellItem.DURATION_NATURAL, false))
							.then(argument("ticks", IntegerArgumentType.integer(1))
									.executes(ctx -> giveDynamicSpell(ctx,
											IntegerArgumentType.getInteger(ctx, "ticks"), false))
									.then(argument("single_use", BoolArgumentType.bool())
											.executes(ctx -> giveDynamicSpell(ctx,
													IntegerArgumentType.getInteger(ctx, "ticks"),
													BoolArgumentType.getBool(ctx, "single_use")))))
							.then(argument("single_use", BoolArgumentType.bool())
									.executes(ctx -> giveDynamicSpell(ctx,
											DynamicSpellItem.DURATION_NATURAL,
											BoolArgumentType.getBool(ctx, "single_use"))))))
				.then(opLiteral("log")
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
				.then(opLiteral("async")
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

		// /yhdev developer commands
		event.getDispatcher().register(literal("yhdev")				.requires(e -> e.hasPermission(2))
				.then(literal("spell_analyzer_self_test")
						.executes(ctx -> runAnalyzerSelfTest(ctx.getSource())))
				.then(literal("certification")
						.then(literal("boss")
								// OP/console: summon a boss spell certification using ANY
								// registered spell (built-in cards included). Non-OP players
								// are limited to self-made spell cards via the in-game UI path.
								.then(argument("targets", EntityArgument.players())
										.then(argument("spell", ResourceLocationArgument.id())
												.suggests(SPELL_SUGGESTIONS)
										.then(argument("ticks", IntegerArgumentType.integer(100, 60000))
												.executes(ctx -> runCertificationTest(ctx, false))
												.then(argument("halfSize", DoubleArgumentType.doubleArg(4, 256))
														.executes(ctx -> runCertificationTest(ctx, false)))))))
						.then(literal("test")
								.then(argument("targets", EntityArgument.players())
										.then(argument("spell", ResourceLocationArgument.id())
												.suggests(SPELL_SUGGESTIONS)
										.then(argument("ticks", IntegerArgumentType.integer(100, 60000))
												.then(argument("halfSize", DoubleArgumentType.doubleArg(4, 256))
														.executes(ctx -> runCertificationTest(ctx, true))
														.then(argument("breakHealth", FloatArgumentType.floatArg(1, 1_000_000))
																.executes(ctx -> runCertificationTest(ctx, true))))))))
						.then(literal("abort")
								.then(argument("targets", EntityArgument.players())
										.executes(ctx -> abortCertification(ctx))))
						.then(literal("claim")
								.then(argument("targets", EntityArgument.players())
										.executes(ctx -> claimCertifiedRewards(ctx))))));

		// /yhspell capability commands (Phase 6)
		event.getDispatcher().register(literal("yhspell")
				.then(opLiteral("capability")
						.then(literal("get")
								.then(argument("id", StringArgumentType.string())
										.executes(ctx -> capabilityGet(ctx))))
						.then(literal("set")
								.then(argument("id", StringArgumentType.string())
										.then(argument("policy", StringArgumentType.string())
												.suggests((ctx2, builder) -> SharedSuggestionProvider.suggest(
														new String[]{"allow", "experimental", "deny", "op_only"}, builder))
												.executes(ctx -> capabilitySet(ctx)))))));
	}

	private static int capabilityGet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		SpellCapability cap = SpellCapability.byId(StringArgumentType.getString(ctx, "id"));
		ctx.getSource().sendSystemMessage(Component.literal("[YH] capability " + cap.id() + " policy: "
				+ SpellCapabilityPolicies.currentPolicy(cap)));
		return 1;
	}

	private static int capabilitySet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		try {
			SpellCapability cap = SpellCapability.byId(StringArgumentType.getString(ctx, "id"));
			String policyName = StringArgumentType.getString(ctx, "policy");
			SpellCapabilityPolicy policy = switch (policyName) {
				case "allow" -> SpellCapabilityPolicy.ALLOW;
				case "experimental" -> SpellCapabilityPolicy.EXPERIMENTAL;
				case "deny" -> SpellCapabilityPolicy.DENY;
				case "op_only" -> SpellCapabilityPolicy.OP_ONLY;
				default -> throw new IllegalArgumentException("unknown policy: " + policyName);
			};
			SpellCapabilityPolicies.setPolicy(cap, policy);
			ctx.getSource().sendSystemMessage(Component.literal("[YH] capability " + cap.id()
					+ " policy set to " + SpellCapabilityPolicies.currentPolicy(cap)));
			return 1;
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] " + e.getMessage()));
			return 0;
		}
	}

	private static int runCertificationTest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
			boolean noHitDefault) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = EntityArgument.getPlayer(ctx, "targets");
		var spellId = ResourceLocationArgument.getId(ctx, "spell");
		int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
		double halfSize = 8.0;
		try {
			halfSize = DoubleArgumentType.getDouble(ctx, "halfSize");
		} catch (IllegalArgumentException ignored) {
			// optional on the boss path; defaults to the config-clamped 8
		}
		Float breakHealth = null;
		try {
			breakHealth = FloatArgumentType.getFloat(ctx, "breakHealth");
		} catch (IllegalArgumentException ignored) {
			// certification test defaults to an invulnerable no-hit survival trial
		}
		var definition = SpellRegistry.get(spellId);
		if (definition == null) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] unknown spell: " + spellId));
			return 0;
		}
		try {
			var quote = CertificationService.quoteOperatorTest(player, definition, ticks, halfSize);
			CertificationManager.INSTANCE.setQuote(player, quote, definition);
			// OP test command: skip the start fee so certification can be exercised
			// without resources; the in-game UI path pays through SpellPaymentRouter.
			if (!YHModConfig.COMMON.certificationEnabled.get()) {
				ctx.getSource().sendSystemMessage(Component.literal("[YH] certification disabled in config"));
				return 0;
			}
			if (dev.xkmc.youkaishomecoming.content.capability.GrazeCapability.HOLDER.get(player).isInSession()
					|| !dev.xkmc.youkaishomecoming.content.capability.GrazeCapability.HOLDER.get(player)
					.snapshotOpponents().ids().isEmpty()) {
				ctx.getSource().sendSystemMessage(Component.literal("[YH] player is in a real battle (D15)"));
				return 0;
			}
			if (CertificationManager.INSTANCE.hasActiveTrial(player)) {
				ctx.getSource().sendSystemMessage(Component.literal("[YH] player already has an active certification trial"));
				return 0;
			}
			if (!noHitDefault && breakHealth == null) breakHealth = (float) quote.spellHp();
			boolean started = CertificationService.startFree(player, quote, breakHealth);
			ctx.getSource().sendSystemMessage(Component.literal("[YH] certification started=" + started
					+ " cost=" + quote.startCostUnits() + " breakHealth="
					+ (breakHealth == null ? "no-hit" : breakHealth)
					+ " hash=" + quote.definitionHash().substring(0, 8)));
			return started ? 1 : 0;
		} catch (Exception e) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] certification failed: " + e.getMessage()));
			return 0;
		}
	}

	private static int claimCertifiedRewards(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = EntityArgument.getPlayer(ctx, "targets");
		String hash = PendingRewardStorage.peek(player.server, player.getUUID());
		if (hash == null) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] no pending certified rewards"));
			return 0;
		}
		SpellCertificate certificate = CertifiedSpellStorage.loadCertificate(player.server, hash);
		SpellDefinition definition = CertifiedSpellStorage.loadDefinition(player.server, hash);
		if (certificate == null || definition == null) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] pending reward data missing"));
			return 0;
		}
		ItemStack stack = CertifiedSpellRewardService.buildCertifiedStack(player.server, certificate, definition);
		if (player.getInventory().add(stack)) {
			PendingRewardStorage.claim(player.server, player.getUUID(), hash);
			ctx.getSource().sendSystemMessage(Component.literal("[YH] claimed: " + definition.display.name()));
			return 1;
		}
		ctx.getSource().sendSystemMessage(Component.literal("[YH] inventory full"));
		return 0;
	}

	private static int abortCertification(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = EntityArgument.getPlayer(ctx, "targets");
		var trial = CertificationManager.INSTANCE.getActiveTrial(player);
		if (trial == null) {
			ctx.getSource().sendSystemMessage(Component.literal("[YH] no active certification for player"));
			return 0;
		}
		trial.abort();
		ctx.getSource().sendSystemMessage(Component.literal("[YH] certification aborted"));
		return 1;
	}

	private static int runAnalyzerSelfTest(CommandSourceStack source) {
		var result = SpellAnalyzerSelfCheck.run(source.getPlayer());
		source.sendSystemMessage(Component.literal("[YH] spell analyzer self-test: " + result.passed() + "/" + result.total() + " passed"));
		if (!result.allPassed()) {
			for (String failure : result.failures()) {
				source.sendSystemMessage(Component.literal("[YH] FAIL: " + failure));
			}
		}
		return result.allPassed() ? 1 : 0;
	}

	/**
	 * Headless self-test for scripts/CI — no client/chat interaction required.
	 * Trigger with system property {@code -Dyhdev.selftest=true} OR environment
	 * variable {@code YHDEV_SELFTEST=1}; add {@code yhdev.selftest.stop} /
	 * {@code YHDEV_SELFTEST_STOP=1} to halt the server right after. Results are
	 * logged and written to {@code <serverDir>/yhdev-selftest-result.txt}.
	 */
	private static void runHeadlessSelfTest(MinecraftServer server) {
		boolean enabled = SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST");
		if (!enabled) return;
		var result = SpellAnalyzerSelfCheck.run();
		StringBuilder sb = new StringBuilder("[YH] spell analyzer self-test: ")
				.append(result.passed()).append("/").append(result.total()).append(" passed");
		if (!result.allPassed()) {
			for (String failure : result.failures()) {
				sb.append('\n').append("[YH] FAIL: ").append(failure);
			}
		}
		YoukaisHomecoming.LOGGER.info(sb.toString());
		try {
			java.nio.file.Path out = server.getServerDirectory().toPath().resolve("yhdev-selftest-result.txt");
			java.nio.file.Files.writeString(out, sb.toString());
		} catch (Exception e) {
			YoukaisHomecoming.LOGGER.error("Failed to write selftest result file", e);
		}
		if (SpellSelfTestFlags.enabled("yhdev.selftest.stop", "YHDEV_SELFTEST_STOP")) {
			server.halt(false);
		}
	}
	protected static LiteralArgumentBuilder<CommandSourceStack> literal(String str) {
		return LiteralArgumentBuilder.literal(str);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> opLiteral(String str) {
		return literal(str).requires(e -> e.hasPermission(2));
	}

	protected static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> circleSetCommand(String name) {
		return literal(name)
				.then(argument("targets", EntityArgument.entities())
						.then(argument("circle_id", ResourceLocationArgument.id())
								.suggests(SPELL_CIRCLE_SUGGESTIONS)
								.executes(ctx -> setCircleTargets(ctx, 1.0f))
								.then(argument("size", FloatArgumentType.floatArg(0.0f, 64.0f))
										.executes(ctx -> setCircleTargets(ctx,
												FloatArgumentType.getFloat(ctx, "size"))))));
	}

	private static int setCircleTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
										float size)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var targets = EntityArgument.getEntities(ctx, "targets");
		ResourceLocation circle = ResourceLocationArgument.getId(ctx, "circle_id");
		if (!YoukaisHomecoming.SPELL.getMerged().map.containsKey(circle.toString())) {
			ctx.getSource().sendFailure(Component.literal("Unknown spell circle: " + circle));
			return 0;
		}
		for (Entity entity : targets) {
			EntitySpellCircleManager.setOverride(entity, circle, size);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Set spell circle " + circle +
				" on " + targets.size() + " entities"), true);
		return targets.size();
	}

	private static int hideCircleTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var targets = EntityArgument.getEntities(ctx, "targets");
		for (Entity entity : targets) {
			EntitySpellCircleManager.setHidden(entity);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Hidden spell circle on " +
				targets.size() + " entities"), true);
		return targets.size();
	}

	private static int clearCircleTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var targets = EntityArgument.getEntities(ctx, "targets");
		for (Entity entity : targets) {
			EntitySpellCircleManager.clearOverride(entity);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared spell circle override on " +
				targets.size() + " entities"), true);
		return targets.size();
	}

	private static int stopSpellTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
										double radius)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var targets = EntityArgument.getEntities(ctx, "targets");
		StopResult result = new StopResult();
		Set<UUID> stoppedEntities = new HashSet<>();
		Set<UUID> erasedProjectiles = new HashSet<>();
		for (Entity target : targets) {
			stopSpellEntity(target, result, stoppedEntities);
			stopSpellHostsAround(target, radius, result, stoppedEntities);
			eraseLooseDanmakuAround(target, radius, result, erasedProjectiles);
		}
		if (result.erased > 0) {
			DanmakuManager.flushErases();
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + result.stopped +
				" spell hosts and erased " + result.erased + " danmaku"), true);
		return result.stopped + result.erased;
	}

	private static void stopSpellHostsAround(Entity centerEntity, double radius, StopResult result,
											 Set<UUID> stoppedEntities) {
		if (!(centerEntity.level() instanceof ServerLevel level)) {
			return;
		}
		AABB area = new AABB(centerEntity.position(), centerEntity.position()).inflate(Math.max(0, radius));
		for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
				e -> e instanceof SpellRuntimeHost || e instanceof ServerPlayer)) {
			stopSpellEntity(entity, result, stoppedEntities);
		}
	}

	private static void stopSpellEntity(Entity entity, StopResult result, Set<UUID> stoppedEntities) {
		if (!stoppedEntities.add(entity.getUUID())) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			SpellContainer.clear(player);
			result.stopped++;
		}
		if (entity instanceof DanmakuProxyEntity proxy) {
			result.erased += proxy.eraseAllDanmakuAndCount(null);
			proxy.cleanup();
			result.stopped++;
			return;
		}
		if (entity instanceof EntitySpellProxyEntity proxy) {
			result.erased += proxy.eraseAllDanmakuAndCount(null);
			proxy.cleanup();
			result.stopped++;
			return;
		}
		if (entity instanceof YoukaiEntity youkai) {
			result.erased += youkai.eraseAllDanmakuAndCount(null);
			youkai.spellCard = null;
			youkai.setSpellRuntime(null);
			result.stopped++;
			return;
		}
		if (entity instanceof SpellRuntimeHost host) {
			host.eraseDanmaku(null);
			host.setSpellRuntime(null);
			host.syncSpellState();
			result.stopped++;
		}
	}

	private static void eraseLooseDanmakuAround(Entity centerEntity, double radius, StopResult result,
												Set<UUID> erasedProjectiles) {
		if (!(centerEntity.level() instanceof ServerLevel level)) {
			return;
		}
		AABB area = new AABB(centerEntity.position(), centerEntity.position()).inflate(Math.max(0, radius));
		for (SimplifiedProjectile projectile : level.getEntitiesOfClass(SimplifiedProjectile.class, area)) {
			if (!(projectile instanceof IYHDanmaku)) {
				continue;
			}
			if (!erasedProjectiles.add(projectile.getUUID())) {
				continue;
			}
			projectile.markErased(true);
			result.erased++;
		}
	}

	private static final class StopResult {
		private int stopped;
		private int erased;
	}

	private static int createNewSpell(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
									  String spellOrTemplate, @Nullable String explicitTemplate)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String template = explicitTemplate;
		ResourceLocation spellId;
		if (template == null && SpellTemplates.contains(spellOrTemplate)) {
			template = spellOrTemplate;
			spellId = SpellTemplates.defaultId(template);
		} else {
			spellId = ResourceLocation.tryParse(spellOrTemplate);
			if (spellId == null) {
				ctx.getSource().sendFailure(Component.literal("Invalid spell id: " + spellOrTemplate));
				return 0;
			}
		}
		if (template != null && !SpellTemplates.contains(template)) {
			ctx.getSource().sendFailure(Component.literal("Unknown spell template: " + template +
					" (available: " + String.join(", ", SpellTemplates.names()) + ")"));
			return 0;
		}
		if (SpellRegistry.get(spellId) != null) {
			ctx.getSource().sendFailure(Component.literal("Spell already exists: " + spellId));
			return 0;
		}
		SpellDefinition def;
		try {
			def = template == null ? SpellTemplates.empty(spellId) : SpellTemplates.create(spellId, template);
		} catch (Exception e) {
			ctx.getSource().sendFailure(Component.literal("Failed to create spell template: " + e.getMessage()));
			return 0;
		}
		SpellRegistry.register(def);
		CustomSpellStorage.saveSpell(ctx.getSource().getServer(), def);
		String source = template == null ? "empty spell" : "template '" + template + "'";
		ctx.getSource().sendSuccess(
				() -> Component.literal("Created new " + source + ": " + spellId + " (use /yhspell preview to edit)"), true);
		openSpellPreview(ctx.getSource(), def);
		return 1;
	}

	private static int giveDynamicSpell(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
										 int duration, boolean singleUse)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack stack = duration == DynamicSpellItem.DURATION_NATURAL
				? DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId, singleUse)
				: DynamicSpellItem.createStackWithDuration(YHDanmaku.DYNAMIC_SPELL.get(), spellId, duration, singleUse);
		// OP-given cards are complete: right-click casts directly, never the editor.
		DynamicSpellItem.setComplete(stack, true);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		String detail = formatDuration(duration) + (singleUse ? ", single-use" : "");
		ctx.getSource().sendSuccess(
				() -> Component.literal("Gave spell item [" + spellId + "]" + detail + " to " + player.getName().getString()), true);
		return 1;
	}

	private static int spawnEntitySpellProxy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int duration)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Entity host = EntityArgument.getEntity(ctx, "host");
		ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
		SpellDefinition def = getSpellOrReport(ctx, spellId);
		if (def == null) return 0;
		LivingEntity target = host instanceof Mob mob ? mob.getTarget() : null;
		var proxy = SpellCardBlockHelper.spawnProxy(host, def, duration, target);
		if (proxy == null) {
			ctx.getSource().sendFailure(Component.literal("Failed to spawn spell proxy"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Spawned spell proxy " + proxy.getId() +
				" on " + host.getDisplayName().getString() + " with " + spellId + formatDuration(duration)), true);
		return 1;
	}

	private static int spawnFixedSpellProxy(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int duration) {
		ResourceLocation spellId = ResourceLocationArgument.getId(ctx, "spell_id");
		SpellDefinition def = getSpellOrReport(ctx, spellId);
		if (def == null) return 0;
		var source = ctx.getSource();
		var rotation = source.getRotation();
		var proxy = SpellCardBlockHelper.spawnFixedProxy(source.getLevel(), source.getPosition(),
				rotation.y, rotation.x, def, duration, null);
		ctx.getSource().sendSuccess(() -> Component.literal("Spawned fixed spell proxy " + proxy.getId() +
				" with " + spellId + formatDuration(duration)), true);
		return 1;
	}

	@Nullable
	private static SpellDefinition getSpellOrReport(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
													ResourceLocation spellId) {
		SpellDefinition def = SpellRegistry.get(spellId);
		if (def == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown spell: " + spellId));
		}
		return def;
	}

	private static String formatDuration(int duration) {
		return duration < 0 ? " until natural end" : " for " + duration + "t";
	}

	private static void openDraftSpellEditor(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		YoukaisHomecoming.HANDLER.toClientPlayer(OpenSpellPreviewToClient.draftEditor(), player);
	}

	private static int openSpellMarket(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		YoukaisHomecoming.HANDLER.toClientPlayer(new OpenSpellMarketToClient(), player);
		return 1;
	}

	private static int syncMarketTag(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean prune) {
		String tag = StringArgumentType.getString(ctx, "tag");
		var manager = SpellMarketServerManager.get(ctx.getSource().getServer());
		var future = prune ? manager.pruneTag(tag) : manager.syncTag(tag, true);
		ctx.getSource().sendSuccess(() -> Component.literal((prune ? "Pruning" : "Synchronizing") +
				" market tag '" + tag + "' asynchronously"), false);
		future.thenAccept(result -> ctx.getSource().getServer().execute(() -> {
			String summary = "Market tag '" + result.tag + "': added=" + result.added +
					", updated=" + result.updated + ", unchanged=" + result.unchanged +
					", removed=" + result.removed + ", rejected=" + result.rejected;
			if (result.success) ctx.getSource().sendSuccess(() -> Component.literal(summary), true);
			else ctx.getSource().sendFailure(Component.literal(summary + " errors=" + result.errors));
		}));
		return 1;
	}

	private static int marketStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		String tag = StringArgumentType.getString(ctx, "tag");
		var entries = SpellMarketServerManager.get(ctx.getSource().getServer()).listByTag(tag);
		ctx.getSource().sendSuccess(() -> Component.literal("Managed market tag '" + tag + "': " + entries.size() + " spells"), false);
		for (var entry : entries) {
			ctx.getSource().sendSystemMessage(Component.literal("  " + entry.localSpellId + " <- " + entry.marketUuid +
					" sha256=" + entry.contentHash));
		}
		return entries.size();
	}

	private static void openSpellPreview(CommandSourceStack source, SpellDefinition definition) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		// Always stream full server definition (chunked if > writeUtf limit)
		OpenSpellPreviewToClient.sendPreview(player, definition);
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
