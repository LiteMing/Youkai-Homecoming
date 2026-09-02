package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.item.PlayerHolder;
import dev.xkmc.youkaishomecoming.content.spell.item.RuntimeItemSpell;
import dev.xkmc.youkaishomecoming.content.spell.feedback.NoopFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.feedback.PreviewFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.feedback.ServerFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.feedback.SpellFeedbackSink;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SpellContext {

	private final CardHolder holder;
	@Nullable
	private final SpellRuntimeHost host;
	private final SpellDefinition definition;
	private final SpellRuntime runtime;
	private final DifficultyModifiers difficulty;
	@Nullable
	private final SpellHitContext hitContext;
	@Nullable
	private final ProjectileCallbackContext callbackContext;
	private final SpellFeedbackSink feedback;

	public SpellContext(CardHolder holder, SpellDefinition definition,
						SpellRuntime runtime, DifficultyModifiers difficulty) {
		this(holder, definition, runtime, difficulty, null);
	}

	public SpellContext(CardHolder holder, SpellDefinition definition,
						SpellRuntime runtime, DifficultyModifiers difficulty,
						@Nullable SpellHitContext hitContext) {
		this(holder, definition, runtime, difficulty, hitContext, (ProjectileCallbackContext) null);
	}

	public SpellContext(CardHolder holder, SpellDefinition definition,
						SpellRuntime runtime, DifficultyModifiers difficulty,
						@Nullable SpellHitContext hitContext,
						@Nullable ProjectileCallbackContext callbackContext) {
		this.holder = holder;
		this.host = holder instanceof SpellRuntimeHost spellHost ? spellHost : null;
		this.definition = definition;
		this.runtime = runtime;
		this.difficulty = difficulty;
		this.hitContext = hitContext;
		this.callbackContext = callbackContext;
		this.feedback = holder == null
				? NoopFeedbackSink.INSTANCE
				: holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview
				? preview.feedbackSink()
				: holder.self().level() instanceof net.minecraft.server.level.ServerLevel
				? new ServerFeedbackSink(holder, hitContext)
				: NoopFeedbackSink.INSTANCE;
	}

	public SpellContext(CardHolder holder, SpellDefinition definition,
			SpellRuntime runtime, DifficultyModifiers difficulty,
			@Nullable SpellHitContext hitContext, SpellFeedbackSink feedback) {
		this(holder, definition, runtime, difficulty, hitContext, null, feedback);
	}

	public SpellContext(CardHolder holder, SpellDefinition definition,
			SpellRuntime runtime, DifficultyModifiers difficulty,
			@Nullable SpellHitContext hitContext,
			@Nullable ProjectileCallbackContext callbackContext,
			SpellFeedbackSink feedback) {
		this.holder = holder;
		this.host = holder instanceof SpellRuntimeHost spellHost ? spellHost : null;
		this.definition = definition;
		this.runtime = runtime;
		this.difficulty = difficulty;
		this.hitContext = hitContext;
		this.callbackContext = callbackContext;
		this.feedback = feedback == null ? NoopFeedbackSink.INSTANCE : feedback;
	}

	public java.util.Optional<SpellHitContext> hitContext() {
		return java.util.Optional.ofNullable(hitContext);
	}

	public java.util.Optional<ProjectileCallbackContext> callbackContext() {
		return java.util.Optional.ofNullable(callbackContext);
	}

	/**
	 * Resolve a scalar from the transient projectile callback snapshot. Unknown
	 * keys intentionally resolve to zero so old/non-callback actions remain safe.
	 */
	public double callbackValue(String key) {
		ProjectileCallbackContext c = callbackContext;
		if (c == null || key == null) return 0;
		return switch (key) {
			case "source_x", "source_position_x" -> c.sourcePosition().x;
			case "source_y", "source_position_y" -> c.sourcePosition().y;
			case "source_z", "source_position_z" -> c.sourcePosition().z;
			case "source_velocity_x", "velocity_x", "vx" -> c.sourceVelocity().x;
			case "source_velocity_y", "velocity_y", "vy" -> c.sourceVelocity().y;
			case "source_velocity_z", "velocity_z", "vz" -> c.sourceVelocity().z;
			case "source_direction_x" -> c.sourceDirection().x;
			case "source_direction_y" -> c.sourceDirection().y;
			case "source_direction_z" -> c.sourceDirection().z;
			case "source_speed" -> c.sourceSpeed();
			case "source_size", "size" -> c.sourceSize();
			case "source_spread", "spread" -> c.sourceSpread();
			case "source_lifetime", "lifetime" -> c.sourceLifetime();
			case "source_age" -> c.sourceAge();
			case "source_remaining_lifetime" -> c.sourceRemainingLifetime();
			case "position_x", "hook_x", "hookpos_x" -> c.position().x;
			case "position_y", "hook_y", "hookpos_y" -> c.position().y;
			case "position_z", "hook_z", "hookpos_z" -> c.position().z;
			case "movement_start_x" -> c.movementStart().x;
			case "movement_start_y" -> c.movementStart().y;
			case "movement_start_z" -> c.movementStart().z;
			case "movement_end_x" -> c.movementEnd().x;
			case "movement_end_y" -> c.movementEnd().y;
			case "movement_end_z" -> c.movementEnd().z;
			case "hit_x" -> component(c.hitPosition(), 0);
			case "hit_y" -> component(c.hitPosition(), 1);
			case "hit_z" -> component(c.hitPosition(), 2);
			case "hit_normal_x" -> component(c.hitNormal(), 0);
			case "hit_normal_y" -> component(c.hitNormal(), 1);
			case "hit_normal_z" -> component(c.hitNormal(), 2);
			case "laser_start_x", "start_x" -> componentOr(c.laserStart(), c.movementStart(), 0);
			case "laser_start_y", "start_y" -> componentOr(c.laserStart(), c.movementStart(), 1);
			case "laser_start_z", "start_z" -> componentOr(c.laserStart(), c.movementStart(), 2);
			case "laser_end_x", "end_x" -> componentOr(c.laserEnd(), c.movementEnd(), 0);
			case "laser_end_y", "end_y" -> componentOr(c.laserEnd(), c.movementEnd(), 1);
			case "laser_end_z", "end_z" -> componentOr(c.laserEnd(), c.movementEnd(), 2);
			case "laser_clipped_end_x", "clipped_end_x" -> component(c.laserClippedEnd(), 0);
			case "laser_clipped_end_y", "clipped_end_y" -> component(c.laserClippedEnd(), 1);
			case "laser_clipped_end_z", "clipped_end_z" -> component(c.laserClippedEnd(), 2);
			default -> 0;
		};
	}

	private static double component(@Nullable net.minecraft.world.phys.Vec3 value, int axis) {
		if (value == null) return 0;
		return axis == 0 ? value.x : axis == 1 ? value.y : value.z;
	}

	private static double componentOr(@Nullable net.minecraft.world.phys.Vec3 preferred,
			net.minecraft.world.phys.Vec3 fallback, int axis) {
		return component(preferred == null ? fallback : preferred, axis);
	}

	public dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor callbackColor() {
		return callbackContext == null
				? dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor.WHITE
				: callbackContext.sourceColor();
	}

	public net.minecraft.world.phys.Vec3 targetFacing() {
		var target = holder == null ? null : holder.targetEntity();
		if (target != null) return target.getLookAngle();
		var targetPos = holder == null ? null : holder.target();
		if (targetPos != null) {
			var delta = targetPos.subtract(holder.center());
			if (delta.lengthSqr() > 1.0e-12) return delta.normalize();
		}
		return holder == null ? new net.minecraft.world.phys.Vec3(0, 0, 1) : holder.forward();
	}

	/**
	 * A discard_source action erases the current projectile without allowing
	 * later actions in the same hit callback to run. Other hit dispositions
	 * remain overridable so combinations such as bounce_source followed by
	 * continue_source keep their intentional last-writer-wins behavior.
	 */
	public boolean shouldAbortActionList() {
		return hitContext != null
				&& hitContext.disposition() == SpellHitContext.HitDisposition.DISCARD;
	}

	public void executeList(java.util.List<dev.xkmc.youkaishomecoming.content.spell.action.SpellAction> actions) {
		var preview = previewHolder();
		for (var action : actions) {
			if (shouldAbortActionList()) {
				break;
			}
			if (preview != null) {
				int previous = preview.beginPreviewAction(action);
				try {
					action.execute(this);
				} finally {
					preview.restorePreviewAction(previous);
				}
			} else {
				action.execute(this);
			}
		}
	}

	@Nullable
	private dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder previewHolder() {
		CardHolder current = holder;
		while (current instanceof dev.xkmc.youkaishomecoming.content.spell.action.TrailCardHolder trail) {
			current = trail.delegate();
		}
		return current instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview
				? preview : null;
	}

	public CardHolder holder() {
		return holder;
	}

	@Nullable
	public SpellRuntimeHost host() {
		return host;
	}

	public SpellDefinition definition() {
		return definition;
	}

	public SpellRuntime runtime() {
		return runtime;
	}

	public DifficultyModifiers difficulty() {
		return difficulty;
	}

	public SpellFeedbackSink feedback() {
		return feedback;
	}

	public LivingEntity self() {
		return holder.self();
	}

	public int phaseTick() {
		return runtime.getPhaseTick();
	}

	public int totalTick() {
		return runtime.getTotalTick();
	}

	public ResourceLocation currentPhaseId() {
		return runtime.getCurrentPhaseId();
	}

	public float healthRatio() {
		var self = holder.self();
		return self.getHealth() / self.getMaxHealth();
	}

	public double distanceToTarget() {
		var target = holder.target();
		if (target == null) return Double.MAX_VALUE;
		return holder.center().distanceTo(target);
	}

	public double getVariable(String key) {
		return runtime.getVariable(key);
	}

	public void setVariable(String key, double value) {
		runtime.setVariable(key, value);
	}

	public void setMovementDirective(SpellMovementDirective directive) {
		runtime.setMovementDirective(directive);
	}

	public void clearDanmaku() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			preview.clear();
			return;
		}
		if (holder instanceof PlayerHolder playerHolder && playerHolder.spell() instanceof RuntimeItemSpell runtimeItemSpell) {
			runtimeItemSpell.clearDanmaku();
			return;
		}
		if (host != null) {
			host.eraseDanmaku(null);
		}
	}

	public boolean switchSpell(ResourceLocation spellId, boolean clearScreen) {
		var def = runtime.resolveDefinition(spellId);
		if (def == null) {
			return false;
		}
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.switchSpell(def, clearScreen);
		}
		if (holder instanceof PlayerHolder playerHolder && playerHolder.spell() instanceof RuntimeItemSpell runtimeItemSpell) {
			runtimeItemSpell.switchSpell(def, runtime.continueWith(def), clearScreen);
			return true;
		}
		if (host != null) {
			SpellRuntime next = runtime.continueWith(def);
			host.switchSpellRuntime(next, clearScreen);
			next.enterCurrentPhase(host);
			return true;
		}
		return false;
	}

	public int hitCount() {
		return runtime.getHitCount();
	}

	public Map<String, Double> variables() {
		return runtime.getVariables();
	}

	/**
	 * Returns true if the target entity is on the ground.
	 * Returns false if there is no target.
	 */
	public boolean targetOnGround() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetOnGround();
		}
		var target = holder.targetEntity();
		return target != null && target.onGround();
	}

	/**
	 * Returns the horizontal speed of the target entity.
	 * Returns 0 if there is no target or no velocity data.
	 */
	public double targetSpeed() {
		var vel = holder.targetVelocity();
		if (vel == null) return 0;
		return vel.horizontalDistance();
	}

	/**
	 * Returns the health ratio of the target entity (0.0 ~ 1.0).
	 * Returns 1.0 if there is no target.
	 */
	public float targetHealthRatio() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.getTargetHealthRatio();
		}
		var target = holder.targetEntity();
		if (target == null) return 1.0f;
		return target.getHealth() / target.getMaxHealth();
	}

	/**
	 * Returns true if the target entity is flying (abilities.flying).
	 */
	public boolean targetIsFlying() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetFlying();
		}
		var target = holder.targetEntity();
		if (target == null) return false;
		if (target instanceof net.minecraft.world.entity.player.Player p) {
			return p.getAbilities().flying;
		}
		return !target.onGround() && target.fallDistance == 0;
	}

	/**
	 * Returns how many consecutive ticks the target has been off the ground.
	 * Tracked by {@link SpellRuntime} each tick for accuracy.
	 * Returns 0 if there is no target or the target is on the ground.
	 */
	public int targetFlyTime() {
		return runtime.getTargetFlyTime();
	}

	/**
	 * Returns true if the target entity is elytra gliding.
	 */
	public boolean targetIsFallFlying() {
		if (holder instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview) {
			return preview.isTargetFallFlying();
		}
		var target = holder.targetEntity();
		return target != null && target.isFallFlying();
	}
}
