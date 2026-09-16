package dev.xkmc.youkaishomecoming.content.spell.analysis;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellTestBootstrap;
import dev.xkmc.youkaishomecoming.content.spell.action.DataDrivenShooterSpell;
import dev.xkmc.youkaishomecoming.content.spell.action.DataDrivenTrailAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.LegacyTickerAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.feedback.NoopFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorController;
import dev.xkmc.youkaishomecoming.content.spell.runtime.ProjectileCallbackContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.events.SpellPermissionCommands;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Real command parsing, Codec, analyzer and action dispatch, without starting a game world. */
public final class SpellPermissionHotfixTest {

	private static final ResourceLocation ID = new ResourceLocation("yh_test", "permissions");
	private static final SpellCardRank RANK = SpellCardRank.LESSER_WISDOM;
	private static int checks;

	public static void main(String[] args) throws Exception {
		if (SpellTestBootstrap.enter(SpellPermissionHotfixTest.class, args)) return;
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		commandSyntax();
		permissionDefaults();
		runtimeDispatch();
		callbacksAndChildren();
		nonSpellValidation();
		System.out.println("SpellPermissionHotfixTest: " + checks + " checks passed");
	}

	private static void commandSyntax() throws Exception {
		var dispatcher = new CommandDispatcher<CommandSourceStack>();
		dispatcher.register(Commands.literal("yhspell").then(SpellPermissionCommands.create()));
		CommandSourceStack operator = source(2);
		for (int level = 0; level <= 4; level++) {
			check("direct player permission " + level, parses(dispatcher, operator, "yhspell permission Alice " + level));
			check("selector permission " + level, parses(dispatcher, operator, "yhspell permission @a " + level));
		}
		check("query without a level", parses(dispatcher, operator, "yhspell permission Alice"));
		check("reset without extra subcommands", parses(dispatcher, operator, "yhspell permission Alice reset"));
		for (String invalid : List.of("-1", "5", "base", "1 extra")) {
			check("invalid level rejected: " + invalid, !parses(dispatcher, operator, "yhspell permission Alice " + invalid));
		}
		check("ordinary player cannot grant permissions", !parses(dispatcher, source(0), "yhspell permission Alice 4"));
		var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("yhspell permission Alice ", operator)).get().getList();
		check("numeric completions plus reset", suggestions.stream().map(s -> s.getText()).collect(Collectors.toSet())
				.equals(Set.of("0", "1", "2", "3", "4", "reset")));
		check("each numeric completion explains its level", suggestions.stream()
				.filter(s -> !s.getText().equals("reset")).allMatch(s -> s.getTooltip() != null));
	}

	private static CommandSourceStack source(int level) {
		return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level,
				"test", Component.literal("test"), null, null);
	}

	private static boolean parses(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String text) {
		var parsed = dispatcher.parse(text, source);
		return !parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && parsed.getContext().getCommand() != null;
	}

	private static void permissionDefaults() {
		check("new player starts at base", SpellPermissionService.resolveLevel(-1, false, true, false, false) == 1);
		check("Tier 6 unlocks hooks", SpellPermissionService.resolveLevel(-1, false, true, true, false) == 2);
		check("Tier 12 unlocks EXP even without Tier 6", SpellPermissionService.resolveLevel(-1, false, true, false, true) == 3);
		check("OP default", SpellPermissionService.resolveLevel(-1, true, true, true, true) == 4);
		check("disabled progression keeps base", SpellPermissionService.resolveLevel(-1, false, false, true, true) == 1);
		for (int override = 0; override <= 4; override++) {
			for (boolean operator : List.of(false, true)) {
				check("manual level wins over OP and all achievements: " + override,
						SpellPermissionService.resolveLevel(override, operator, true, true, true) == override);
			}
		}
	}

	private static void runtimeDispatch() {
		FireDanmakuAction fire = basicFire();
		check("real Codec defaults to continuing collisions", fire.hitBehaviorEntity() == HitBehavior.CONTINUE
				&& fire.hitBehaviorBlock() == HitBehavior.CONTINUE);
		var holder = new RecordingHolder();
		var experimental = new SpellActions.SetVariable("target", new NumberProviders.TargetX());
		var conditional = new SpellActions.ConditionalAction(
				new SpellConditions.CompareNumbers(new NumberProviders.TargetX(), ">", NumberProvider.constant(0)),
				List.of(new SpellActions.SetVariable("branch", 1)), List.of(new SpellActions.SetVariable("branch", -1)));
		for (int level = 0; level <= 4; level++) {
			SpellRuntime runtime = runtime(level);
			SpellContext ctx = context(runtime, holder);
			ctx.executeList(List.of(new SpellActions.SequenceAction(List.of(
					new SpellActions.SetVariable("base", 1), experimental, conditional,
					new SpellActions.SetVariable("after", 1)))));
			check("base and later sibling execution at " + level,
					runtime.getVariable("base") == (level > 0 ? 1 : 0) && runtime.getVariable("after") == (level > 0 ? 1 : 0));
			check("EXP provider execution at " + level, runtime.getVariable("target") == (level >= 3 ? 7 : 0));
			check("denied conditional uses its false branch at " + level,
					runtime.getVariable("branch") == (level == 0 ? 0 : level >= 3 ? 1 : -1));
			checkEmission("base bullet at " + level, ctx, holder, fire, level >= 1);
			checkEmission("sized OP bullet at " + level, ctx, holder, fire.withSize(NumberProvider.constant(2)), level == 4);
		}
		int[] legacyTicks = {0};
		var legacy = new LegacyTickerAction(() -> new SpellCard() {
			@Override public void tick(CardHolder ignored) { legacyTicks[0]++; }
		});
		SpellRuntime trusted = new SpellRuntime(definition(List.of()));
		context(trusted, holder).executeList(List.of(legacy));
		check("trusted Boss/preview legacy firing still executes", legacyTicks[0] == 1);
		context(runtime(4), holder).executeList(List.of(legacy));
		check("unserializable legacy nodes stay denied in player JSON", legacyTicks[0] == 1);
		check("trusted continuation stays trusted", !trusted.continueWith(definition(List.of())).hasPlayerPermissions());
	}

	private static void callbacksAndChildren() throws Exception {
		var runtimeField = DataDrivenShooterSpell.class.getDeclaredField("runtime");
		runtimeField.setAccessible(true);
		for (int level = 0; level <= 4; level++) {
			SpellRuntime runtime = runtime(level);
			for (var kind : ProjectileCallbackContext.Kind.values()) {
				runtime.setVariable("hook", 0);
				var callback = ProjectileCallbackContext.point(kind, null, Vec3.ZERO, new Vec3(0, 0, 1),
						Vec3.ZERO, Vec3.ZERO, null, null, null);
				new DataDrivenTrailAction(List.of(new SpellActions.SetVariable("hook", 1)), runtime, runtime.getDefinition())
						.execute(null, callback);
				check(kind + " permission at " + level, runtime.getVariable("hook") == (level >= 2 ? 1 : 0));
			}
			SpellRuntime child = (SpellRuntime) runtimeField.get(new DataDrivenShooterSpell(List.of(), runtime));
			check("shooter inherits explicit player gate at " + level, child.hasPlayerPermissions() && child.permissionLevel() == level);
			check("continuation inherits player gate at " + level,
					runtime.continueWith(definition(List.of())).permissionLevel() == level);
		}
		var trustedChild = (SpellRuntime) runtimeField.get(new DataDrivenShooterSpell(List.of(), new SpellRuntime(definition(List.of()))));
		check("Boss child shooter stays trusted", !trustedChild.hasPlayerPermissions());
	}

	private static void nonSpellValidation() {
		FireDanmakuAction fire = basicFire();
		SpellDefinition basic = definition(List.of(fire));
		NonSpellValidator.validateForPlayer(basic, RANK, 0, 1);
		check("default basic non-spell passes at zero Power", project(basic, 0, 1).maxSpawnPerTick() == 1);
		check("ordinary analyzer agrees on basic output", SpellAnalyzer.analyze(basic, SpellAnalysisProfile.CERTIFICATION,
				SpellAnalysisLimits.certification()).maxSpawnPerTick() == 1);
		SpellDefinition hooks = definition(List.of(fire.withOnHitEntity(Optional.of(List.of(fire)))));
		NonSpellValidator.validateForPlayer(hooks, RANK, 0, 1);
		check("unavailable hook output cannot block base firing", project(hooks, 0, 1).maxSpawnPerTick() == 1);
		rejected("unlocked hook output still needs Power", () -> NonSpellValidator.validateForPlayer(hooks, RANK, 0, 2), "maxSpawnPerTick");
		NonSpellValidator.validateForPlayer(hooks, RANK, 4, 2);
		check("unlocked collision hook shares ordinary budget", project(hooks, 4, 2).hookExecutionUpperBound() > 0);
		SpellDefinition stateHooks = definition(List.of(fire.withOnTrail(Optional.of(List.of(new SpellActions.SetVariable("x", 1))))
				.withOnExpiry(Optional.of(List.of(new SpellActions.SetVariable("x", 2))))));
		NonSpellValidator.validateForPlayer(stateHooks, RANK, 0, 2);
		check("state and expiry callbacks use ordinary checks", project(stateHooks, 0, 2).hookExecutionUpperBound() > 0);
		SpellDefinition laser = definition(List.of(action("{\"type\":\"fire_laser\",\"color\":\"red\",\"lifetime\":60,\"length\":10}")));
		check("locked laser is omitted from output", project(laser, 0, 2).maxSpawnPerTick() == 0);
		NonSpellValidator.validateForPlayer(laser, RANK, 0, 3);
		check("EXP non-spell laser works at its ordinary cost", project(laser, 0, 3).maxSpawnPerTick() == 1);
		SpellAction shooter = action("{\"type\":\"spawn_shooter\",\"health\":1,\"damage\":1,\"lifetime\":20,\"count\":1,\"speed\":0,\"body\":["
				+ "{\"type\":\"fire_danmaku\",\"bullet\":\"ball\",\"color\":\"red\",\"count\":1,\"speed\":0.5,\"lifetime\":20}]}");
		SpellDefinition shooterSpell = definition(List.of());
		shooterSpell.phases.put(ID, new PhaseDefinition(ID, List.of(shooter), List.of(), List.of(), List.of(), List.of()));
		NonSpellValidator.validateForPlayer(shooterSpell, RANK, 2, 3);
		check("shooter and child bullets both count", project(shooterSpell, 2, 3).maxSpawnPerTick() == 2);
		rejected("default non-spell quota remains active", () -> NonSpellValidator.validateForPlayer(
				definition(List.of(fire.withCount(NumberProvider.constant(2)))), RANK, 0, 1), "maxSpawnPerTick");
		rejected("shared finite lifetime check remains active", () -> NonSpellValidator.validateForPlayer(
				definition(List.of(fire.withLifetime(new NumberProviders.Variable("unknown")))), RANK, 0, 4), "lifetime");
		rejected("broken nodes cannot pass cast validation", () -> NonSpellValidator.validateForPlayer(definition(List.of(
				action("{\"type\":\"broken\",\"raw\":\"bad\",\"error\":\"unknown action\"}"))), RANK, 0, 1), "broken");
		SpellAction initialization = new SetSpellHealthAction(SetSpellHealthAction.Mode.SET,
				NumberProvider.constant(50), NumberProvider.constant(100));
		SpellDefinition directInitialization = definition(List.of());
		directInitialization.phases.put(ID, new PhaseDefinition(ID, List.of(initialization),
				List.of(), List.of(), List.of(), List.of()));
		rejected("non-spell rejects direct spellcard_init", () ->
				NonSpellValidator.validateStructure(directInitialization), "spellcard_init");
		rejected("non-spell rejects nested spellcard_init", () -> NonSpellValidator.validateStructure(
				definition(List.of(new SpellActions.SequenceAction(List.of(initialization))))), "spellcard_init");
		SpellDefinition disabledInitialization = definition(List.of(new SpellActions.DisabledAction(initialization)));
		NonSpellValidator.validateStructure(disabledInitialization);
		check("disabled spellcard_init is inert", !SpellHealthPlan.hasHealthDeclaration(disabledInitialization));
		SpellDefinition normalInitialization = new SpellDefinition(ID,
				new SpellDisplay("Normal", "", Optional.empty(), Optional.empty()), SpellItemForm.NONE, ID,
				Map.of(ID, new PhaseDefinition(ID, List.of(initialization), List.of(), List.of(), List.of(), List.of())),
				DifficultyProfile.DEFAULT);
		SpellAnalyzer.analyzePlayerCast(normalInitialization, SpellAnalysisLimits.certification(), 0, 1);
		check("ordinary spell still accepts spellcard_init", SpellHealthPlan.hasHealthDeclaration(normalInitialization));
		SpellDefinition newNonSpell = SpellEditorController.createEmptySpellDefinition(
				new ResourceLocation("yh_test", "new_non_spell"), SpellCardType.NON_SPELL);
		SpellDefinition newNormal = SpellEditorController.createEmptySpellDefinition(
				new ResourceLocation("yh_test", "new_normal"), SpellCardType.NORMAL);
		check("new non-spell omits spellcard_init", !SpellHealthPlan.hasHealthDeclaration(newNonSpell));
		check("new ordinary spell keeps spellcard_init", SpellHealthPlan.hasHealthDeclaration(newNormal));
		check("limited tooltip detects nested unavailable hooks", SpellPermissionService.hasUnavailableCapabilities(hooks, 1));
		check("base card has no false permission warning", !SpellPermissionService.hasUnavailableCapabilities(basic, 1));
		check("empty optional hooks have no false warning", !SpellPermissionService.hasUnavailableCapabilities(
				definition(List.of(fire.withOnExpiry(Optional.of(List.of())).withOnTrail(Optional.of(List.of())))), 1));
		check("disabled nodes have no false warning", !SpellPermissionService.hasUnavailableCapabilities(
				definition(List.of(new SpellActions.DisabledAction(shooter))), 1));
		check("forbid warns even on a card with no classified nodes", SpellPermissionService.hasUnavailableCapabilities(definition(List.of()), 0));
	}

	private static SpellAnalysis project(SpellDefinition definition, double power, int level) {
		return SpellAnalyzer.analyzePlayerCast(definition, SpellAnalysisLimits.certification().withMaxSpawnPerTick(RANK.danmakuPerTick(power)), power, level);
	}

	private static FireDanmakuAction basicFire() {
		return (FireDanmakuAction) action("{\"type\":\"fire_danmaku\",\"bullet\":\"ball\",\"color\":\"red\",\"count\":1,\"speed\":0.5,\"lifetime\":60}");
	}

	private static SpellAction action(String json) {
		return SpellAction.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, error -> { throw new AssertionError(error); });
	}

	private static SpellDefinition definition(List<SpellAction> actions) {
		return new SpellDefinition(ID, new SpellDisplay("Permissions", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE.withCardType(SpellCardType.NON_SPELL), ID,
				Map.of(ID, new PhaseDefinition(ID, List.of(), actions, List.of(), List.of(), List.of())), DifficultyProfile.DEFAULT);
	}

	private static SpellRuntime runtime(int level) {
		var runtime = new SpellRuntime(definition(List.of()));
		runtime.setPermissionLevel(level);
		return runtime;
	}

	private static SpellContext context(SpellRuntime runtime, CardHolder holder) {
		return new SpellContext(holder, runtime.getDefinition(), runtime, DifficultyModifiers.DEFAULT, null, NoopFeedbackSink.INSTANCE);
	}

	private static void checkEmission(String label, SpellContext ctx, RecordingHolder holder, SpellAction action, boolean allowed) {
		int before = holder.emissions;
		try { ctx.executeList(List.of(action)); }
		catch (EmissionReached expected) { /* Stop at the real entity factory; no world is needed. */ }
		check(label, holder.emissions - before == (allowed ? 1 : 0));
	}

	private static void rejected(String label, Runnable run, String reason) {
		try { run.run(); }
		catch (SpellAnalysisException error) {
			check(label + ": " + error.getMessage(), error.getMessage().toLowerCase(java.util.Locale.ROOT).contains(reason.toLowerCase(java.util.Locale.ROOT)));
			return;
		}
		throw new AssertionError(label + " was accepted");
	}

	private static final class EmissionReached extends RuntimeException {}

	private static final class RecordingHolder implements CardHolder {
		int emissions;
		@Override public Vec3 center() { return Vec3.ZERO; }
		@Override public Vec3 forward() { return new Vec3(0, 0, 1); }
		@Override public Vec3 target() { return new Vec3(7, 0, 10); }
		@Override public RandomSource random() { return RandomSource.create(1); }
		@Override public LivingEntity self() { return null; }
		@Override public Vec3 targetVelocity() { return Vec3.ZERO; }
		@Override public LivingEntity targetEntity() { return null; }
		@Override public float getDamage(YHDanmaku.IDanmakuType type) { return 1; }
		@Override public ItemDanmakuEntity prepareDanmaku(int life, Vec3 direction, YHDanmaku.Bullet type, DanmakuColor color) {
			emissions++;
			throw new EmissionReached();
		}
		@Override public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 direction, float length, YHDanmaku.Laser type, DyeColor color) { throw new AssertionError(); }
		@Override public TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 direction, float size, String text, int color) { throw new AssertionError(); }
		@Override public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) { throw new AssertionError(); }
		@Override public void shoot(Entity entity) { throw new AssertionError(); }
	}

	private static void check(String label, boolean pass) {
		if (!pass) throw new AssertionError(label);
		checks++;
	}
}
