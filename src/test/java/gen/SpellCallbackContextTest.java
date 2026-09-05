package gen;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.LaserBlockHitEffect;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.LaserHitDispositionEffect;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.LaserHitCallbackGate;
import dev.xkmc.youkaishomecoming.content.spell.action.ContinueSourceAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.ColorProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.GroupRotation;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberExprParser;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.preview.ActionListPanel;
import dev.xkmc.youkaishomecoming.content.spell.runtime.ProjectileCallbackContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Narrow executable contracts for 0.26.3 callback and viewport math. */
public final class SpellCallbackContextTest {

	private static int checks;

	private SpellCallbackContextTest() {
	}

	public static void main(String[] args) {
		testLaserHitGeometryAndDisposition();
		testCallbackExpressionsAndDelayedSnapshot();
		testActionPathsIgnoreVisualCollapse();
		testOriginAndGroupRotationMath();
		System.out.println("SpellCallbackContextTest: all " + checks + " checks passed");
	}

	private static void testLaserHitGeometryAndDisposition() {
		LaserHitCallbackGate gate = new LaserHitCallbackGate();
		check("LASER_ON_HIT_CALLBACK_IS_ONE_SHOT", gate.tryConsume() && !gate.tryConsume()
				&& gate.consumed());

		Vec3 source = new Vec3(3, 4, 5);
		Vec3 velocity = new Vec3(0.25, 0, 0.5);
		Vec3 movementStart = new Vec3(2.75, 4, 4.5);
		Vec3 movementEnd = source;
		Vec3 direction = new Vec3(0, 0, 1);
		Vec3 laserStart = new Vec3(3, 4.5, 5);
		Vec3 laserEnd = new Vec3(3, 4.5, 25);
		Vec3 clippedEnd = new Vec3(3, 4.5, 12);
		Vec3 hit = clippedEnd;
		Vec3 normal = new Vec3(0, 0, -1);

		SpellHitContext context = SpellHitContext.laserHit(null, SpellHitContext.HitType.BLOCK,
				source, velocity, movementStart, movementEnd, direction, laserStart,
				laserEnd, clippedEnd, hit, normal, null);
		ProjectileCallbackContext callback = context.callbackContext().orElseThrow();
		SpellContext spellContext = new SpellContext(null, null, null, DifficultyModifiers.DEFAULT,
				null, callback);
		check("LASER_BLOCK_HIT_HAS_SPELL_HIT_CONTEXT", context.hitType() == SpellHitContext.HitType.BLOCK);
		check("CALLBACK_POSITION_IS_HOOK_POINT", near(callback.position(), hit));
		check("CALLBACK_SOURCE_POSITION_IS_LASER_ANCHOR", near(callback.sourcePosition(), source));
		check("LASER_START_IS_BEAM_START", near(callback.laserStart(), laserStart));
		check("LASER_END_IS_UNCLIPPED_END", near(callback.laserEnd(), laserEnd));
		check("LASER_CLIPPED_END_IS_BLOCK_HIT", near(callback.laserClippedEnd(), clippedEnd));
		check("SOURCE_END_READS_UNCLIPPED_LASER_END",
				spellContext.callbackValue("source_end_z") == laserEnd.z);
		check("SOURCE_CLIPPED_END_READS_VISIBLE_LASER_END",
				spellContext.callbackValue("source_clipped_end_z") == clippedEnd.z);

		ProjectileCallbackContext expiry = callback.asExpiry(hit, velocity);
		check("LASER_EXPIRE_RETAINS_HIT_GEOMETRY",
				expiry.kind() == ProjectileCallbackContext.Kind.EXPIRY
						&& near(expiry.position(), hit)
						&& near(expiry.laserStart(), laserStart)
						&& near(expiry.laserEnd(), laserEnd)
						&& near(expiry.laserClippedEnd(), clippedEnd));

		check("LASER_CONTINUE_SOURCE_SKIPS_DEFAULT_DISCARD",
				LaserHitDispositionEffect.from(SpellHitContext.HitDisposition.CONTINUE)
						== LaserHitDispositionEffect.KEEP);
		check("LASER_BLOCK_FALLBACK_CONTINUE_PASSES_THROUGH",
				LaserBlockHitEffect.from(HitBehavior.CONTINUE) == LaserBlockHitEffect.PASS_THROUGH);
		check("LASER_PASS_THROUGH_RETAINS_FULL_LENGTH",
				LaserBlockHitEffect.PASS_THROUGH.visibleLength(40, 12.5) == 40);
		check("LASER_NON_CONTINUE_RETAINS_CLIPPED_PREFIX",
				LaserBlockHitEffect.CLIP_ONLY.visibleLength(40, 12.5) == 12.5);
		check("LASER_DISCARD_SOURCE_SUPPRESSES_EXPIRY",
				LaserHitDispositionEffect.from(SpellHitContext.HitDisposition.DISCARD)
						== LaserHitDispositionEffect.DISCARD);
		check("LASER_EXPIRE_SOURCE_RUNS_EXPIRY",
				LaserHitDispositionEffect.from(SpellHitContext.HitDisposition.EXPIRE)
						== LaserHitDispositionEffect.EXPIRE);
		check("LASER_POINT_ONLY_CONTROLS_STAY_UNRESOLVED",
				LaserHitDispositionEffect.from(SpellHitContext.HitDisposition.BOUNCE)
						== LaserHitDispositionEffect.UNRESOLVED
						&& LaserHitDispositionEffect.from(SpellHitContext.HitDisposition.HOLD)
						== LaserHitDispositionEffect.UNRESOLVED);
	}

