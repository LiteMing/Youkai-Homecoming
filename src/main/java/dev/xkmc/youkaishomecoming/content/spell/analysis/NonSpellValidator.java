package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.ShowSpellTitleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellCircleAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfigs;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;

import java.util.List;
import java.util.Optional;

/** Fail-closed certification profile for the deliberately small non-spell budget. */
public final class NonSpellValidator {
	private NonSpellValidator() {}

	/** Lets the cast boundary keep presentation-node feedback distinct from other rules. */
	public static final class PresentationNodeException extends SpellAnalysisException {
		private PresentationNodeException() {
			super("Non-spells cannot use spell presentation or health nodes");
		}
	}

	public static void validate(SpellDefinition definition, SpellCardRank rank) {
		if (definition == null) throw new SpellAnalysisException("Non-spell definition is missing");
		if (definition.itemForm.casterMoves()) {
			throw new SpellAnalysisException("Non-spells cannot restrict caster movement");
		}
		if (SpellHealthPlan.hasHealthDeclaration(definition)) {
			throw new SpellAnalysisException("Non-spells cannot declare spell health");
		}
		for (PhaseDefinition phase : definition.phases.values()) {
			checkList(phase.onEnter);
			checkList(phase.onTick);
			checkList(phase.onExit);
			checkList(phase.onDamage);
		}
		SpellAnalysisLimits base = SpellAnalysisLimits.certification();
		int perTier = Math.max(1, YHModConfig.COMMON.nonSpellMaxSpawnPerTier.get());
		int lifetime = Math.max(1, YHModConfig.COMMON.nonSpellMaxLifetimeTicks.get());
		SpellAnalysisLimits limits = new SpellAnalysisLimits(base.maxPhases(), base.maxActions(), base.maxDepth(),
				base.maxRepeat(), base.maxTotalProjectiles(), base.maxShooters(), lifetime,
				base.maxExpressionLength(), Math.max(1, rank.tierNumber() * perTier),
				base.maxPeakAlive(), base.maxProjectileTicks(), 0, 1,
				base.certificationWindowTicks());
		SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION, limits, java.util.Set.of());
	}

	private static void checkList(List<SpellAction> actions) {
		for (SpellAction action : actions) check(action);
	}

	private static void check(SpellAction action) {
		SpellAction inner = action;
		if (inner instanceof SpellActions.DisabledAction disabled) {
			inner = disabled.inner();
		}
		if (inner instanceof SetSpellHealthAction || inner instanceof SetSpellCircleAction
				|| inner instanceof ShowSpellTitleAction) {
			throw new PresentationNodeException();
		}
		if (inner instanceof FireDanmakuAction danmaku) {
			if (has(danmaku.onHitEntity()) || has(danmaku.onHitBlock()) || has(danmaku.onExpiry())
					|| has(danmaku.onTrail())) {
				throw new SpellAnalysisException("Non-spell projectile hooks are disabled");
			}
			if (danmaku.hitBehaviorEntity() != HitBehavior.DISCARD
					|| danmaku.hitBehaviorBlock() != HitBehavior.DISCARD) {
				throw new SpellAnalysisException("Non-spell projectiles must discard on every collision");
			}
			checkBounded(danmaku.speed(), YHModConfig.COMMON.nonSpellMaxInitialSpeed.get(), "initial speed");
			checkBounded(danmaku.lifetime(), YHModConfig.COMMON.nonSpellMaxLifetimeTicks.get(), "lifetime");
			checkOrigin(danmaku.origin());
			danmaku.mover().ifPresent(mover -> checkMover(mover,
					YHModConfig.COMMON.nonSpellMaxLifetimeTicks.get(), maxAbs(danmaku.speed())));
		}
		if (inner instanceof FireLaserAction) {
			throw new SpellAnalysisException("Non-spells cannot use laser nodes");
		}
		if (inner instanceof SpawnShooterAction shooter) {
			if (shooter.lifetime() > YHModConfig.COMMON.nonSpellMaxLifetimeTicks.get()) {
				throw new SpellAnalysisException("Non-spell shooter lifetime exceeds the configured limit");
			}
			checkBounded(shooter.speed(), YHModConfig.COMMON.nonSpellMaxInitialSpeed.get(), "shooter speed");
			checkOrigin(shooter.origin());
			shooter.mover().ifPresent(mover -> checkMover(mover, shooter.lifetime(), maxAbs(shooter.speed())));
		}
		if (inner instanceof SpellActions.ConditionalAction conditional) {
			checkList(conditional.ifTrue());
			checkList(conditional.ifFalse());
		}
		if (inner instanceof SpellActions.SequenceAction sequence) checkList(sequence.actions());
		if (inner instanceof SpellActions.RepeatAction repeat) checkList(repeat.body());
		if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.DelayAction delay) checkList(delay.body());
		if (inner instanceof dev.xkmc.youkaishomecoming.content.spell.action.BurstAction burst) checkList(burst.body());
		if (inner instanceof SpawnShooterAction shooter) checkList(shooter.body());
	}

	private static void checkMover(MoverConfig mover, int lifetime, double initialSpeed) {
		double speedLimit = YHModConfig.COMMON.nonSpellMaxInitialSpeed.get();
		if (mover instanceof MoverConfigs.ZeroMoverConfig || mover instanceof MoverConfigs.RotateConfig) return;
		if (mover instanceof MoverConfigs.FixedDirMoverConfig fixed) {
			checkMover(fixed.inner(), lifetime, initialSpeed);
			return;
		}
		if (mover instanceof MoverConfigs.HomingMoverConfig homing) {
			checkBounded(homing.speed(), speedLimit, "homing speed");
			checkRange(homing.turnRate(), 0, 180, "homing turn rate");
			checkRange(homing.delay(), 0, Math.max(1, lifetime), "homing delay");
			return;
		}
		if (mover instanceof MoverConfigs.DecelerationConfig deceleration) {
			checkRange(deceleration.factor(), 0, 2.0 / Math.max(1, lifetime), "deceleration factor");
			return;
		}
		if (mover instanceof MoverConfigs.AccelerationConfig acceleration) {
			double terminalX = checkAccelerationAxis(acceleration.x(), acceleration.terminalVx(), speedLimit, "X");
			double terminalY = checkAccelerationAxis(acceleration.y(), acceleration.terminalVy(), speedLimit, "Y");
			double terminalZ = checkAccelerationAxis(acceleration.z(), acceleration.terminalVz(), speedLimit, "Z");
			double conservativeSpeed = Math.sqrt(initialSpeed * initialSpeed
					+ terminalX * terminalX + terminalY * terminalY + terminalZ * terminalZ);
			if (conservativeSpeed > speedLimit) {
				throw new SpellAnalysisException("Non-spell acceleration can exceed speed limit " + speedLimit);
			}
			return;
		}
		throw new SpellAnalysisException("Non-spell mover " + MoverConfigs.getTypeId(mover)
				+ " cannot be statically speed-bounded");
	}

	private static double checkAccelerationAxis(NumberProvider acceleration,
			Optional<NumberProvider> terminalVelocity, double speedLimit, String axis) {
		NumberBounds bounds = NumberBounds.resolve(acceleration);
		if (!bounds.bounded()) {
			throw new SpellAnalysisException("Non-spell " + axis + " acceleration cannot be bounded");
		}
		boolean canAccelerate = bounds.min() != 0 || bounds.max() != 0;
		if (canAccelerate && terminalVelocity.isEmpty()) {
			throw new SpellAnalysisException("Non-spell " + axis + " acceleration requires terminal velocity");
		}
		if (!canAccelerate) return 0;
		NumberProvider terminal = terminalVelocity.orElseThrow();
		checkBounded(terminal, speedLimit, axis + " terminal velocity");
		return maxAbs(terminal);
	}

	private static void checkRange(NumberProvider provider, double min, double max, String label) {
		NumberBounds bounds = NumberBounds.resolve(provider);
		if (!bounds.bounded() || bounds.min() < min || bounds.max() > max) {
			throw new SpellAnalysisException("Non-spell " + label + " is unbounded or outside ["
					+ min + ", " + max + "]");
		}
	}

	private static void checkOrigin(OriginConfig origin) {
		double limit = YHModConfig.COMMON.nonSpellMaxOriginOffset.get();
		checkBounded(origin.offsetX(), limit, "origin X offset");
		checkBounded(origin.offsetY(), limit, "origin Y offset");
		checkBounded(origin.offsetZ(), limit, "origin Z offset");
	}

	private static void checkBounded(NumberProvider provider, double absoluteLimit, String label) {
		NumberBounds bounds = NumberBounds.resolve(provider);
		if (!bounds.bounded() || Math.abs(bounds.min()) > absoluteLimit || Math.abs(bounds.max()) > absoluteLimit) {
			throw new SpellAnalysisException("Non-spell " + label + " is unbounded or exceeds " + absoluteLimit);
		}
	}

	private static double maxAbs(NumberProvider provider) {
		NumberBounds bounds = NumberBounds.resolve(provider);
		return bounds.bounded() ? Math.max(Math.abs(bounds.min()), Math.abs(bounds.max()))
				: Double.POSITIVE_INFINITY;
	}

	private static boolean has(Optional<List<SpellAction>> actions) {
		return actions.isPresent() && !actions.get().isEmpty();
	}
}
