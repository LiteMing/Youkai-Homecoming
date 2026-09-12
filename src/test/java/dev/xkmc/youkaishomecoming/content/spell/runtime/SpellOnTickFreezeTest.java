package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Drives the production phase loop without creating a Minecraft world. */
public final class SpellOnTickFreezeTest {

	private static final ResourceLocation INTRO = new ResourceLocation("yh_test", "intro");
	private static final ResourceLocation NEXT = new ResourceLocation("yh_test", "next");
	private static int checks;

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		defaultUnchanged();
		entryFreeze();
		delayedWaves();
		lifetime();
		transitions();
		System.out.println("SpellOnTickFreezeTest: all " + checks + " checks passed");
	}

	private static void defaultUnchanged() {
		var ticks = new ArrayList<Integer>();
		var runtime = runtime(List.of(ctx -> ticks.add(ctx.phaseTick())), List.of());
		runtime.setCasterInvulnerability(1000, 100);
		step(runtime, 1000, 3);
		check("invulnerability does not pause actions", ticks.equals(List.of(0, 1, 2)));
		check("default clock is unchanged", runtime.getPhaseTick() == 3 && runtime.getTotalTick() == 3);
		check("invulnerability and freeze are independent", runtime.isCasterInvulnerable(1003)
				&& !runtime.isOnTickFrozen(1003));
	}

	private static void entryFreeze() {
		var ticks = new ArrayList<Integer>();
		var runtime = runtime(List.of(ctx -> {
			ticks.add(ctx.phaseTick());
			check("onTick total time excludes the freeze", ctx.totalTick() == ctx.phaseTick());
		}), List.of());
		runtime.setSpellHealth(50, 100);
		runtime.setOnTickFreeze(1000, 4);
		step(runtime, 1000, 4);
		check("no onTick action executes during declaration", ticks.isEmpty());
		check("phase time stays at zero", runtime.getPhaseTick() == 0);
		check("health timeout and lifecycle age keep running", runtime.getSpellElapsedTicks() == 4
				&& runtime.getBattleElapsedTicks() == 4);
		step(runtime, 1004, 3);
		check("first wave resumes at tick zero without a catch-up burst", ticks.equals(List.of(0, 1, 2)));
		check("world and action time stay distinct", runtime.getTotalTick() == 7 && runtime.getPhaseTick() == 3);
	}

	private static void delayedWaves() {
		var waves = new ArrayList<Integer>();
		var ordinary = new ArrayList<Integer>();
		var held = new ArrayList<Integer>();
		var nested = new ArrayList<Integer>();
		SpellAction burst = new BurstAction(3, 2, List.of(ctx -> waves.add(ctx.phaseTick())));
		var runtime = runtime(List.of(ctx -> {
			if (ctx.phaseTick() == 0) {
				burst.execute(ctx);
				new DelayAction(1, List.of(new DelayAction(2,
						List.of(delayed -> nested.add(delayed.phaseTick()))))).execute(ctx);
			}
		}), List.of());
		// on_enter delay and projectile hold release intentionally use real cast age.
		new DelayAction(2, List.of(ctx -> ordinary.add(ctx.totalTick()))).execute(context(runtime));
		runtime.schedulePersistentDelayed(3, List.of(ctx -> held.add(ctx.totalTick())));
		step(runtime, 1000, 1);
		runtime.setOnTickFreeze(1001, 4);
		step(runtime, 1001, 4);
		check("queued burst waves stay paused", waves.equals(List.of(0)));
		check("declaration delays still execute", ordinary.equals(List.of(2)));
		check("projectile hold releases still execute", held.equals(List.of(3)));
		check("nested onTick delay cannot escape the freeze", nested.isEmpty());
		step(runtime, 1005, 4);
		check("burst spacing survives resume", waves.equals(List.of(0, 2, 4)));
		check("nested delay keeps its action clock", nested.equals(List.of(3)));
	}

	private static void lifetime() {
		var runtime = runtime(List.of(), List.of());
		runtime.setOnTickFreeze(1000, 5);
		step(runtime, 1000, 2);
		var restored = runtime(List.of(), List.of());
		restored.loadFromTag(runtime.saveToTag());
		check("save restores the remaining freeze", restored.isOnTickFrozen(1004) && !restored.isOnTickFrozen(1005));
		step(restored, 1005, 1);
		check("reload preserves action clock offset", restored.getPhaseTick() == 1 && restored.getTotalTick() == 3);
		runtime.setCasterInvulnerability(1002, 5);
		runtime.setOnTickFreeze(1002, 0);
		check("zero clears only the freeze", !runtime.isOnTickFrozen(1002) && runtime.isCasterInvulnerable(1002));
		runtime.setOnTickFreeze(1002, 5);
		runtime.setCasterInvulnerability(1003, 0);
		check("clearing protection leaves the independent freeze", !runtime.isCasterInvulnerable(1004)
				&& runtime.isOnTickFrozen(1004));
		runtime.reset();
		check("reset clears the freeze and clock offset", !runtime.isOnTickFrozen(1004)
				&& runtime.getPhaseTick() == 0 && runtime.getTotalTick() == 0);
		restored.loadFromTag(runtime.saveToTag());
		check("old/default NBT cannot leave a stale freeze", !restored.isOnTickFrozen(1004));
	}

	private static void transitions() {
		var seen = new ArrayList<Integer>();
		var runtime = runtime(List.of(), List.of(new Transition(ctx -> ctx.phaseTick() >= 1, NEXT, TransitionMode.IMMEDIATE)),
				List.of(ctx -> seen.add(ctx.phaseTick())));
		runtime.setOnTickFreeze(1000, 3);
		step(runtime, 1000, 3);
		check("phase transition waits for action time", runtime.getCurrentPhaseId().equals(INTRO));
		step(runtime, 1003, 2);
		check("phase transitions resume", runtime.getCurrentPhaseId().equals(NEXT));
		step(runtime, 1005, 1);
		check("the next phase gets its tick-zero actions", seen.equals(List.of(0)));
		var forced = runtime(List.of(ctx -> new SpellActions.ForcePhase(NEXT, false).execute(ctx)), List.of(),
				List.of(ctx -> seen.add(ctx.phaseTick())));
		step(forced, 2000, 1);
		check("an action-driven phase change also starts at zero", forced.getPhaseTick() == 0);
		var loop = runtime(List.of(ctx -> new SpellActions.ForcePhase(INTRO, false).execute(ctx)), List.of());
		step(loop, 3000, 2);
		check("a repeated phase also retains its first action tick", loop.getPhaseTick() == 0);
		var totalClock = runtime(List.of(), List.of(new Transition(ctx -> ctx.totalTick() >= 2, NEXT, TransitionMode.IMMEDIATE)));
		totalClock.setOnTickFreeze(4000, 3);
		step(totalClock, 4000, 4);
		check("total_tick transition conditions use action time", totalClock.getCurrentPhaseId().equals(INTRO));
		step(totalClock, 4004, 2);
		check("total_tick transition resumes at its authored threshold", totalClock.getCurrentPhaseId().equals(NEXT));
	}

	private static SpellRuntime runtime(List<SpellAction> tick, List<Transition> transitions) {
		return runtime(tick, transitions, List.of());
	}

	private static SpellRuntime runtime(List<SpellAction> tick, List<Transition> transitions, List<SpellAction> nextTick) {
		var intro = new PhaseDefinition(INTRO, List.of(), tick, List.of(), List.of(), transitions);
		var next = new PhaseDefinition(NEXT, List.of(), nextTick, List.of(), List.of(), List.of());
		var definition = new SpellDefinition(new ResourceLocation("yh_test", "freeze"),
				new SpellDisplay("Freeze", "", Optional.empty(), Optional.empty()), SpellItemForm.NONE,
				INTRO, Map.of(INTRO, intro, NEXT, next), DifficultyProfile.DEFAULT);
		return new SpellRuntime(definition, id -> null, null);
	}

	private static SpellContext context(SpellRuntime runtime) {
		return new SpellContext(null, runtime.getDefinition(), runtime, DifficultyModifiers.DEFAULT);
	}

	private static void step(SpellRuntime runtime, long gameTime, int count) {
		for (int i = 0; i < count; i++) runtime.tickCurrentPhase(context(runtime), gameTime + i);
	}

	private static void check(String message, boolean condition) {
		if (!condition) throw new AssertionError(message);
		checks++;
	}
}