	private static void testCallbackExpressionsAndDelayedSnapshot() {
		String[] aliases = {
				"source_speed", "source_position_x", "source_velocity_y", "source_direction_z",
				"source_size", "source_spread", "source_lifetime", "source_hook_x", "hook_x", "hookpos_z", "hit_y",
				"start_z", "source_end_x", "source_clipped_end_y", "vx"
		};
		for (String alias : aliases) {
			NumberProvider parsed = NumberExprParser.parse(alias);
			check("CALLBACK_EXPRESSION_ALIAS_" + alias.toUpperCase(),
					parsed instanceof NumberProviders.CallbackValue value && alias.equals(value.key())
							&& alias.equals(NumberExprParser.unparse(parsed)));
		}
		check("LEGACY_END_CALLBACK_NAME_IS_REJECTED", NumberExprParser.parse("end_x") == null);
		check("LEGACY_LASER_END_CALLBACK_NAME_IS_REJECTED",
				NumberExprParser.parse("laser_end_x") == null);
		check("LEGACY_CLIPPED_END_CALLBACK_NAME_IS_REJECTED",
				NumberExprParser.parse("clipped_end_y") == null);
		check("LEGACY_LASER_CLIPPED_END_CALLBACK_NAME_IS_REJECTED",
				NumberExprParser.parse("laser_clipped_end_y") == null);

		ColorProvider sourceColor = ColorProvider.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("source_color"))
				.result().orElseThrow();
		check("SOURCE_COLOR_CODEC_IS_EXPLICIT", sourceColor instanceof ColorProvider.SourceColor);

