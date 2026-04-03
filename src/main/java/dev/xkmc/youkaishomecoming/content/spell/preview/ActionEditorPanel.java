package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.action.*;
import dev.xkmc.youkaishomecoming.content.spell.condition.*;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Editor panel for editing SpellAction properties within the preview screen.
 * Supports editing all action types and a type selector mode for creating new actions.
 */
@OnlyIn(Dist.CLIENT)
public class ActionEditorPanel {

	private static final int ROW_HEIGHT = 20;
	private static final int LABEL_WIDTH = 70;
	private static final int PADDING = 4;

	private static final String[] CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count", "always", "and"
	};

	private static final String[] SIMPLE_CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count", "always"
	};

	private static final String[] AIM_MODE_TYPES = {
			"target", "fixed", "caster_facing", "angle_offset", "variable_angle"
	};

	private static final String[] ORIGIN_MODE_TYPES = {
			"caster", "target", "absolute", "caster_facing"
	};

	private final Consumer<AbstractWidget> addWidget;
	private final Consumer<AbstractWidget> removeWidget;
	private final Consumer<SpellAction> onActionChanged;
	private final Runnable onDeleteAction;

	private int x, y, w, h;
	private SpellAction currentAction;
	private int actionIndex = -1;
	private final List<EditorRow> rows = new ArrayList<>();
	private int scrollOffset = 0;
	private boolean widgetsRegistered = false;

	// Type selector mode
	private boolean typeSelectorMode = false;
	private Consumer<SpellAction> typeSelectorCallback;

	public ActionEditorPanel(Consumer<AbstractWidget> addWidget,
							 Consumer<AbstractWidget> removeWidget,
							 Consumer<SpellAction> onActionChanged,
							 Runnable onDeleteAction) {
		this.addWidget = addWidget;
		this.removeWidget = removeWidget;
		this.onActionChanged = onActionChanged;
		this.onDeleteAction = onDeleteAction;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public void setAction(SpellAction action, int index) {
		if (action == currentAction && index == actionIndex) return;
		clearWidgets();
		this.currentAction = action;
		this.actionIndex = index;
		this.scrollOffset = 0;
		this.typeSelectorMode = false;
		if (action instanceof FireDanmakuAction fda) {
			buildFireDanmakuRows(fda);
		} else if (action instanceof FireLaserAction fla) {
			buildFireLaserRows(fla);
		} else if (action instanceof SpellActions.ConditionalAction ca) {
			buildConditionalRows(ca);
		} else if (action instanceof SpellActions.SetVariable sv) {
			buildSetVariableRows(sv);
		} else if (action instanceof SpellActions.AddVariable av) {
			buildAddVariableRows(av);
		} else if (action instanceof SpellActions.PlaySoundAction ps) {
			buildPlaySoundRows(ps);
		} else if (action instanceof SpellActions.ForcePhase fp) {
			buildForcePhaseRows(fp);
		} else if (action instanceof SpellActions.RepeatAction ra) {
			buildRepeatRows(ra);
		}
		layoutWidgets();
	}

	public void clearAction() {
		clearWidgets();
		currentAction = null;
		actionIndex = -1;
		typeSelectorMode = false;
	}

	public void showTypeSelector(Consumer<SpellAction> onCreated) {
		clearWidgets();
		currentAction = null;
		actionIndex = -1;
		typeSelectorMode = true;
		typeSelectorCallback = onCreated;
		buildTypeSelectorRows();
		layoutWidgets();
	}

	private void clearWidgets() {
		for (var row : rows) {
			removeWidget.accept(row.widget);
		}
		rows.clear();
		widgetsRegistered = false;
	}

	// --- Type selector ---

	private void buildTypeSelectorRows() {
		addFullWidthButton("Fire Danmaku", () -> selectType("fire_danmaku"));
		addFullWidthButton("Fire Laser", () -> selectType("fire_laser"));
		addFullWidthButton("Conditional", () -> selectType("conditional"));
		addFullWidthButton("Repeat", () -> selectType("repeat"));
		addFullWidthButton("Set Variable", () -> selectType("set_variable"));
		addFullWidthButton("Add Variable", () -> selectType("add_variable"));
		addFullWidthButton("Clear Screen", () -> selectType("clear_screen"));
		addFullWidthButton("Play Sound", () -> selectType("play_sound"));
		addFullWidthButton("Force Phase", () -> selectType("force_phase"));
	}

	private void selectType(String type) {
		SpellAction action = createDefaultAction(type);
		typeSelectorMode = false;
		if (typeSelectorCallback != null) {
			typeSelectorCallback.accept(action);
		}
	}

	public static SpellAction createDefaultAction(String type) {
		return switch (type) {
			case "fire_danmaku" -> new FireDanmakuAction(
					YHDanmaku.Bullet.CIRCLE, DyeColor.WHITE,
					NumberProvider.constant(8), NumberProvider.constant(0.5),
					NumberProvider.constant(100), NumberProvider.constant(0),
					NumberProvider.constant(360), PatternType.RING,
					OriginConfig.caster(), new AimMode.AimModes.Target(),
					Optional.empty(), Optional.empty());
			case "fire_laser" -> new FireLaserAction(
					YHDanmaku.Laser.LASER, DyeColor.WHITE,
					NumberProvider.constant(60), NumberProvider.constant(80),
					NumberProvider.constant(0), new AimMode.AimModes.Target(),
					OriginConfig.caster(), Optional.empty(), 0, 0, 0);
			case "conditional" -> new SpellActions.ConditionalAction(
					new SpellConditions.TickInterval(20, 0),
					new ArrayList<>(), new ArrayList<>());
			case "repeat" -> new SpellActions.RepeatAction(
					NumberProvider.constant(8), "i", new ArrayList<>());
			case "set_variable" -> new SpellActions.SetVariable("var", 0);
			case "add_variable" -> new SpellActions.AddVariable("var", 1);
			case "clear_screen" -> new SpellActions.ClearScreen();
			case "play_sound" -> new SpellActions.PlaySoundAction(
					new ResourceLocation("minecraft", "entity.experience_orb.pickup"), 1f, 1f);
			case "force_phase" -> new SpellActions.ForcePhase(
					new ResourceLocation("youkaishomecoming", "main"));
			default -> new SpellActions.NoopAction();
		};
	}

	// --- FireDanmaku rows ---

	private void buildFireDanmakuRows(FireDanmakuAction a) {
		addEnumRow("Bullet", YHDanmaku.Bullet.values(), a.bulletType(), v ->
				notifyDanmaku(old -> old.withBulletType(v)));

		addEnumRow("Color", DyeColor.values(), a.color(), v ->
				notifyDanmaku(old -> old.withColor(v)));

		addNumberRow("Count", a.count(), v ->
				notifyDanmaku(old -> old.withCount(v), false));

		addNumberRow("Speed", a.speed(), v ->
				notifyDanmaku(old -> old.withSpeed(v), false));

		addNumberRow("Lifetime", a.lifetime(), v ->
				notifyDanmaku(old -> old.withLifetime(v), false));

		addNumberRow("Angle", a.angleOffset(), v ->
				notifyDanmaku(old -> old.withAngleOffset(v), false));

		addNumberRow("Spread", a.spread(), v ->
				notifyDanmaku(old -> old.withSpread(v), false));

		addEnumRow("Pattern", PatternType.values(), a.pattern(), v ->
				notifyDanmaku(old -> old.withPattern(v)));

		// AimMode cycle
		String currentAim = getAimModeType(a.aimMode());
		addStringCycleRow("Aim Mode", AIM_MODE_TYPES, currentAim, newType -> {
			AimMode newMode = createDefaultAimMode(newType);
			notifyDanmaku(old -> old.withAimMode(newMode));
		});

		// OriginConfig mode
		addEnumRow("Origin", OriginConfig.OriginMode.values(), a.origin().mode(), v -> {
			var newOrigin = new OriginConfig(v, a.origin().offsetX(), a.origin().offsetY(),
					a.origin().offsetZ(), a.origin().rotation());
			notifyDanmaku(old -> old.withOrigin(newOrigin));
		});

		// OriginConfig offsets
		buildOriginOffsetRows(a.origin(), newOrigin -> notifyDanmaku(old -> old.withOrigin(newOrigin), false));

		// Mover
		buildMoverRows(a.mover(),
				newMover -> notifyDanmaku(old -> old.withMover(newMover)),
				newMover -> notifyDanmaku(old -> old.withMover(newMover), false));

		// onExpiry indicator
		if (a.onExpiry().isPresent()) {
			addFullWidthButton("[onExpiry: " + a.onExpiry().get().size() + " actions]", () -> {});
		}
	}

	// --- FireLaser rows ---

	private void buildFireLaserRows(FireLaserAction a) {
		addEnumRow("Laser", YHDanmaku.Laser.values(), a.laserType(), v ->
				notifyLaser(old -> old.withLaserType(v)));

		addEnumRow("Color", DyeColor.values(), a.color(), v ->
				notifyLaser(old -> old.withColor(v)));

		addNumberRow("Lifetime", a.lifetime(), v ->
				notifyLaser(old -> old.withLifetime(v), false));

		addNumberRow("Length", a.length(), v ->
				notifyLaser(old -> old.withLength(v), false));

		addNumberRow("Angle", a.angleOffset(), v ->
				notifyLaser(old -> old.withAngleOffset(v), false));

		// AimMode cycle
		String currentAim = getAimModeType(a.aimMode());
		addStringCycleRow("Aim Mode", AIM_MODE_TYPES, currentAim, newType -> {
			AimMode newMode = createDefaultAimMode(newType);
			notifyLaser(old -> old.withAimMode(newMode));
		});

		// OriginConfig mode
		addEnumRow("Origin", OriginConfig.OriginMode.values(), a.origin().mode(), v -> {
			var newOrigin = new OriginConfig(v, a.origin().offsetX(), a.origin().offsetY(),
					a.origin().offsetZ(), a.origin().rotation());
			notifyLaser(old -> old.withOrigin(newOrigin));
		});

		// setupTime params
		addIntRow("Prepare", a.setupPrepare(), v ->
				notifyLaser(old -> old.withSetupPrepare(v), false));
		addIntRow("Start", a.setupStart(), v ->
				notifyLaser(old -> old.withSetupStart(v), false));
		addIntRow("End", a.setupEnd(), v ->
				notifyLaser(old -> old.withSetupEnd(v), false));

		// OriginConfig offsets
		buildOriginOffsetRows(a.origin(), newOrigin -> notifyLaser(old -> old.withOrigin(newOrigin), false));

		// Mover
		buildMoverRows(a.mover(),
				newMover -> notifyLaser(old -> old.withMover(newMover)),
				newMover -> notifyLaser(old -> old.withMover(newMover), false));
	}

	// --- Conditional rows ---

	private void buildConditionalRows(SpellActions.ConditionalAction ca) {
		String currentType = getConditionType(ca.condition());

		addStringCycleRow("Condition", CONDITION_TYPES, currentType, newType -> {
			SpellCondition newCond = createDefaultCondition(newType);
			notifyConditional(old -> new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse()));
		});

		SpellCondition cond = ca.condition();
		if (cond instanceof SpellConditions.AndCondition ac) {
			buildAndConditionRows(ac);
		} else {
			buildConditionParamRows("", cond, newCond ->
					notifyConditional(old -> new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse()), false));
		}
	}

	private void buildAndConditionRows(SpellConditions.AndCondition ac) {
		List<SpellCondition> subs = ac.conditions();
		SpellCondition sub1 = subs.size() > 0 ? subs.get(0) : new SpellConditions.AlwaysCondition(true);
		SpellCondition sub2 = subs.size() > 1 ? subs.get(1) : new SpellConditions.AlwaysCondition(true);

		addStringCycleRow("Cond 1", SIMPLE_CONDITION_TYPES, getConditionType(sub1), newType ->
				notifyAndSubCondition(0, createDefaultCondition(newType)));
		buildConditionParamRows("1:", sub1, newSub -> notifyAndSubCondition(0, newSub, false));

		addStringCycleRow("Cond 2", SIMPLE_CONDITION_TYPES, getConditionType(sub2), newType ->
				notifyAndSubCondition(1, createDefaultCondition(newType)));
		buildConditionParamRows("2:", sub2, newSub -> notifyAndSubCondition(1, newSub, false));

		// Additional sub-conditions beyond the first 2
		for (int i = 2; i < subs.size(); i++) {
			SpellCondition sub = subs.get(i);
			int idx = i;
			addStringCycleRow("Cond " + (i + 1), SIMPLE_CONDITION_TYPES, getConditionType(sub), newType ->
					notifyAndSubCondition(idx, createDefaultCondition(newType)));
			buildConditionParamRows((idx + 1) + ":", sub, newSub -> notifyAndSubCondition(idx, newSub, false));
		}

		// Button to add more sub-conditions
		addFullWidthButton("[+ Add Condition]", () -> {
			notifyConditional(old -> {
				if (old.condition() instanceof SpellConditions.AndCondition oldAc) {
					List<SpellCondition> newSubs = new ArrayList<>(oldAc.conditions());
					newSubs.add(new SpellConditions.AlwaysCondition(true));
					return new SpellActions.ConditionalAction(new SpellConditions.AndCondition(newSubs), old.ifTrue(), old.ifFalse());
				}
				return old;
			});
		});
	}

	private void notifyAndSubCondition(int subIndex, SpellCondition newSub) {
		notifyAndSubCondition(subIndex, newSub, true);
	}

	private void notifyAndSubCondition(int subIndex, SpellCondition newSub, boolean rebuild) {
		notifyConditional(old -> {
			if (old.condition() instanceof SpellConditions.AndCondition ac) {
				List<SpellCondition> newSubs = new ArrayList<>(ac.conditions());
				while (newSubs.size() <= subIndex) newSubs.add(new SpellConditions.AlwaysCondition(true));
				newSubs.set(subIndex, newSub);
				return new SpellActions.ConditionalAction(new SpellConditions.AndCondition(newSubs), old.ifTrue(), old.ifFalse());
			}
			return old;
		}, rebuild);
	}

	private void buildConditionParamRows(String prefix, SpellCondition cond, Consumer<SpellCondition> onChanged) {
		if (cond instanceof SpellConditions.TickInterval ti) {
			addIntRow(prefix + "Interval", ti.interval(), v ->
					onChanged.accept(new SpellConditions.TickInterval(v, ti.offset())));
			addIntRow(prefix + "Offset", ti.offset(), v ->
					onChanged.accept(new SpellConditions.TickInterval(ti.interval(), v)));
		} else if (cond instanceof SpellConditions.HealthBelow hb) {
			addFloatRow(prefix + "Threshold", hb.threshold(), v ->
					onChanged.accept(new SpellConditions.HealthBelow(v)));
		} else if (cond instanceof SpellConditions.HealthAbove ha) {
			addFloatRow(prefix + "Threshold", ha.threshold(), v ->
					onChanged.accept(new SpellConditions.HealthAbove(v)));
		} else if (cond instanceof SpellConditions.TickElapsed te) {
			addIntRow(prefix + "Ticks", te.ticks(), v ->
					onChanged.accept(new SpellConditions.TickElapsed(v)));
		} else if (cond instanceof SpellConditions.DistanceAbove da) {
			addDoubleRow(prefix + "Distance", da.distance(), v ->
					onChanged.accept(new SpellConditions.DistanceAbove(v)));
		} else if (cond instanceof SpellConditions.DistanceBelow db) {
			addDoubleRow(prefix + "Distance", db.distance(), v ->
					onChanged.accept(new SpellConditions.DistanceBelow(v)));
		} else if (cond instanceof SpellConditions.HitCountCondition hc) {
			addIntRow(prefix + "Count", hc.count(), v ->
					onChanged.accept(new SpellConditions.HitCountCondition(v)));
		}
	}

	// --- SetVariable / AddVariable rows ---

	private void buildSetVariableRows(SpellActions.SetVariable sv) {
		addStringRow("Key", sv.key(), v ->
				notifySimple(old -> new SpellActions.SetVariable(v, ((SpellActions.SetVariable) old).value())));
		addDoubleRow("Value", sv.value(), v ->
				notifySimple(old -> new SpellActions.SetVariable(((SpellActions.SetVariable) old).key(), v)));
	}

	private void buildAddVariableRows(SpellActions.AddVariable av) {
		addStringRow("Key", av.key(), v ->
				notifySimple(old -> new SpellActions.AddVariable(v, ((SpellActions.AddVariable) old).delta())));
		addDoubleRow("Delta", av.delta(), v ->
				notifySimple(old -> new SpellActions.AddVariable(((SpellActions.AddVariable) old).key(), v)));
	}

	// --- PlaySound rows ---

	private void buildPlaySoundRows(SpellActions.PlaySoundAction ps) {
		addStringRow("Sound", ps.soundId().toString(), v -> {
			ResourceLocation id = ResourceLocation.tryParse(v);
			if (id != null) notifySimple(old -> new SpellActions.PlaySoundAction(id,
					((SpellActions.PlaySoundAction) old).volume(), ((SpellActions.PlaySoundAction) old).pitch()));
		});
		addFloatRow("Volume", ps.volume(), v ->
				notifySimple(old -> new SpellActions.PlaySoundAction(
						((SpellActions.PlaySoundAction) old).soundId(), v, ((SpellActions.PlaySoundAction) old).pitch())));
		addFloatRow("Pitch", ps.pitch(), v ->
				notifySimple(old -> new SpellActions.PlaySoundAction(
						((SpellActions.PlaySoundAction) old).soundId(), ((SpellActions.PlaySoundAction) old).volume(), v)));
	}

	// --- ForcePhase rows ---

	private void buildForcePhaseRows(SpellActions.ForcePhase fp) {
		addStringRow("Phase", fp.phaseId().toString(), v -> {
			ResourceLocation id = ResourceLocation.tryParse(v);
			if (id != null) notifySimple(old -> new SpellActions.ForcePhase(id));
		});
	}

	// --- RepeatAction rows ---

	private void buildRepeatRows(SpellActions.RepeatAction ra) {
		addNumberRow("Count", ra.count(), v ->
				notifySimple(old -> {
					var r = (SpellActions.RepeatAction) old;
					return new SpellActions.RepeatAction(v, r.indexVariable(), r.body());
				}));
		addStringRow("Index Var", ra.indexVariable(), v ->
				notifySimple(old -> {
					var r = (SpellActions.RepeatAction) old;
					return new SpellActions.RepeatAction(r.count(), v, r.body());
				}));
	}

	// --- Shared Origin/Mover row builders ---

	private static final String[] MOVER_TYPES = {"none", "acceleration", "rotate", "polar", "zero"};

	/**
	 * Read the current mover config from currentAction (not from a stale build-time snapshot).
	 */
	private Optional<MoverConfig> getCurrentMover() {
		if (currentAction instanceof FireDanmakuAction fda) return fda.mover();
		if (currentAction instanceof FireLaserAction fla) return fla.mover();
		return Optional.empty();
	}

	/**
	 * Read the current origin config from currentAction (not from a stale build-time snapshot).
	 */
	private OriginConfig getCurrentOrigin() {
		if (currentAction instanceof FireDanmakuAction fda) return fda.origin();
		if (currentAction instanceof FireLaserAction fla) return fla.origin();
		return OriginConfig.caster();
	}

	private void buildOriginOffsetRows(OriginConfig cfg, Consumer<OriginConfig> onChanged) {
		addNumberRow("Off X", cfg.offsetX(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), v, cur.offsetY(), cur.offsetZ(), cur.rotation()));
		});
		addNumberRow("Off Y", cfg.offsetY(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), cur.offsetX(), v, cur.offsetZ(), cur.rotation()));
		});
		addNumberRow("Off Z", cfg.offsetZ(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), cur.offsetX(), cur.offsetY(), v, cur.rotation()));
		});
		addNumberRow("Rot", cfg.rotation(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), cur.offsetX(), cur.offsetY(), cur.offsetZ(), v));
		});
	}

	/**
	 * @param onTypeChanged  called when mover type is cycled — triggers rebuild (new param rows)
	 * @param onParamChanged called when a mover parameter EditBox value changes — no rebuild (preserves focus)
	 */
	private void buildMoverRows(Optional<MoverConfig> moverOpt,
								 Consumer<Optional<MoverConfig>> onTypeChanged,
								 Consumer<Optional<MoverConfig>> onParamChanged) {
		String currentType = getMoverType(moverOpt);
		addStringCycleRow("Mover", MOVER_TYPES, currentType, newType -> {
			onTypeChanged.accept(createDefaultMover(newType));
		});

		if (moverOpt.isPresent()) {
			MoverConfig cfg = moverOpt.get();
			if (cfg instanceof MoverConfigs.AccelerationConfig acc) {
				addDoubleRow("Accel X", acc.acceleration().x, v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(
								new net.minecraft.world.phys.Vec3(v, a.acceleration().y, a.acceleration().z))));
					}
				});
				addDoubleRow("Accel Y", acc.acceleration().y, v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(
								new net.minecraft.world.phys.Vec3(a.acceleration().x, v, a.acceleration().z))));
					}
				});
				addDoubleRow("Accel Z", acc.acceleration().z, v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(
								new net.minecraft.world.phys.Vec3(a.acceleration().x, a.acceleration().y, v))));
					}
				});
			} else if (cfg instanceof MoverConfigs.RotateConfig rot) {
				addDoubleRow("Deg/tick", rot.degreesPerTick(), v ->
						onParamChanged.accept(Optional.of(new MoverConfigs.RotateConfig(v))));
			} else if (cfg instanceof MoverConfigs.PolarMoverConfig polar) {
				addDoubleRow("Radius", polar.radius(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel())));
					}
				});
				addDoubleRow("Rad Spd", polar.radialSpeed(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel())));
					}
				});
				addDoubleRow("Ang Spd", polar.angularSpeed(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel())));
					}
				});
				addDoubleRow("Init Ang", polar.initialAngle(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel())));
					}
				});
			}
		}
	}

	private static String getMoverType(Optional<MoverConfig> mover) {
		if (mover.isEmpty()) return "none";
		MoverConfig cfg = mover.get();
		if (cfg instanceof MoverConfigs.AccelerationConfig) return "acceleration";
		if (cfg instanceof MoverConfigs.RotateConfig) return "rotate";
		if (cfg instanceof MoverConfigs.PolarMoverConfig) return "polar";
		if (cfg instanceof MoverConfigs.ZeroMoverConfig) return "zero";
		return "none";
	}

	private static Optional<MoverConfig> createDefaultMover(String type) {
		return switch (type) {
			case "acceleration" -> Optional.of(new MoverConfigs.AccelerationConfig(new net.minecraft.world.phys.Vec3(0, -0.05, 0)));
			case "rotate" -> Optional.of(new MoverConfigs.RotateConfig(5.0));
			case "polar" -> Optional.of(new MoverConfigs.PolarMoverConfig(5.0, 0, 0, 0, 10.0, 0));
			case "zero" -> Optional.of(new MoverConfigs.ZeroMoverConfig());
			default -> Optional.empty();
		};
	}

	// --- Notification helpers ---

	private void notifyDanmaku(Function<FireDanmakuAction, SpellAction> modifier) {
		notifyDanmaku(modifier, true);
	}

	private void notifyDanmaku(Function<FireDanmakuAction, SpellAction> modifier, boolean rebuild) {
		if (currentAction instanceof FireDanmakuAction fda) {
			var newAction = modifier.apply(fda);
			currentAction = newAction;
			onActionChanged.accept(newAction);
			if (rebuild) {
				int idx = actionIndex;
				clearWidgets();
				if (newAction instanceof FireDanmakuAction nfda) {
					buildFireDanmakuRows(nfda);
				}
				actionIndex = idx;
				layoutWidgets();
			}
		}
	}

	private void notifyLaser(Function<FireLaserAction, SpellAction> modifier) {
		notifyLaser(modifier, true);
	}

	private void notifyLaser(Function<FireLaserAction, SpellAction> modifier, boolean rebuild) {
		if (currentAction instanceof FireLaserAction fla) {
			var newAction = modifier.apply(fla);
			currentAction = newAction;
			onActionChanged.accept(newAction);
			if (rebuild) {
				int idx = actionIndex;
				clearWidgets();
				if (newAction instanceof FireLaserAction nfla) {
					buildFireLaserRows(nfla);
				}
				actionIndex = idx;
				layoutWidgets();
			}
		}
	}

	private void notifyConditional(Function<SpellActions.ConditionalAction, SpellAction> modifier) {
		notifyConditional(modifier, true);
	}

	private void notifyConditional(Function<SpellActions.ConditionalAction, SpellAction> modifier, boolean rebuild) {
		if (currentAction instanceof SpellActions.ConditionalAction ca) {
			var newAction = modifier.apply(ca);
			currentAction = newAction;
			onActionChanged.accept(newAction);
			if (rebuild) {
				int idx = actionIndex;
				clearWidgets();
				if (newAction instanceof SpellActions.ConditionalAction nca) {
					buildConditionalRows(nca);
				}
				actionIndex = idx;
				layoutWidgets();
			}
		}
	}

	private void notifySimple(Function<SpellAction, SpellAction> modifier) {
		var newAction = modifier.apply(currentAction);
		currentAction = newAction;
		onActionChanged.accept(newAction);
	}

	// --- Row builders ---

	private <E extends Enum<E>> void addEnumRow(String label, E[] values, E current, Consumer<E> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var btn = Button.builder(Component.literal(formatEnum(current)), b -> {
			int idx = current.ordinal();
			E next = values[(idx + 1) % values.length];
			onChange.accept(next);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void addStringCycleRow(String label, String[] values, String current, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var btn = Button.builder(Component.literal(current), b -> {
			int idx = -1;
			for (int i = 0; i < values.length; i++) {
				if (values[i].equals(current)) {
					idx = i;
					break;
				}
			}
			String next = values[(idx + 1) % values.length];
			onChange.accept(next);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void addNumberRow(String label, NumberProvider provider, Consumer<NumberProvider> onChange) {
		double value = provider instanceof NumberProviders.Constant c ? c.value() : 0;
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setValue(formatNumber(value));
		editBox.setResponder(text -> {
			try {
				double v = Double.parseDouble(text);
				onChange.accept(NumberProvider.constant(v));
			} catch (NumberFormatException ignored) {
			}
		});
		String displayLabel = label;
		if (!(provider instanceof NumberProviders.Constant)) {
			displayLabel = label + "*";
		}
		rows.add(new EditorRow(displayLabel, editBox, false));
	}

	private void addBoolRow(String label, boolean value, Consumer<Boolean> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var btn = Button.builder(Component.literal(value ? "ON" : "OFF"), b -> {
			onChange.accept(!value);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void addIntRow(String label, int value, Consumer<Integer> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setValue(String.valueOf(value));
		editBox.setResponder(text -> {
			try {
				onChange.accept(Integer.parseInt(text));
			} catch (NumberFormatException ignored) {
			}
		});
		rows.add(new EditorRow(label, editBox, false));
	}

	private void addFloatRow(String label, float value, Consumer<Float> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setValue(String.format("%.2f", value));
		editBox.setResponder(text -> {
			try {
				onChange.accept(Float.parseFloat(text));
			} catch (NumberFormatException ignored) {
			}
		});
		rows.add(new EditorRow(label, editBox, false));
	}

	private void addDoubleRow(String label, double value, Consumer<Double> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setValue(formatNumber(value));
		editBox.setResponder(text -> {
			try {
				onChange.accept(Double.parseDouble(text));
			} catch (NumberFormatException ignored) {
			}
		});
		rows.add(new EditorRow(label, editBox, false));
	}

	private void addStringRow(String label, String value, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setValue(value);
		editBox.setResponder(onChange::accept);
		rows.add(new EditorRow(label, editBox, false));
	}

	private void addFullWidthButton(String text, Runnable onClick) {
		int widgetW = w - PADDING * 2;
		var btn = Button.builder(Component.literal(text), b -> onClick.run())
				.bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow("", btn, true));
	}

	// --- Layout ---

	private void layoutWidgets() {
		for (int i = 0; i < rows.size(); i++) {
			int rowY = y + PADDING + (i + 1) * ROW_HEIGHT - scrollOffset;
			var row = rows.get(i);
			if (row.fullWidth) {
				row.widget.setX(x + PADDING);
				row.widget.setWidth(w - PADDING * 2);
			} else {
				row.widget.setX(x + LABEL_WIDTH + PADDING * 2);
			}
			row.widget.setY(rowY);
			if (!widgetsRegistered) {
				addWidget.accept(row.widget);
			}
		}
		widgetsRegistered = true;
	}

	// --- Rendering ---

	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;

		// Panel background
		guiGraphics.fill(x, y, x + w, y + h, 0xCC1a1a2e);
		guiGraphics.fill(x, y, x + 1, y + h, 0xFF444466);

		if (typeSelectorMode) {
			guiGraphics.drawString(font, "Add Action", x + PADDING, y + PADDING + 2, 0xFFFFCC44, false);
			for (int i = 0; i < rows.size(); i++) {
				int rowY = y + PADDING + (i + 1) * ROW_HEIGHT - scrollOffset;
				rows.get(i).widget.visible = rowY >= y && rowY + ROW_HEIGHT <= y + h;
			}
			return;
		}

		// Title
		String title = currentAction == null ? "Select an action" : actionTypeName(currentAction);
		guiGraphics.drawString(font, title, x + PADDING, y + PADDING + 2, 0xFFFFCC44, false);

		if (currentAction == null) {
			guiGraphics.drawString(font, "Click an action in", x + PADDING, y + 30, 0xFF888888, false);
			guiGraphics.drawString(font, "the list below to", x + PADDING, y + 42, 0xFF888888, false);
			guiGraphics.drawString(font, "edit its properties", x + PADDING, y + 54, 0xFF888888, false);
			return;
		}

		// Delete button
		String deleteText = "[Delete]";
		int deleteX = x + w - font.width(deleteText) - PADDING;
		boolean deleteHovered = mouseX >= deleteX && mouseX < x + w
				&& mouseY >= y + PADDING && mouseY < y + PADDING + 12;
		guiGraphics.drawString(font, deleteText, deleteX, y + PADDING + 2,
				deleteHovered ? 0xFFFF4444 : 0xFFAA4444, false);

		if (rows.isEmpty()) {
			guiGraphics.drawString(font, "Read-only action", x + PADDING, y + 30, 0xFF888888, false);
			return;
		}

		// Row labels
		for (int i = 0; i < rows.size(); i++) {
			int rowY = y + PADDING + (i + 1) * ROW_HEIGHT - scrollOffset;
			var row = rows.get(i);
			boolean visible = rowY >= y && rowY + ROW_HEIGHT <= y + h;
			row.widget.visible = visible;
			if (visible && !row.fullWidth && !row.label.isEmpty()) {
				guiGraphics.drawString(font, row.label, x + PADDING, rowY + 4, 0xFFBBBBBB, false);
			}
		}
	}

	// --- Mouse handling ---

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || currentAction == null) return false;
		Font font = Minecraft.getInstance().font;
		String deleteText = "[Delete]";
		int deleteX = x + w - font.width(deleteText) - PADDING;
		if (mouseX >= deleteX && mouseX < x + w
				&& mouseY >= y + PADDING && mouseY < y + PADDING + 12) {
			onDeleteAction.run();
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (isMouseOver(mouseX, mouseY)) {
			int maxScroll = Math.max(0, (rows.size() + 1) * ROW_HEIGHT - h);
			scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * ROW_HEIGHT));
			layoutWidgets();
			return true;
		}
		return false;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	public int getActionIndex() {
		return actionIndex;
	}

	public SpellAction getCurrentAction() {
		return currentAction;
	}

	// --- Condition helpers ---

	private static String getConditionType(SpellCondition cond) {
		String id = SpellConditions.getTypeId(cond);
		return id != null ? id : "always";
	}

	private static SpellCondition createDefaultCondition(String type) {
		return switch (type) {
			case "tick_interval" -> new SpellConditions.TickInterval(20, 0);
			case "health_below" -> new SpellConditions.HealthBelow(0.5f);
			case "health_above" -> new SpellConditions.HealthAbove(0.5f);
			case "tick_elapsed" -> new SpellConditions.TickElapsed(100);
			case "distance_above" -> new SpellConditions.DistanceAbove(10);
			case "distance_below" -> new SpellConditions.DistanceBelow(5);
			case "hit_count" -> new SpellConditions.HitCountCondition(3);
			case "and" -> new SpellConditions.AndCondition(List.of(
					new SpellConditions.TickInterval(20, 0),
					new SpellConditions.AlwaysCondition(true)));
			default -> new SpellConditions.AlwaysCondition(true);
		};
	}

	// --- Utility ---

	private static final Map<String, String> ACTION_TYPE_NAMES = Map.ofEntries(
			Map.entry("fire_danmaku", "Fire Danmaku"),
			Map.entry("fire_laser", "Fire Laser"),
			Map.entry("conditional", "Conditional"),
			Map.entry("repeat", "Repeat"),
			Map.entry("set_variable", "Set Variable"),
			Map.entry("add_variable", "Add Variable"),
			Map.entry("clear_screen", "Clear Screen"),
			Map.entry("play_sound", "Play Sound"),
			Map.entry("force_phase", "Force Phase"),
			Map.entry("sequence", "Sequence"),
			Map.entry("noop", "Noop"),
			Map.entry("legacy_ticker", "Legacy Ticker")
	);

	private static String actionTypeName(SpellAction action) {
		String id = SpellActions.getTypeId(action);
		if (id != null) {
			String name = ACTION_TYPE_NAMES.get(id);
			if (name != null) return name;
			return id;
		}
		return action.getClass().getSimpleName();
	}

	private static String formatNumber(double value) {
		if (value == (long) value) return String.valueOf((long) value);
		return String.format("%.2f", value);
	}

	private static <E extends Enum<E>> String formatEnum(E value) {
		return value.name().toLowerCase().replace('_', ' ');
	}

	private record EditorRow(String label, AbstractWidget widget, boolean fullWidth) {
		EditorRow(String label, AbstractWidget widget) {
			this(label, widget, false);
		}
	}

	// --- AimMode helpers ---

	private static String getAimModeType(AimMode mode) {
		String id = AimMode.AimModes.getTypeId(mode);
		return id != null ? id : "target";
	}

	private static AimMode createDefaultAimMode(String type) {
		return switch (type) {
			case "target" -> new AimMode.AimModes.Target();
			case "fixed" -> new AimMode.AimModes.FixedDirection(new net.minecraft.world.phys.Vec3(0, 0, 1));
			case "caster_facing" -> new AimMode.AimModes.CasterFacing();
			case "angle_offset" -> new AimMode.AimModes.AngleOffset(NumberProvider.constant(0));
			case "variable_angle" -> new AimMode.AimModes.VariableAngle("aim_angle");
			default -> new AimMode.AimModes.Target();
		};
	}

}