		SpellDefinition definition = minimalDefinition();
		SpellRuntime runtime = new SpellRuntime(definition);
		Vec3 hook = new Vec3(17, 18, 19);
		ProjectileCallbackContext callback = ProjectileCallbackContext.point(
				ProjectileCallbackContext.Kind.TRAIL, null, hook, new Vec3(1, 0, 0),
				new Vec3(16, 18, 19), hook, null, null, null);
		runtime.schedulePersistentDelayed(0, List.of(new SpellActions.SetVariable(
				"delayed_hook", new NumberProviders.CallbackValue("hook_x"))), callback);
		runtime.tickDelayed(null);
		check("DELAY_AND_BURST_RETAIN_CALLBACK_SNAPSHOT", runtime.getVariable("delayed_hook") == 17.0);
	}

	private static void testActionPathsIgnoreVisualCollapse() {
		var leaf = new ContinueSourceAction();
		var delay = new DelayAction(NumberProvider.constant(1), List.of(leaf));
		var repeat = new SpellActions.RepeatAction(NumberProvider.constant(2), "i", List.of(delay));
		ResourceLocation phaseId = new ResourceLocation("youkaishomecoming", "callback_path_test");
		PhaseDefinition phase = new PhaseDefinition(phaseId, List.of(), List.of(repeat),
				List.of(), List.of(), List.of());
		ActionListPanel panel = new ActionListPanel((action, path) -> {}, target -> {}, () -> {}, () -> null);
		panel.setPhase(phase);
		List<ActionListPanel.ActionEntry> entries = panel.getActionEntries();
		check("ACTION_PATH_ENUMERATES_COLLAPSED_DESCENDANTS", entries.size() == 3);
		check("ACTION_PATH_IDENTIFIES_NESTED_CALLBACK_NODE",
				entries.get(2).action() == leaf
						&& entries.get(2).path().path().equals(List.of(
						new ActionListPanel.PathEntry(0, "body"),
						new ActionListPanel.PathEntry(0, "body"),
						new ActionListPanel.PathEntry(0, null))));
	}

	private static void testOriginAndGroupRotationMath() {
		Vec3 worldDelta = new Vec3(2, 3, 4);
		Vec3 unchanged = OriginConfig.worldDeltaToOffsetDelta(OriginConfig.OriginMode.WORLD,
				0, worldDelta, new Vec3(0, 0, 1), new Vec3(1, 0, 0));
		check("WORLD_ORIGIN_DRAG_USES_WORLD_AXES", near(unchanged, worldDelta));

		Vec3 targetLocal = OriginConfig.worldDeltaToOffsetDelta(OriginConfig.OriginMode.TARGET_FACING,
				0, new Vec3(4, 3, 2), new Vec3(0, 0, 1), new Vec3(1, 0, 0));
		var targetFrame = DanmakuHelper.getOrientation(new Vec3(1, 0, 0));
		Vec3 rebuilt = targetFrame.side().scale(targetLocal.x)
				.add(targetFrame.normal().scale(targetLocal.y))
				.add(targetFrame.forward().scale(targetLocal.z));
		check("TARGET_FACING_ORIGIN_DRAG_INVERTS_LOCAL_FRAME", near(rebuilt, new Vec3(4, 3, 2)));

		var initial = DanmakuHelper.getOrientation(new Vec3(0, 0, 1));
		GroupRotation yaw = new GroupRotation(NumberProvider.constant(0),
				NumberProvider.constant(90), NumberProvider.constant(0));
		var rotated = yaw.apply(initial, new SpellContext(null, null, null, DifficultyModifiers.DEFAULT));
		check("GROUP_ROTATION_Y_CHANGES_FORWARD", Math.abs(rotated.forward().dot(initial.forward())) < 1.0e-6);
		check("GROUP_ROTATION_REMAINS_ORTHONORMAL",
				Math.abs(rotated.forward().dot(rotated.normal())) < 1.0e-6
						&& Math.abs(rotated.forward().dot(rotated.side())) < 1.0e-6
						&& Math.abs(rotated.normal().dot(rotated.side())) < 1.0e-6);
	}

	private static SpellDefinition minimalDefinition() {
		ResourceLocation phase = new ResourceLocation("youkaishomecoming", "callback_test");
		return new SpellDefinition(
				new ResourceLocation("youkaishomecoming", "callback_test_spell"),
				new SpellDisplay("callback_test", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE, phase,
				Map.of(phase, new PhaseDefinition(phase, List.of(), List.of(), List.of(), List.of(), List.of())),
				DifficultyProfile.DEFAULT);
	}

	private static boolean near(Vec3 actual, Vec3 expected) {
		return actual != null && expected != null && actual.distanceToSqr(expected) < 1.0e-12;
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		checks++;
	}
}
