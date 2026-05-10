package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

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
	private static final int DROPDOWN_ITEM_H = 16;
	private static final int DROPDOWN_MAX_VISIBLE = 10;

	private static final String[] CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count",
			"target_on_ground", "target_speed", "random_chance",
			"target_health_below", "target_health_above",
			"target_is_flying", "target_is_fallflying",
			"dynamic_tick_interval", "entity_trait", "entity_flag", "compare", "variable_check",
			"difficulty_equals", "difficulty_above",
			"always", "not", "and", "or"
	};

	private static final String[] SIMPLE_CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count",
			"target_on_ground", "target_speed", "random_chance",
			"target_health_below", "target_health_above",
			"target_is_flying", "target_is_fallflying",
			"dynamic_tick_interval", "entity_trait", "entity_flag", "compare", "variable_check",
			"difficulty_equals", "difficulty_above",
			"always"
	};

	private static final String[] AIM_MODE_TYPES = {
			"target", "direction_to_target", "fixed", "caster_facing", "angle_offset", "variable_angle", "random_angle"
	};

	private final Consumer<AbstractWidget> addWidget;
	private final Consumer<AbstractWidget> removeWidget;
	private final Consumer<SpellAction> onActionChanged;
	private final Runnable onDeleteAction;
	private java.util.function.Supplier<List<ResourceLocation>> phaseOptionsSupplier = List::of;
	private java.util.function.Function<ResourceLocation, String> phaseDisplayFormatter = ResourceLocation::toString;
	private java.util.function.Supplier<List<ResourceLocation>> spellOptionsSupplier = List::of;
	private java.util.function.Function<ResourceLocation, String> spellDisplayFormatter = ResourceLocation::toString;

	private int x, y, w, h;
	private SpellAction currentAction;
	private int actionIndex = -1;
	private final List<EditorRow> rows = new ArrayList<>();
	private int scrollOffset = 0;
	private boolean widgetsRegistered = false;
	private boolean scrollbarDragging = false;

	// Type selector mode
	private boolean typeSelectorMode = false;
	private Consumer<SpellAction> typeSelectorCallback;

	// Dropdown overlay state
	private DropdownOverlay dropdown = null;
	private int dropdownHoverIndex = -1;
	private int dropdownScrollOffset = 0;

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
		boolean changed = (this.x != x || this.y != y || this.w != w || this.h != h);
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		if (changed && widgetsRegistered) {
			layoutWidgets();
		}
	}

	public void setAction(SpellAction action, int index) {
		if (action == currentAction && index == actionIndex) return;
		clearWidgets();
		this.currentAction = action;
		this.actionIndex = index;
		this.scrollOffset = 0;
		this.typeSelectorMode = false;
		buildActionRows(action);
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

	public void setPhaseOptions(java.util.function.Supplier<List<ResourceLocation>> supplier,
								java.util.function.Function<ResourceLocation, String> formatter) {
		this.phaseOptionsSupplier = supplier != null ? supplier : List::of;
		this.phaseDisplayFormatter = formatter != null ? formatter : ResourceLocation::toString;
	}

	public void setSpellOptions(java.util.function.Supplier<List<ResourceLocation>> supplier,
								java.util.function.Function<ResourceLocation, String> formatter) {
		this.spellOptionsSupplier = supplier != null ? supplier : List::of;
		this.spellDisplayFormatter = formatter != null ? formatter : ResourceLocation::toString;
	}

	public void refreshCurrentView() {
		if (typeSelectorMode) {
			clearWidgets();
			buildTypeSelectorRows();
			layoutWidgets();
			return;
		}
		if (currentAction != null) {
			var action = currentAction;
			int index = actionIndex;
			clearWidgets();
			this.currentAction = action;
			this.actionIndex = index;
			buildActionRows(action);
			layoutWidgets();
		}
	}

	private void clearWidgets() {
		closeDropdown();
		closeExprCompletion();
		for (var row : rows) {
			removeWidget.accept(row.widget());
		}
		rows.clear();
		exprEditBoxes.clear();
		widgetsRegistered = false;
	}

	/**
	 * 设置所有已注册 widget 的可见性。用于 Dock Tab 切换时隐藏/显示。
	 */
	public void setAllWidgetsVisible(boolean visible) {
		for (var row : rows) {
			row.widget().visible = visible;
		}
	}

	// --- Type selector ---

	private void buildActionRows(SpellAction action) {
		// Unwrap DisabledAction to edit the inner action
		if (action instanceof SpellActions.DisabledAction da) {
			addFullWidthButton("\u26A0 DISABLED (press D to enable)", () -> {});
			buildActionRows(da.inner());
			return;
		}
		if (action instanceof FireDanmakuAction fda) {
			buildFireDanmakuRows(fda);
		} else if (action instanceof FireLaserAction fla) {
			buildFireLaserRows(fla);
		} else if (action instanceof FireTextDanmakuAction ftda) {
			buildFireTextDanmakuRows(ftda);
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
		} else if (action instanceof SpellActions.ForceSpell fs) {
			buildForceSpellRows(fs);
		} else if (action instanceof SpellActions.RepeatAction ra) {
			buildRepeatRows(ra);
		} else if (action instanceof DelayAction da) {
			buildDelayRows(da);
		} else if (action instanceof TeleportAction ta) {
			buildTeleportRows(ta);
		} else if (action instanceof SpawnShooterAction ssa) {
			buildSpawnShooterRows(ssa);
		} else if (action instanceof BurstAction ba) {
			buildBurstRows(ba);
		} else if (action instanceof SpellActions.SequenceAction sa) {
			buildSequenceRows(sa);
		} else if (action instanceof ConfineTargetAction cta) {
			buildConfineTargetRows(cta);
		} else if (action instanceof SetEntityFlagAction sefa) {
			buildSetEntityFlagRows(sefa);
		} else if (action instanceof TeleportRandomAction tra) {
			buildTeleportRandomRows(tra);
		}
	}

	private void buildTypeSelectorRows() {
		addFullWidthButton("Fire Danmaku", () -> selectType("fire_danmaku"));
		addFullWidthButton("Fire Laser", () -> selectType("fire_laser"));
		addFullWidthButton("Fire Text Danmaku", () -> selectType("fire_text_danmaku"));
		addFullWidthButton("Conditional", () -> selectType("conditional"));
		addFullWidthButton("Repeat", () -> selectType("repeat"));
		addFullWidthButton("Delay", () -> selectType("delay"));
		addFullWidthButton("Teleport", () -> selectType("teleport"));
		addFullWidthButton("Spawn Shooter", () -> selectType("spawn_shooter"));
		addFullWidthButton("Burst", () -> selectType("burst"));
		addFullWidthButton("Set Variable", () -> selectType("set_variable"));
		addFullWidthButton("Add Variable", () -> selectType("add_variable"));
		addFullWidthButton("Sequence", () -> selectType("sequence"));
		addFullWidthButton("Clear Screen", () -> selectType("clear_screen"));
		addFullWidthButton("Play Sound", () -> selectType("play_sound"));
		addFullWidthButton("Force Phase", () -> selectType("force_phase"));
		addFullWidthButton("Force Spell", () -> selectType("force_spell"));
		addFullWidthButton("Confine Target", () -> selectType("confine_target"));
		addFullWidthButton("Set Entity Flag", () -> selectType("set_entity_flag"));
		addFullWidthButton("Teleport Random", () -> selectType("teleport_random"));
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
					YHDanmaku.Bullet.CIRCLE, ColorProvider.constant(DyeColor.WHITE),
					NumberProvider.constant(8), NumberProvider.constant(0.5),
					NumberProvider.constant(100), NumberProvider.constant(0),
					NumberProvider.constant(360), NumberProvider.constant(0),
					PatternType.RING,
					OriginConfig.caster(), new AimMode.AimModes.Target(),
					Optional.empty(), Optional.empty(), Optional.empty(),
					Optional.empty(), 1);
		case "fire_laser" -> new FireLaserAction(
				YHDanmaku.Laser.LASER, DyeColor.WHITE,
				NumberProvider.constant(60), NumberProvider.constant(80),
				NumberProvider.constant(0), new AimMode.AimModes.Target(),
				OriginConfig.caster(), Optional.empty(), 0, 0, 0);
		case "fire_text_danmaku" -> new FireTextDanmakuAction(
				"言弾", 0xFFFFFFFF, false,
				NumberProvider.constant(100), NumberProvider.constant(dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity.DEFAULT_SIZE),
				NumberProvider.constant(0), NumberProvider.constant(0),
				NumberProvider.constant(0),
				new AimMode.AimModes.Target(), OriginConfig.caster(),
				Optional.empty(), 0, 0, 0, Optional.empty());
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
					new ResourceLocation("youkaishomecoming", "main"), true);
			case "force_spell" -> new SpellActions.ForceSpell(
					new ResourceLocation("youkaishomecoming", "main"), true);
			case "delay" -> new DelayAction(20, new ArrayList<>());
			case "teleport" -> new TeleportAction(OriginConfig.caster(), true);
			case "spawn_shooter" -> new SpawnShooterAction(40, 4f, 100,
					OriginConfig.caster(),
					NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
					Optional.empty(), new ArrayList<>());
		case "burst" -> new BurstAction(3, 5, new ArrayList<>());
		case "sequence" -> new SpellActions.SequenceAction(new ArrayList<>());
		case "confine_target" -> new ConfineTargetAction(32, 1.0);
		case "set_entity_flag" -> new SetEntityFlagAction(4, true);
		case "teleport_random" -> new TeleportRandomAction(32, 0.8, 0.4, 16, true, true);
		default -> new SpellActions.NoopAction();
		};
	}

	// --- FireDanmaku rows ---

	private void buildFireDanmakuRows(FireDanmakuAction a) {
		addEnumRow("Bullet", YHDanmaku.Bullet.values(), a.bulletType(), v ->
				notifyDanmaku(old -> old.withBulletType(v)));

		// Color: if constant, show DyeColor dropdown; otherwise show type label
		if (a.color() instanceof ColorProvider.Constant cc) {
			addEnumRow("Color", DyeColor.values(), cc.color(), v ->
					notifyDanmaku(old -> old.withColor(ColorProvider.constant(v))));
		} else {
			// For dynamic color providers (cycle, by_variable), show a read-only label
			String colorType = ColorProvider.CLASS_TO_TYPE.getOrDefault(a.color().getClass(), "dynamic");
			addStringRow("Color", colorType, v -> {}); // read-only display
		}

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

		addNumberRow("Elevation", a.elevation(), v ->
				notifyDanmaku(old -> old.withElevation(v), false));

		addEnumRow("Pattern", PatternType.values(), a.pattern(), v ->
				notifyDanmaku(old -> old.withPattern(v)));

		// NESTED_RING, GRID: show outerCount row
		// SPHERE/SPHERE_RANDOM use count as total — no outerCount needed
		if (a.pattern() == PatternType.NESTED_RING || a.pattern() == PatternType.GRID) {
			String label = switch (a.pattern()) {
				case GRID -> "Cols";
				default -> "Outer Cnt";
			};
			NumberProvider outerProv = a.outerCount().orElse(NumberProvider.constant(1));
			addNumberRow(label, outerProv, v ->
					notifyDanmaku(old -> old.withOuterCount(Optional.of(v)), false));
		}

		// AimMode dropdown
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

		// Trail interval (only show if onTrail is used)
		if (a.onTrail().isPresent()) {
			addIntRow("Trail Intv", a.trailInterval(), v ->
					notifyDanmaku(old -> old.withTrailInterval(v), false));
		}

		// Tilt angle: for NESTED_RING controls inner ring axis (0°=vertical, 90°=perpendicular);
		// for other patterns tilts the entire orientation plane.
		// NESTED_RING always shows tilt (default 0°); other patterns show add/remove toggle.
		if (a.pattern() == PatternType.NESTED_RING) {
			NumberProvider tiltProv = a.tiltAngle().orElse(NumberProvider.constant(0));
			addNumberRow("Axis Tilt", tiltProv, v ->
					notifyDanmaku(old -> old.withTiltAngle(Optional.of(v)), false));
		} else if (a.tiltAngle().isPresent()) {
			addNumberRow("Tilt Angle", a.tiltAngle().get(), v ->
					notifyDanmaku(old -> old.withTiltAngle(Optional.of(v)), false));
			addFullWidthButton("[Remove Tilt]", () ->
					notifyDanmaku(old -> old.withTiltAngle(Optional.empty())));
		} else {
			addFullWidthButton("[+ Tilt Angle]", () ->
					notifyDanmaku(old -> old.withTiltAngle(Optional.of(NumberProvider.constant(0)))));
		}

		// Hit behavior: separate entity/block controls
		addEnumRow("Hit Entity", HitBehavior.values(), a.hitBehaviorEntity(), v ->
				notifyDanmaku(old -> old.withHitBehaviorEntity(v)));
		addEnumRow("Hit Block", HitBehavior.values(), a.hitBehaviorBlock(), v ->
				notifyDanmaku(old -> old.withHitBehaviorBlock(v)));

		// Damage type override: optional, defaults to standard danmaku damage
		if (a.damageType().isPresent()) {
			addEnumRow("Dmg Type", dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.values(),
					a.damageType().get(), v ->
					notifyDanmaku(old -> old.withDamageType(Optional.of(v))));
			addFullWidthButton("[- Remove Dmg Type]", () ->
					notifyDanmaku(old -> old.withDamageType(Optional.empty())));
		} else {
			addFullWidthButton("[+ Damage Type]", () ->
					notifyDanmaku(old -> old.withDamageType(Optional.of(
							dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.ABYSSAL))));
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

		addNumberRow("Elevation", a.elevation(), v ->
				notifyLaser(old -> old.withElevation(v), false));

		// AimMode dropdown
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

		// Delayed mover fields
		if (a.delayedV0().isPresent()) {
			addDoubleRow("Delayed V0", a.delayedV0().get(), v ->
					notifyLaser(old -> old.withDelayedV0(Optional.of(v)), false));
		}
		if (a.delayedV1().isPresent()) {
			addDoubleRow("Delayed V1", a.delayedV1().get(), v ->
					notifyLaser(old -> old.withDelayedV1(Optional.of(v)), false));
		}
		if (a.delayedV0().isEmpty() && a.delayedV1().isEmpty()) {
			addFullWidthButton("[+ Delayed Mover]", () -> notifyLaser(old ->
					old.withDelayedV0(Optional.of(0.1)).withDelayedV1(Optional.of(0.5))));
		} else {
			addFullWidthButton("[- Remove Delayed]", () -> notifyLaser(old ->
					old.withDelayedV0(Optional.empty()).withDelayedV1(Optional.empty())));
		}

		// Damage type override: optional, defaults to standard danmaku damage
		if (a.damageType().isPresent()) {
			addEnumRow("Dmg Type", dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.values(),
					a.damageType().get(), v ->
					notifyLaser(old -> old.withDamageType(Optional.of(v))));
			addFullWidthButton("[- Remove Dmg Type]", () ->
					notifyLaser(old -> old.withDamageType(Optional.empty())));
		} else {
			addFullWidthButton("[+ Damage Type]", () ->
					notifyLaser(old -> old.withDamageType(Optional.of(
							dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.ABYSSAL))));
		}
	}

	// --- FireTextDanmaku rows ---

	private void buildFireTextDanmakuRows(FireTextDanmakuAction a) {
		addStringRow("Text", a.text(), v ->
				notifyTextDanmaku(old -> old.withText(v)));

		addColorRow("Text Color", a.textColor(), v ->
				notifyTextDanmaku(old -> old.withTextColor(v), false));

		addBooleanRow("Per Char", a.perChar(), v ->
				notifyTextDanmaku(old -> old.withPerChar(v)));

		addNumberRow("Lifetime", a.lifetime(), v ->
				notifyTextDanmaku(old -> old.withLifetime(v), false));

		addNumberRow("Size", a.size(), v ->
				notifyTextDanmaku(old -> old.withSize(v), false));

		addNumberRow("Angle", a.angleOffset(), v ->
				notifyTextDanmaku(old -> old.withAngleOffset(v), false));

		addNumberRow("Elevation", a.elevation(), v ->
				notifyTextDanmaku(old -> old.withElevation(v), false));

		if (!a.perChar()) {
			addNumberRow("Roll", a.roll(), v ->
					notifyTextDanmaku(old -> old.withRoll(v), false));
		}

		// AimMode dropdown
		String currentAim = getAimModeType(a.aimMode());
		addStringCycleRow("Aim Mode", AIM_MODE_TYPES, currentAim, newType -> {
			AimMode newMode = createDefaultAimMode(newType);
			notifyTextDanmaku(old -> old.withAimMode(newMode));
		});

		// OriginConfig mode
		addEnumRow("Origin", OriginConfig.OriginMode.values(), a.origin().mode(), v -> {
			var newOrigin = new OriginConfig(v, a.origin().offsetX(), a.origin().offsetY(),
					a.origin().offsetZ(), a.origin().rotation());
			notifyTextDanmaku(old -> old.withOrigin(newOrigin));
		});

		// setupTime params
		addIntRow("Prepare", a.setupPrepare(), v ->
				notifyTextDanmaku(old -> old.withSetupPrepare(v), false));
		addIntRow("Start", a.setupStart(), v ->
				notifyTextDanmaku(old -> old.withSetupStart(v), false));
		addIntRow("End", a.setupEnd(), v ->
				notifyTextDanmaku(old -> old.withSetupEnd(v), false));

		// OriginConfig offsets
		buildOriginOffsetRows(a.origin(), newOrigin -> notifyTextDanmaku(old -> old.withOrigin(newOrigin), false));

		// Mover
		buildMoverRows(a.mover(),
				newMover -> notifyTextDanmaku(old -> old.withMover(newMover)),
				newMover -> notifyTextDanmaku(old -> old.withMover(newMover), false));

		// Damage type override
		if (a.damageType().isPresent()) {
			addEnumRow("Dmg Type", dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.values(),
					a.damageType().get(), v ->
					notifyTextDanmaku(old -> old.withDamageType(Optional.of(v))));
			addFullWidthButton("[- Remove Dmg Type]", () ->
					notifyTextDanmaku(old -> old.withDamageType(Optional.empty())));
		} else {
			addFullWidthButton("[+ Damage Type]", () ->
					notifyTextDanmaku(old -> old.withDamageType(Optional.of(
							dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType.ABYSSAL))));
		}
	}

	private void addBooleanRow(String label, boolean value, Consumer<Boolean> onChange) {
		addBoolRow(label, value, onChange);
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
			buildCompoundConditionRows(ac.conditions(), true);
		} else if (cond instanceof SpellConditions.OrCondition oc) {
			buildCompoundConditionRows(oc.conditions(), false);
		} else {
			buildConditionParamRows("", cond, newCond ->
					notifyConditional(old -> new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse()), false));
		}
	}

	/**
	 * Build sub-condition editing rows for AND/OR compound conditions.
	 * @param isAnd true = AndCondition, false = OrCondition
	 */
	private void buildCompoundConditionRows(List<SpellCondition> subs, boolean isAnd) {
		for (int i = 0; i < subs.size(); i++) {
			SpellCondition sub = subs.get(i);
			int idx = i;
			addStringCycleRow("Cond " + (i + 1), SIMPLE_CONDITION_TYPES, getConditionType(sub), newType ->
					notifyCompoundSubCondition(idx, createDefaultCondition(newType), isAnd));
			buildConditionParamRows((idx + 1) + ":", sub, newSub ->
					notifyCompoundSubCondition(idx, newSub, isAnd, false));
		}

		// Button to add more sub-conditions
		addFullWidthButton("[+ Add Condition]", () -> {
			notifyConditional(old -> {
				List<SpellCondition> newSubs = new ArrayList<>(extractCompoundSubs(old.condition()));
				newSubs.add(new SpellConditions.AlwaysCondition(true));
				SpellCondition newCond = isAnd ? new SpellConditions.AndCondition(newSubs)
						: new SpellConditions.OrCondition(newSubs);
				return new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse());
			});
		});
	}

	private void notifyCompoundSubCondition(int subIndex, SpellCondition newSub, boolean isAnd) {
		notifyCompoundSubCondition(subIndex, newSub, isAnd, true);
	}

	private void notifyCompoundSubCondition(int subIndex, SpellCondition newSub, boolean isAnd, boolean rebuild) {
		notifyConditional(old -> {
			List<SpellCondition> newSubs = new ArrayList<>(extractCompoundSubs(old.condition()));
			while (newSubs.size() <= subIndex) newSubs.add(new SpellConditions.AlwaysCondition(true));
			newSubs.set(subIndex, newSub);
			SpellCondition newCond = isAnd ? new SpellConditions.AndCondition(newSubs)
					: new SpellConditions.OrCondition(newSubs);
			return new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse());
		}, rebuild);
	}

	private static List<SpellCondition> extractCompoundSubs(SpellCondition cond) {
		if (cond instanceof SpellConditions.AndCondition ac) return ac.conditions();
		if (cond instanceof SpellConditions.OrCondition oc) return oc.conditions();
		return List.of(cond);
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
		} else if (cond instanceof SpellConditions.TargetOnGround) {
			// No parameters - just a label
		} else if (cond instanceof SpellConditions.TargetSpeed ts) {
			addDoubleRow(prefix + "Threshold", ts.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetSpeed(v, ts.op())));
			addStringCycleRow(prefix + "Op", new String[]{">", ">=", "<", "<="}, ts.op(), v ->
					onChanged.accept(new SpellConditions.TargetSpeed(ts.threshold(), v)));
		} else if (cond instanceof SpellConditions.RandomChance rc) {
			addFloatRow(prefix + "Probability", rc.probability(), v ->
					onChanged.accept(new SpellConditions.RandomChance(v)));
		} else if (cond instanceof SpellConditions.TargetHealthBelow thb) {
			addFloatRow(prefix + "Threshold", thb.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetHealthBelow(v)));
		} else if (cond instanceof SpellConditions.TargetHealthAbove tha) {
			addFloatRow(prefix + "Threshold", tha.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetHealthAbove(v)));
		} else if (cond instanceof SpellConditions.TargetIsFlying) {
			// No parameters
		} else if (cond instanceof SpellConditions.TargetIsFallFlying) {
			// No parameters
		} else if (cond instanceof SpellConditions.AlwaysCondition ac) {
			addStringCycleRow(prefix + "Value", new String[]{"true", "false"},
					ac.value() ? "true" : "false", v ->
					onChanged.accept(new SpellConditions.AlwaysCondition(v.equals("true"))));
		} else if (cond instanceof SpellConditions.DynamicTickInterval dti) {
			addNumberRow(prefix + "Period", dti.period(), v ->
					onChanged.accept(new SpellConditions.DynamicTickInterval(v, dti.offset())));
			addNumberRow(prefix + "Offset", dti.offset(), v ->
					onChanged.accept(new SpellConditions.DynamicTickInterval(dti.period(), v)));
		} else if (cond instanceof SpellConditions.EntityTrait et) {
			addStringRow(prefix + "Trait", et.trait(), v ->
					onChanged.accept(new SpellConditions.EntityTrait(v)));
		} else if (cond instanceof SpellConditions.EntityFlagCondition ef) {
			addIntRow(prefix + "Flag", ef.flag(), v ->
					onChanged.accept(new SpellConditions.EntityFlagCondition(v)));
		} else if (cond instanceof SpellConditions.CompareNumbers cn) {
			addNumberRow(prefix + "Left", cn.left(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(v, cn.op(), cn.right())));
			addStringCycleRow(prefix + "Op", new String[]{"<", ">", "==", "!=", "<=", ">="}, cn.op(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(cn.left(), v, cn.right())));
			addNumberRow(prefix + "Right", cn.right(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(cn.left(), cn.op(), v)));
		} else if (cond instanceof SpellConditions.VariableCheck vc) {
			addStringRow(prefix + "Key", vc.key(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(v, vc.op(), vc.value())));
			addStringCycleRow(prefix + "Op", new String[]{"==", "!=", "<", ">", "<=", ">="}, vc.op(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(vc.key(), v, vc.value())));
			addDoubleRow(prefix + "Value", vc.value(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(vc.key(), vc.op(), v)));
		} else if (cond instanceof SpellConditions.DifficultyEquals de) {
			addStringCycleRow(prefix + "Difficulty", new String[]{"PEACEFUL", "EASY", "NORMAL", "HARD"},
					difficultyName(de.difficultyId()), v ->
					onChanged.accept(new SpellConditions.DifficultyEquals(difficultyId(v))));
		} else if (cond instanceof SpellConditions.DifficultyAbove da) {
			addStringCycleRow(prefix + "Min Diff", new String[]{"PEACEFUL", "EASY", "NORMAL", "HARD"},
					difficultyName(da.minDifficultyId()), v ->
					onChanged.accept(new SpellConditions.DifficultyAbove(difficultyId(v))));
		} else if (cond instanceof SpellConditions.NotCondition nc) {
			// Show inner condition type and params
			addStringCycleRow(prefix + "Inner", SIMPLE_CONDITION_TYPES, getConditionType(nc.condition()), newType ->
					onChanged.accept(new SpellConditions.NotCondition(createDefaultCondition(newType))));
			buildConditionParamRows(prefix + "!", nc.condition(), inner ->
					onChanged.accept(new SpellConditions.NotCondition(inner)));
		}
	}

	// --- SetVariable / AddVariable rows ---

	private void buildSetVariableRows(SpellActions.SetVariable sv) {
		addStringRow("Key", sv.key(), v ->
				notifySimple(old -> new SpellActions.SetVariable(v, ((SpellActions.SetVariable) old).value())));
		addNumberRow("Value", sv.value(), v ->
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
		List<ResourceLocation> phaseOptions = phaseOptionsSupplier.get();
		if (phaseOptions != null && !phaseOptions.isEmpty()) {
			addChoiceRow("Phase ID", phaseOptions, fp.phaseId(), this::formatPhaseOption, id ->
					notifySimple(old -> new SpellActions.ForcePhase(id, fp.clearScreen())));
		} else {
			addStringRow("Phase ID", fp.phaseId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.ForcePhase(id, fp.clearScreen()));
			});
		}
		if (phaseOptions == null || !phaseOptions.contains(fp.phaseId())) {
			addStringRow("Raw ID", fp.phaseId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.ForcePhase(id, fp.clearScreen()));
			});
		}
		addBoolRow("Clear Screen", fp.clearScreen(), v ->
				notifySimple(old -> new SpellActions.ForcePhase(fp.phaseId(), v), true));
	}

	private String formatPhaseOption(ResourceLocation phaseId) {
		String formatted = phaseDisplayFormatter.apply(phaseId);
		if (formatted == null || formatted.isBlank()) {
			return phaseId.toString();
		}
		return formatted;
	}

	private <T> void addChoiceRow(String label, List<T> values, T current,
								  java.util.function.Function<T, String> display,
								  Consumer<T> onChange) {
		if (values == null || values.isEmpty()) {
			return;
		}
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		String[] displayNames = new String[values.size()];
		int selectedIndex = -1;
		for (int i = 0; i < values.size(); i++) {
			T value = values.get(i);
			displayNames[i] = display.apply(value);
			if (selectedIndex < 0 && java.util.Objects.equals(value, current)) {
				selectedIndex = i;
			}
		}
		String currentLabel = selectedIndex >= 0 ? displayNames[selectedIndex] : display.apply(current) + " (missing)";
		final int initialSelectedIndex = selectedIndex;
		int rowIndex = rows.size();
		var btn = Button.builder(Component.literal(currentLabel + " \u25BC"), b -> {
			openDropdown(displayNames, initialSelectedIndex, idx -> onChange.accept(values.get(idx)), rowIndex);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void buildForceSpellRows(SpellActions.ForceSpell fs) {
		List<ResourceLocation> spellOptions = spellOptionsSupplier.get();
		if (spellOptions != null && !spellOptions.isEmpty()) {
			addChoiceRow("Spell ID", spellOptions, fs.spellId(), this::formatSpellOption, id ->
					notifySimple(old -> new SpellActions.ForceSpell(id, fs.clearScreen())));
		} else {
			addStringRow("Spell ID", fs.spellId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.ForceSpell(id, fs.clearScreen()));
			});
		}
		if (spellOptions == null || !spellOptions.contains(fs.spellId())) {
			addStringRow("Raw ID", fs.spellId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.ForceSpell(id, fs.clearScreen()));
			});
		}
		addBoolRow("Clear Screen", fs.clearScreen(), v ->
				notifySimple(old -> new SpellActions.ForceSpell(fs.spellId(), v), true));
	}

	private String formatSpellOption(ResourceLocation spellId) {
		String formatted = spellDisplayFormatter.apply(spellId);
		if (formatted == null || formatted.isBlank()) {
			return spellId.toString();
		}
		return formatted;
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

	// --- Delay rows ---

	private void buildDelayRows(DelayAction da) {
		addNumberRow("Delay Ticks", da.delayTicks(), v ->
				notifySimple(old -> {
					var d = (DelayAction) old;
					return new DelayAction(v, d.body());
				}));
	}

	// --- Teleport rows ---

	private void buildTeleportRows(TeleportAction ta) {
		addEnumRow("Origin", OriginConfig.OriginMode.values(), ta.destination().mode(), v -> {
			var newDest = new OriginConfig(v, ta.destination().offsetX(), ta.destination().offsetY(),
					ta.destination().offsetZ(), ta.destination().rotation());
			notifySimple(old -> new TeleportAction(newDest, ((TeleportAction) old).playSound()));
		});
		buildOriginOffsetRows(ta.destination(), newDest ->
				notifySimple(old -> new TeleportAction(newDest, ((TeleportAction) old).playSound())));
		addBoolRow("Play Sound", ta.playSound(), v ->
				notifySimple(old -> new TeleportAction(((TeleportAction) old).destination(), v)));
	}

	// --- Burst rows ---

	private void buildBurstRows(BurstAction ba) {
		addIntRow("Waves", ba.waves(), v ->
				notifySimple(old -> {
					var b = (BurstAction) old;
					return new BurstAction(v, b.interval(), b.waveVariable(), b.body());
				}));
		addIntRow("Interval", ba.interval(), v ->
				notifySimple(old -> {
					var b = (BurstAction) old;
					return new BurstAction(b.waves(), v, b.waveVariable(), b.body());
				}));
		addStringRow("Wave Var", ba.waveVariable(), v ->
				notifySimple(old -> {
					var b = (BurstAction) old;
					return new BurstAction(b.waves(), b.interval(), v, b.body());
				}));
	}

	// --- Sequence rows ---

	private void buildSequenceRows(SpellActions.SequenceAction sa) {
		addIntRow("Actions", sa.actions().size(), v -> {}); // read-only count display
	}

	// --- Confine Target rows ---

	private void buildConfineTargetRows(ConfineTargetAction cta) {
		addDoubleRow("Max Distance", cta.maxDistance(), v ->
				notifySimple(old -> new ConfineTargetAction(v, ((ConfineTargetAction) old).pushSpeed())));
		addDoubleRow("Push Speed", cta.pushSpeed(), v ->
				notifySimple(old -> new ConfineTargetAction(((ConfineTargetAction) old).maxDistance(), v)));
	}

	// --- Set Entity Flag rows ---

	private void buildSetEntityFlagRows(SetEntityFlagAction sefa) {
		addIntRow("Flag", sefa.flag(), v ->
				notifySimple(old -> new SetEntityFlagAction(v, ((SetEntityFlagAction) old).enable())));
		addStringCycleRow("Enable", new String[]{"true", "false"}, sefa.enable() ? "true" : "false", v ->
				notifySimple(old -> new SetEntityFlagAction(((SetEntityFlagAction) old).flag(), v.equals("true"))));
	}

	// --- Teleport Random rows ---

	private void buildTeleportRandomRows(TeleportRandomAction tra) {
		addDoubleRow("Max Dist", tra.maxDistance(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(v, t.minDistanceFactor(), t.distanceVariance(), t.attempts(), t.upwardBias(), t.playSound());
				}));
		addDoubleRow("Min Dist %", tra.minDistanceFactor(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(t.maxDistance(), v, t.distanceVariance(), t.attempts(), t.upwardBias(), t.playSound());
				}));
		addDoubleRow("Dist Var", tra.distanceVariance(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(t.maxDistance(), t.minDistanceFactor(), v, t.attempts(), t.upwardBias(), t.playSound());
				}));
		addIntRow("Attempts", tra.attempts(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(t.maxDistance(), t.minDistanceFactor(), t.distanceVariance(), v, t.upwardBias(), t.playSound());
				}));
		addBoolRow("Up Bias", tra.upwardBias(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(t.maxDistance(), t.minDistanceFactor(), t.distanceVariance(), t.attempts(), v, t.playSound());
				}));
		addBoolRow("Sound", tra.playSound(), v ->
				notifySimple(old -> {
					var t = (TeleportRandomAction) old;
					return new TeleportRandomAction(t.maxDistance(), t.minDistanceFactor(), t.distanceVariance(), t.attempts(), t.upwardBias(), v);
				}));
	}

	// --- SpawnShooter rows ---

	private void buildSpawnShooterRows(SpawnShooterAction ssa) {
		addIntRow("Health", ssa.health(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(v, s.damage(), s.lifetime(), s.origin(),
							s.velocityX(), s.velocityY(), s.velocityZ(), s.mover(), s.body());
				}));
		addIntRow("Lifetime", ssa.lifetime(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), v, s.origin(),
							s.velocityX(), s.velocityY(), s.velocityZ(), s.mover(), s.body());
				}));
		addNumberRow("Vel X", ssa.velocityX(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), s.origin(),
							v, s.velocityY(), s.velocityZ(), s.mover(), s.body());
				}));
		addNumberRow("Vel Y", ssa.velocityY(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), s.origin(),
							s.velocityX(), v, s.velocityZ(), s.mover(), s.body());
				}));
		addNumberRow("Vel Z", ssa.velocityZ(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), s.origin(),
							s.velocityX(), s.velocityY(), v, s.mover(), s.body());
				}));
		addFloatRow("Damage", ssa.damage(), v ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), v, s.lifetime(), s.origin(),
							s.velocityX(), s.velocityY(), s.velocityZ(), s.mover(), s.body());
				}));
		addEnumRow("Origin", OriginConfig.OriginMode.values(), ssa.origin().mode(), v -> {
			var s = (SpawnShooterAction) currentAction;
			var newOrigin = new OriginConfig(v, s.origin().offsetX(), s.origin().offsetY(),
					s.origin().offsetZ(), s.origin().rotation());
			notifySimple(old -> new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), newOrigin,
					s.velocityX(), s.velocityY(), s.velocityZ(), s.mover(), s.body()));
		});
		// Origin offsets
		buildOriginOffsetRows(ssa.origin(), newOrigin ->
				notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), newOrigin,
							s.velocityX(), s.velocityY(), s.velocityZ(), s.mover(), s.body());
				}));
		// Mover
		buildMoverRows(ssa.mover(),
				newMover -> notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), s.origin(),
							s.velocityX(), s.velocityY(), s.velocityZ(), newMover, s.body());
				}),
				newMover -> notifySimple(old -> {
					var s = (SpawnShooterAction) old;
					return new SpawnShooterAction(s.health(), s.damage(), s.lifetime(), s.origin(),
							s.velocityX(), s.velocityY(), s.velocityZ(), newMover, s.body());
				}));
	}

	// --- Shared Origin/Mover row builders ---

	private static final String[] MOVER_TYPES = {"none", "acceleration", "deceleration", "rotate", "polar", "composite", "layered", "zero", "bezier"};

	/**
	 * Read the current mover config from currentAction (not from a stale build-time snapshot).
	 */
	private Optional<MoverConfig> getCurrentMover() {
		if (currentAction instanceof FireDanmakuAction fda) return fda.mover();
		if (currentAction instanceof FireLaserAction fla) return fla.mover();
		if (currentAction instanceof SpawnShooterAction ssa) return ssa.mover();
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
	 * @param onTypeChanged  called when mover type is changed — triggers rebuild (new param rows)
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
			} else if (cfg instanceof MoverConfigs.DecelerationConfig dc) {
				addDoubleRow("Factor", dc.factor(), v ->
						onParamChanged.accept(Optional.of(new MoverConfigs.DecelerationConfig(v))));
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
			addDoubleRow("Rad Acc", polar.radialAccel(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
					onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel())));
				}
			});
			addDoubleRow("Ang Acc", polar.angularAccel(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
					onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), v)));
				}
			});
		} else if (cfg instanceof MoverConfigs.CompositeMoverConfig comp) {
			// Display segment count and per-segment editors
			addStringRow("Segments", String.valueOf(comp.segments().size()), v -> {});
			for (int si = 0; si < comp.segments().size(); si++) {
				var seg = comp.segments().get(si);
				final int segIdx = si;
				addIntRow("Seg " + (si + 1) + " Dur", seg.duration(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
						var segs = new java.util.ArrayList<>(c.segments());
						if (segIdx < segs.size()) {
							segs.set(segIdx, new MoverConfigs.CompositeMoverConfig.Segment(v, segs.get(segIdx).mover()));
							onParamChanged.accept(Optional.of(new MoverConfigs.CompositeMoverConfig(segs)));
						}
					}
				});
				// Show sub-mover type as cycle selector
				String subType = getMoverType(Optional.of(seg.mover()));
				addStringCycleRow("  Type", new String[]{"acceleration", "deceleration", "rotate", "polar", "zero"}, subType, newSubType -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
						var segs = new java.util.ArrayList<>(c.segments());
						if (segIdx < segs.size()) {
							var newMover = createDefaultMover(newSubType);
							if (newMover.isPresent()) {
								segs.set(segIdx, new MoverConfigs.CompositeMoverConfig.Segment(segs.get(segIdx).duration(), newMover.get()));
								onTypeChanged.accept(Optional.of(new MoverConfigs.CompositeMoverConfig(segs)));
							}
						}
					}
				});
				// Inline sub-mover parameters
				buildCompositeSegmentParams(seg.mover(), segIdx, onTypeChanged, onParamChanged);
			}
			// Add/Remove segment buttons
			addFullWidthButton("[+] Add Segment", () -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
					var segs = new java.util.ArrayList<>(c.segments());
					segs.add(new MoverConfigs.CompositeMoverConfig.Segment(20, new MoverConfigs.ZeroMoverConfig()));
					onTypeChanged.accept(Optional.of(new MoverConfigs.CompositeMoverConfig(segs)));
				}
			});
			if (comp.segments().size() > 1) {
				addFullWidthButton("[-] Remove Last Segment", () -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
						var segs = new java.util.ArrayList<>(c.segments());
						if (segs.size() > 1) {
							segs.remove(segs.size() - 1);
							onTypeChanged.accept(Optional.of(new MoverConfigs.CompositeMoverConfig(segs)));
						}
					}
				});
			}
		} else if (cfg instanceof MoverConfigs.LayeredMoverConfig layered) {
			// Display layer count and per-layer editors
			addStringRow("Layers", String.valueOf(layered.layers().size()), v -> {});
			for (int li = 0; li < layered.layers().size(); li++) {
				var layerCfg = layered.layers().get(li);
				final int layerIdx = li;
				// Show sub-mover type as cycle selector
				String subType = getMoverType(Optional.of(layerCfg));
				// Exclude "composite" and "layered" from sub-layer types to avoid deep nesting
				String[] layerTypes = {"acceleration", "deceleration", "rotate", "polar", "zero"};
				addStringCycleRow("  L" + (li + 1) + " Type", layerTypes, subType, newSubType -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.LayeredMoverConfig lm) {
						var layers = new java.util.ArrayList<>(lm.layers());
						if (layerIdx < layers.size()) {
							var newMover = createDefaultMover(newSubType);
							if (newMover.isPresent()) {
								layers.set(layerIdx, newMover.get());
								onTypeChanged.accept(Optional.of(new MoverConfigs.LayeredMoverConfig(layers)));
							}
						}
					}
				});
				// Inline sub-mover parameters
				buildLayeredLayerParams(layerCfg, layerIdx, onTypeChanged, onParamChanged);
			}
			// Add/Remove layer buttons
			addFullWidthButton("[+] Add Layer", () -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.LayeredMoverConfig lm) {
					var layers = new java.util.ArrayList<>(lm.layers());
					layers.add(new MoverConfigs.ZeroMoverConfig());
					onTypeChanged.accept(Optional.of(new MoverConfigs.LayeredMoverConfig(layers)));
				}
			});
			if (layered.layers().size() > 1) {
				addFullWidthButton("[-] Remove Last Layer", () -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.LayeredMoverConfig lm) {
						var layers = new java.util.ArrayList<>(lm.layers());
						if (layers.size() > 1) {
							layers.remove(layers.size() - 1);
							onTypeChanged.accept(Optional.of(new MoverConfigs.LayeredMoverConfig(layers)));
						}
					}
				});
			}
		} else if (cfg instanceof MoverConfigs.BezierMoverConfig bez) {
				addDoubleRow("CP1 Fwd", bez.cp1Forward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								v, b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("CP1 Right", bez.cp1Right(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), v, b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("CP1 Up", bez.cp1Up(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), v, b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("CP2 Fwd", bez.cp2Forward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), v, b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("CP2 Right", bez.cp2Right(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), v, b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("CP2 Up", bez.cp2Up(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), v,
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("End Fwd", bez.endForward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								v, b.endRight(), b.endUp(), b.duration())));
					}
				});
				addDoubleRow("End Right", bez.endRight(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), v, b.endUp(), b.duration())));
					}
				});
				addDoubleRow("End Up", bez.endUp(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), v, b.duration())));
					}
				});
				addIntRow("Duration", bez.duration(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), v)));
					}
				});
			}
		}
	}

	private static String getMoverType(Optional<MoverConfig> mover) {
		if (mover.isEmpty()) return "none";
		MoverConfig cfg = mover.get();
		if (cfg instanceof MoverConfigs.AccelerationConfig) return "acceleration";
		if (cfg instanceof MoverConfigs.DecelerationConfig) return "deceleration";
		if (cfg instanceof MoverConfigs.RotateConfig) return "rotate";
		if (cfg instanceof MoverConfigs.PolarMoverConfig) return "polar";
		if (cfg instanceof MoverConfigs.CompositeMoverConfig) return "composite";
		if (cfg instanceof MoverConfigs.LayeredMoverConfig) return "layered";
		if (cfg instanceof MoverConfigs.ZeroMoverConfig) return "zero";
		if (cfg instanceof MoverConfigs.BezierMoverConfig) return "bezier";
		return "none";
	}

	private static Optional<MoverConfig> createDefaultMover(String type) {
		return switch (type) {
			case "acceleration" -> Optional.of(new MoverConfigs.AccelerationConfig(new net.minecraft.world.phys.Vec3(0, -0.05, 0)));
			case "deceleration" -> Optional.of(new MoverConfigs.DecelerationConfig(0.06));
			case "rotate" -> Optional.of(new MoverConfigs.RotateConfig(5.0));
			case "polar" -> Optional.of(new MoverConfigs.PolarMoverConfig(5.0, 0, 0, 0, 10.0, 0));
			case "composite" -> Optional.of(new MoverConfigs.CompositeMoverConfig(List.of(
					new MoverConfigs.CompositeMoverConfig.Segment(20, new MoverConfigs.AccelerationConfig(new net.minecraft.world.phys.Vec3(0, 0, 0))),
					new MoverConfigs.CompositeMoverConfig.Segment(20, new MoverConfigs.ZeroMoverConfig())
			)));
			case "layered" -> Optional.of(new MoverConfigs.LayeredMoverConfig(List.of(
					new MoverConfigs.AccelerationConfig(new net.minecraft.world.phys.Vec3(0, 0, 0)),
					new MoverConfigs.PolarMoverConfig(0, 0.3, -0.01, 0, 8.0, 0)
			)));
			case "zero" -> Optional.of(new MoverConfigs.ZeroMoverConfig());
			case "bezier" -> Optional.of(new MoverConfigs.BezierMoverConfig(5, 3, 0, 10, -3, 0, 15, 0, 0, 40));
			default -> Optional.empty();
		};
	}

	/**
	 * Render inline parameter rows for a sub-mover within a CompositeMover segment.
	 */
	private void buildCompositeSegmentParams(MoverConfig subCfg, int segIdx,
											Consumer<Optional<MoverConfig>> onTypeChanged,
											Consumer<Optional<MoverConfig>> onParamChanged) {
		if (subCfg instanceof MoverConfigs.AccelerationConfig acc) {
			addDoubleRow("  Acc X", acc.acceleration().x, v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(v, a.acceleration().y, a.acceleration().z)), onParamChanged);
				}
			});
			addDoubleRow("  Acc Y", acc.acceleration().y, v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(a.acceleration().x, v, a.acceleration().z)), onParamChanged);
				}
			});
			addDoubleRow("  Acc Z", acc.acceleration().z, v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(a.acceleration().x, a.acceleration().y, v)), onParamChanged);
				}
			});
		} else if (subCfg instanceof MoverConfigs.DecelerationConfig dc) {
			addDoubleRow("  Factor", dc.factor(), v -> updateCompositeSegment(segIdx,
					new MoverConfigs.DecelerationConfig(v), onParamChanged));
		} else if (subCfg instanceof MoverConfigs.RotateConfig rot) {
			addDoubleRow("  Deg/t", rot.degreesPerTick(), v -> updateCompositeSegment(segIdx,
					new MoverConfigs.RotateConfig(v), onParamChanged));
		} else if (subCfg instanceof MoverConfigs.PolarMoverConfig polar) {
			addDoubleRow("  Radius", polar.radius(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Rad Spd", polar.radialSpeed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Ang Spd", polar.angularSpeed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Init Ang", polar.initialAngle(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Rad Acc", polar.radialAccel(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Ang Acc", polar.angularAccel(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), v), onParamChanged);
				}
			});
		}
		// ZeroMoverConfig has no params
	}

	/**
	 * Read the current sub-mover for a specific segment from the live currentAction state.
	 * Returns null if the segment doesn't exist or the mover is not composite.
	 */
	private MoverConfig getCompositeSegmentMover(int segIdx) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
			if (segIdx < c.segments().size()) {
				return c.segments().get(segIdx).mover();
			}
		}
		return null;
	}

	private void updateCompositeSegment(int segIdx, MoverConfig newSubMover,
										Consumer<Optional<MoverConfig>> onParamChanged) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.CompositeMoverConfig c) {
			var segs = new java.util.ArrayList<>(c.segments());
			if (segIdx < segs.size()) {
				segs.set(segIdx, new MoverConfigs.CompositeMoverConfig.Segment(segs.get(segIdx).duration(), newSubMover));
				onParamChanged.accept(Optional.of(new MoverConfigs.CompositeMoverConfig(segs)));
			}
		}
	}

	// --- Layered mover helpers ---

	/**
	 * Render inline parameter rows for a layer within a LayeredMover.
	 * Uses the same pattern as buildCompositeSegmentParams but reads from layered layers.
	 */
	private void buildLayeredLayerParams(MoverConfig layerCfg, int layerIdx,
										 Consumer<Optional<MoverConfig>> onTypeChanged,
										 Consumer<Optional<MoverConfig>> onParamChanged) {
		if (layerCfg instanceof MoverConfigs.AccelerationConfig acc) {
			addDoubleRow("  Acc X", acc.acceleration().x, v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(v, a.acceleration().y, a.acceleration().z)), onParamChanged);
				}
			});
			addDoubleRow("  Acc Y", acc.acceleration().y, v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(a.acceleration().x, v, a.acceleration().z)), onParamChanged);
				}
			});
			addDoubleRow("  Acc Z", acc.acceleration().z, v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(
							new net.minecraft.world.phys.Vec3(a.acceleration().x, a.acceleration().y, v)), onParamChanged);
				}
			});
		} else if (layerCfg instanceof MoverConfigs.DecelerationConfig dc) {
			addDoubleRow("  Factor", dc.factor(), v -> updateLayeredLayer(layerIdx,
					new MoverConfigs.DecelerationConfig(v), onParamChanged));
		} else if (layerCfg instanceof MoverConfigs.RotateConfig rot) {
			addDoubleRow("  Deg/t", rot.degreesPerTick(), v -> updateLayeredLayer(layerIdx,
					new MoverConfigs.RotateConfig(v), onParamChanged));
		} else if (layerCfg instanceof MoverConfigs.PolarMoverConfig polar) {
			addDoubleRow("  Radius", polar.radius(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Rad Spd", polar.radialSpeed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Rad Acc", polar.radialAccel(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Init Ang", polar.initialAngle(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Ang Spd", polar.angularSpeed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel()), onParamChanged);
				}
			});
			addDoubleRow("  Ang Acc", polar.angularAccel(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), v), onParamChanged);
				}
			});
		}
		// ZeroMoverConfig has no params
	}

	/**
	 * Read the current layer mover from the live currentAction state.
	 */
	private MoverConfig getLayeredLayerMover(int layerIdx) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.LayeredMoverConfig lm) {
			if (layerIdx < lm.layers().size()) {
				return lm.layers().get(layerIdx);
			}
		}
		return null;
	}

	private void updateLayeredLayer(int layerIdx, MoverConfig newLayerMover,
									Consumer<Optional<MoverConfig>> onParamChanged) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.LayeredMoverConfig lm) {
			var layers = new java.util.ArrayList<>(lm.layers());
			if (layerIdx < layers.size()) {
				layers.set(layerIdx, newLayerMover);
				onParamChanged.accept(Optional.of(new MoverConfigs.LayeredMoverConfig(layers)));
			}
		}
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

	private void notifyTextDanmaku(Function<FireTextDanmakuAction, SpellAction> modifier) {
		notifyTextDanmaku(modifier, true);
	}

	private void notifyTextDanmaku(Function<FireTextDanmakuAction, SpellAction> modifier, boolean rebuild) {
		if (currentAction instanceof FireTextDanmakuAction ftda) {
			var newAction = modifier.apply(ftda);
			currentAction = newAction;
			onActionChanged.accept(newAction);
			if (rebuild) {
				int idx = actionIndex;
				clearWidgets();
				if (newAction instanceof FireTextDanmakuAction nftda) {
					buildFireTextDanmakuRows(nftda);
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
		notifySimple(modifier, false);
	}

	private void notifySimple(Function<SpellAction, SpellAction> modifier, boolean rebuild) {
		if (currentAction == null) return;
		SpellAction newAction;
		if (currentAction instanceof SpellActions.DisabledAction da) {
			// Unwrap, modify inner, re-wrap
			var modified = modifier.apply(da.inner());
			newAction = new SpellActions.DisabledAction(modified);
		} else {
			newAction = modifier.apply(currentAction);
		}
		currentAction = newAction;
		onActionChanged.accept(newAction);
		if (rebuild) {
			int idx = actionIndex;
			clearWidgets();
			buildActionRows(newAction);
			actionIndex = idx;
			layoutWidgets();
		}
	}

	// --- Row builders ---

	private <E extends Enum<E>> void addEnumRow(String label, E[] values, E current, Consumer<E> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		String[] displayNames = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			displayNames[i] = formatEnum(values[i]);
		}
		int selectedIndex = current.ordinal();
		int rowIndex = rows.size();
		var btn = Button.builder(Component.literal(displayNames[selectedIndex] + " \u25BC"), b -> {
			openDropdown(displayNames, selectedIndex, idx -> onChange.accept(values[idx]), rowIndex);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void addStringCycleRow(String label, String[] values, String current, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		int selectedIdx = -1;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(current)) {
				selectedIdx = i;
				break;
			}
		}
		final int selectedIndex = selectedIdx;
		int rowIndex = rows.size();
		var btn = Button.builder(Component.literal(current + " \u25BC"), b -> {
			openDropdown(values, selectedIndex, idx -> onChange.accept(values[idx]), rowIndex);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	// Expression autocomplete keywords
	private static final String[] EXPR_FUNCTIONS = {
			"rand", "random", "lerp", "lerp_time", "hp", "health", "by_health",
			"tick_mod", "sin", "cos", "sqrt", "max", "min", "clamp", "gaussian", "choose",
			"tick", "phase_tick", "total_tick", "distance",
			"target_height", "target_fly_time", "target_speed", "game_difficulty",
			"caster_x", "caster_y", "caster_z", "target_x", "target_y", "target_z"
	};

	/** Returns the insert template for a function (with parens and commas). */
	private static String getFuncInsertText(String name) {
		if (name.equals("rand") || name.equals("random")) return "rand(, )";
		if (name.equals("lerp") || name.equals("lerp_time")) return "lerp(, , )";
		if (name.equals("hp") || name.equals("health") || name.equals("by_health")) return "hp(, )";
		if (name.equals("tick_mod")) return "tick_mod()";
		if (name.equals("sin")) return "sin()";
		if (name.equals("cos")) return "cos()";
		if (name.equals("sqrt")) return "sqrt()";
		if (name.equals("max")) return "max(, )";
		if (name.equals("min")) return "min(, )";
		if (name.equals("clamp")) return "clamp(, , )";
		if (name.equals("gaussian")) return "gaussian(, )";
		if (name.equals("choose")) return "choose(, )";
		return name; // bare keyword (tick, phase_tick, total_tick, distance, caster_x, etc.)
	}

	/** Returns the cursor position within the template string (right after first '('). */
	private static int getFuncCursorInTemplate(String name) {
		if (name.equals("rand") || name.equals("random")) return 5;
		if (name.equals("lerp") || name.equals("lerp_time")) return 5;
		if (name.equals("hp") || name.equals("health") || name.equals("by_health")) return 3;
		if (name.equals("tick_mod")) return 9;
		if (name.equals("sin")) return 4;
		if (name.equals("cos")) return 4;
		if (name.equals("sqrt")) return 5;
		return name.length(); // bare keyword
	}

	/** Returns the display name with signature for the completion list. */
	private static String getFuncDisplayName(String name) {
		if (name.equals("rand") || name.equals("random")) return "rand(min, max)";
		if (name.equals("lerp") || name.equals("lerp_time")) return "lerp(start, end, dur)";
		if (name.equals("hp") || name.equals("health") || name.equals("by_health")) return "hp(full, empty)";
		if (name.equals("tick_mod")) return "tick_mod(period)";
		if (name.equals("sin")) return "sin(input, amp?, phase?)";
		if (name.equals("cos")) return "cos(input, amp?, phase?)";
		if (name.equals("sqrt")) return "sqrt(input)";
		return name;
	}

	// Rainbow bracket colors (cycle through these for nesting depth)
	private static final int[] BRACKET_COLORS = {
			0xFFDD44, // depth 0: yellow (also used for function parens)
			0xFF6666, // depth 1: red
			0x66FF66, // depth 2: green
			0x6688FF, // depth 3: blue
			0xFF66FF, // depth 4: magenta
			0xFFAA44, // depth 5: orange
	};
	private static final int COLOR_VARIABLE = 0x55FFFF;  // aqua
	private static final int COLOR_FUNCTION = 0xFFDD44;  // yellow
	private static final int COLOR_KEYWORD = 0xFFDD44;   // yellow (tick, distance, etc.)
	private static final java.util.Set<String> KNOWN_FUNCTIONS = java.util.Set.of(
			"rand", "random", "lerp", "lerp_time", "hp", "health", "by_health",
			"tick_mod", "sin", "cos", "sqrt", "max", "min", "clamp", "gaussian", "choose"
	);
	private static final java.util.Set<String> KNOWN_KEYWORDS = java.util.Set.of(
			"tick", "phase_tick", "total_tick", "distance", "target_height", "target_fly_time",
			"target_speed", "game_difficulty",
			"caster_x", "caster_y", "caster_z", "target_x", "target_y", "target_z"
	);

	/**
	 * Compute per-character color array for expression syntax highlighting.
	 * Returns 0 for default color, or an RGB int for colored characters.
	 */
	private static int[] computeExprColors(String text, boolean valid) {
		int[] colors = new int[text.length()];
		if (text.isEmpty()) return colors;
		// Variables: $name → aqua
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '$') {
				colors[i] = COLOR_VARIABLE;
				int j = i + 1;
				while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
					colors[j] = COLOR_VARIABLE;
					j++;
				}
				i = j - 1;
			}
		}
		// Functions and keywords: name followed by ( → yellow
		for (int i = 0; i < text.length(); i++) {
			if (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_') {
				int j = i;
				while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) j++;
				String word = text.substring(i, j);
				// Check if followed by ( → function
				int afterWord = j;
				while (afterWord < text.length() && text.charAt(afterWord) == ' ') afterWord++;
				if (afterWord < text.length() && text.charAt(afterWord) == '(' && KNOWN_FUNCTIONS.contains(word)) {
					for (int k = i; k < j; k++) colors[k] = COLOR_FUNCTION;
				} else if (KNOWN_KEYWORDS.contains(word)) {
					for (int k = i; k < j; k++) colors[k] = COLOR_KEYWORD;
				}
				i = j - 1;
			}
		}
		// Rainbow brackets (only if expression is valid)
		if (valid) {
			int depth = 0;
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if (c == '(') {
					// If this ( is preceded by a function name, keep function color (yellow)
					if (colors[i] == 0) {
						colors[i] = BRACKET_COLORS[depth % BRACKET_COLORS.length];
					}
					depth++;
				} else if (c == ')') {
					depth = Math.max(0, depth - 1);
					if (colors[i] == 0) {
						colors[i] = BRACKET_COLORS[depth % BRACKET_COLORS.length];
					}
				}
			}
		}
		return colors;
	}

	private final List<EditBox> exprEditBoxes = new ArrayList<>();

	// Expression completion overlay
	private String[] exprCompletionItems = null;
	private int exprCompletionHoverIndex = -1;
	private EditBox exprCompletionTarget = null;
	private int exprCompletionInsertStart = -1;

	private void addNumberRow(String label, NumberProvider provider, Consumer<NumberProvider> onChange) {
		double value = provider instanceof NumberProviders.Constant c ? c.value() : 0;
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setMaxLength(256);
		String unparsed = NumberExprParser.unparse(provider);
		editBox.setValue(unparsed != null ? unparsed : formatNumber(value));
		editBox.setResponder(text -> {
			NumberProvider parsed = NumberExprParser.parse(text);
			if (parsed != null) {
				onChange.accept(parsed);
			}
		});
		// Syntax-aware formatter: variables=aqua, functions=yellow, rainbow brackets
		editBox.setFormatter((text, displayPos) -> {
			String fullValue = editBox.getValue();
			boolean valid = !fullValue.trim().isEmpty() && NumberExprParser.parse(fullValue.trim()) != null;
			// Pre-compute per-character color for the full text
			int[] colors = computeExprColors(fullValue, valid);
			// Build FormattedCharSequence for the visible slice
			var defaultStyle = net.minecraft.network.chat.Style.EMPTY;
			var parts = new java.util.ArrayList<FormattedCharSequence>();
			int end = Math.min(displayPos + text.length(), fullValue.length());
			int runStart = displayPos;
			for (int ci = displayPos; ci <= end; ci++) {
				if (ci == end || (ci > runStart && colors[ci] != colors[ci - 1])) {
					int localStart = runStart - displayPos;
					int localEnd = ci - displayPos;
					if (localEnd > localStart && localEnd <= text.length()) {
						int c = colors[runStart];
						var style = c != 0 ? defaultStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(c)) : defaultStyle;
						parts.add(FormattedCharSequence.forward(text.substring(localStart, localEnd), style));
					}
					runStart = ci;
				}
			}
			if (parts.isEmpty()) {
				parts.add(FormattedCharSequence.forward(text, defaultStyle));
			}
			return FormattedCharSequence.composite(parts);
		});
		String displayLabel = label;
		if (!(provider instanceof NumberProviders.Constant)) {
			displayLabel = label + "*";
		}
		exprEditBoxes.add(editBox);
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

	private void addColorRow(String label, int value, Consumer<Integer> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setMaxLength(16);
		editBox.setValue(String.format("0x%08X", value));
		editBox.setResponder(text -> {
			Integer parsed = parseColor(text);
			if (parsed != null) {
				onChange.accept(parsed);
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
		editBox.setMaxLength(256);
		editBox.setValue(value);
		editBox.setResponder(onChange::accept);
		rows.add(new EditorRow(label, editBox, false));
	}

	private Integer parseColor(String text) {
		String value = text.trim();
		if (value.isEmpty()) return null;
		try {
			if (value.startsWith("#")) {
				long raw = Long.parseLong(value.substring(1), 16);
				if (value.length() == 7) {
					raw |= 0xFF000000L;
				}
				return (int) raw;
			}
			return (int) (long) Long.decode(value);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private void addFullWidthButton(String text, Runnable onClick) {
		int widgetW = w - PADDING * 2;
		var btn = Button.builder(Component.literal(text), b -> onClick.run())
				.bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow("", btn, true));
	}

	private void addInlineRow(String text, Runnable onDelete) {
		int deleteW = 20;
		var btn = Button.builder(Component.literal("[x]"), b -> onDelete.run())
				.bounds(0, 0, deleteW, ROW_HEIGHT - 2).build();
		// customWidgetW > 0 means the widget should be right-aligned with this exact width
		// The label text fills the remaining space on the left
		rows.add(new EditorRow(text, btn, false, deleteW));
	}

	// --- Dropdown overlay ---

	private record DropdownOverlay(
			String[] options,
			int selectedIndex,
			Consumer<Integer> onSelect,
			int triggerRowIndex
	) {}

	private void openDropdown(String[] options, int selected, Consumer<Integer> onSelect, int triggerRowIndex) {
		dropdown = new DropdownOverlay(options, selected, onSelect, triggerRowIndex);
		dropdownHoverIndex = -1;
		// Auto-scroll to make selected item visible
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int maxScroll = Math.max(0, options.length - visibleItems);
		if (selected >= visibleItems) {
			dropdownScrollOffset = selected - visibleItems + 1;
		} else if (selected < 0) {
			dropdownScrollOffset = 0;
		} else {
			dropdownScrollOffset = 0;
		}
		dropdownScrollOffset = Math.max(0, Math.min(maxScroll, dropdownScrollOffset));
		for (var row : rows) row.widget().active = false;
	}

	private void closeDropdown() {
		dropdown = null;
		dropdownHoverIndex = -1;
		for (var row : rows) row.widget().active = true;
	}

	private int[] computeDropdownBounds() {
		if (dropdown == null) return new int[]{0, 0, 0, 0, 0};
		String[] options = dropdown.options();
		if (options == null || options.length == 0) return new int[]{0, 0, 0, 0, 0};
		int visibleItems = Math.min(options.length, DROPDOWN_MAX_VISIBLE);
		int totalH = visibleItems * DROPDOWN_ITEM_H;

		int triggerRowY = y + PADDING + (dropdown.triggerRowIndex() + 1) * ROW_HEIGHT - scrollOffset;
		int dropdownX = x + LABEL_WIDTH + PADDING * 2;
		int dropdownW = w - LABEL_WIDTH - PADDING * 3;
		if (dropdownW < 20) dropdownW = 20;

		int dropdownY = triggerRowY + ROW_HEIGHT;
		if (dropdownY + totalH > y + h) {
			dropdownY = triggerRowY - totalH;
		}
		if (dropdownY < y) {
			dropdownY = y;
		}
		if (dropdownY + totalH > y + h) {
			totalH = y + h - dropdownY;
		}
		if (totalH < DROPDOWN_ITEM_H) totalH = DROPDOWN_ITEM_H;
		return new int[]{dropdownX, dropdownY, dropdownW, totalH, visibleItems};
	}

	private void doRenderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (dropdown == null) return;
		Font font = Minecraft.getInstance().font;
		int[] bounds = computeDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		int visibleItems = bounds[4];
		String[] options = dropdown.options();
		if (options == null || options.length == 0) return;

		boolean needsScroll = options.length > visibleItems;
		int scrollbarW = needsScroll ? 6 : 0;

		// Render without scissor - background is fully opaque and will cover any text beneath
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200); // raise z to render on top of all widgets

		// Shadow
		guiGraphics.fill(dx + 3, dy + 3, dx + dw + 3, dy + dh + 3, 0x88000000);
		// Background (fully opaque to cover any text beneath)
		guiGraphics.fill(dx, dy, dx + dw, dy + dh, 0xFF1a1a30);
		// Border
		guiGraphics.fill(dx, dy, dx + dw, dy + 1, 0xFF666688);
		guiGraphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF666688);
		guiGraphics.fill(dx, dy, dx + 1, dy + dh, 0xFF666688);
		guiGraphics.fill(dx + dw - 1, dy, dx + dw, dy + dh, 0xFF666688);

		// Compute hover (in visible area, mapped to scrolled index)
		dropdownHoverIndex = -1;
		int contentW = dw - scrollbarW;
		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int rawIdx = (mouseY - dy) / DROPDOWN_ITEM_H + dropdownScrollOffset;
			if (rawIdx >= 0 && rawIdx < options.length) {
				dropdownHoverIndex = rawIdx;
			}
		}

		// Render visible items (manually clip to dropdown bounds)
		int visCount = Math.min(options.length, dh / DROPDOWN_ITEM_H);
		for (int i = 0; i < visCount; i++) {
			int optIdx = i + dropdownScrollOffset;
			if (optIdx >= options.length) break;
			int itemY = dy + i * DROPDOWN_ITEM_H;
			if (itemY + DROPDOWN_ITEM_H > dy + dh) break; // clip bottom
			boolean isHovered = optIdx == dropdownHoverIndex;
			boolean isSelected = optIdx == dropdown.selectedIndex();

			if (isHovered) {
				guiGraphics.fill(dx + 1, itemY, dx + contentW - 1, itemY + DROPDOWN_ITEM_H, 0x44FFFFFF);
			}

			// Selection marker
			int textX = dx + 4;
			if (isSelected) {
				guiGraphics.drawString(font, "\u25B6", dx + 3, itemY + 4, 0xFFFFCC44, false);
				textX = dx + 14;
			}

			int textColor = isHovered ? 0xFFFFDD66 : (isSelected ? 0xFFFFCC88 : 0xFFDDDDDD);
			guiGraphics.drawString(font, options[optIdx], textX, itemY + 4, textColor, false);
		}

		// Scrollbar track + thumb
		if (needsScroll) {
			int sbX = dx + dw - scrollbarW;
			// Track background
			guiGraphics.fill(sbX, dy, sbX + scrollbarW, dy + dh, 0x33FFFFFF);
			int trackH = dh - 2;
			// Actual visible items based on rendered dropdown height
			int actualVisible = Math.max(1, dh / DROPDOWN_ITEM_H);
			int thumbH = Math.max(10, trackH * actualVisible / options.length);
			int maxScroll = Math.max(1, options.length - actualVisible);
			int thumbTravel = trackH - thumbH;
			if (thumbTravel > 0) {
				int thumbY = dy + 1 + thumbTravel * dropdownScrollOffset / maxScroll;
				guiGraphics.fill(sbX + 1, thumbY, sbX + scrollbarW - 1, thumbY + thumbH, 0xAAAAAACC);
			}
		}

		guiGraphics.pose().popPose();
	}

	private boolean handleDropdownClick(double mouseX, double mouseY) {
		if (dropdown == null) return false;
		int[] bounds = computeDropdownBounds();
		int dx = bounds[0], dy = bounds[1], dw = bounds[2], dh = bounds[3];
		String[] options = dropdown.options();
		boolean needsScroll = options.length > DROPDOWN_MAX_VISIBLE;
		int scrollbarW = needsScroll ? 6 : 0;
		int contentW = dw - scrollbarW;

		if (mouseX >= dx && mouseX < dx + contentW && mouseY >= dy && mouseY < dy + dh) {
			int visIdx = (int)((mouseY - dy) / DROPDOWN_ITEM_H);
			int optIdx = visIdx + dropdownScrollOffset;
			if (optIdx >= 0 && optIdx < options.length) {
				dropdown.onSelect().accept(optIdx);
				return true;
			}
		}
		return false;
	}

	// --- Layout ---

	private void layoutWidgets() {
		// Clamp scrollOffset after rebuild: content may have shrunk
		int maxScroll = getContentMaxScroll();
		if (scrollOffset > maxScroll) {
			scrollOffset = maxScroll;
		}
		for (int i = 0; i < rows.size(); i++) {
			int rowY = y + PADDING + (i + 1) * ROW_HEIGHT - scrollOffset;
			var row = rows.get(i);
			if (row.fullWidth()) {
				row.widget().setX(x + PADDING);
				row.widget().setWidth(w - PADDING * 2);
			} else {
				int widgetW = row.customWidgetW() > 0 ? row.customWidgetW() : (w - LABEL_WIDTH - PADDING * 3);
				int widgetX = x + w - PADDING - widgetW;
				if (row.customWidgetW() <= 0) {
					widgetX = x + LABEL_WIDTH + PADDING * 2;
				}
				row.widget().setX(widgetX);
				row.widget().setWidth(widgetW);
			}
			row.widget().setY(rowY);
			if (!widgetsRegistered) {
				addWidget.accept(row.widget());
			}
		}
		widgetsRegistered = true;
	}

	// --- Rendering ---

	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		render(guiGraphics, mouseX, mouseY, partialTick, true);
	}

	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, boolean renderDropdown) {
		Font font = Minecraft.getInstance().font;

		// Panel background
		guiGraphics.fill(x, y, x + w, y + h, 0xCC1a1a2e);
		guiGraphics.fill(x, y, x + 1, y + h, 0xFF444466);

		if (typeSelectorMode) {
			guiGraphics.drawString(font, "Add Action", x + PADDING, y + PADDING + 2, 0xFFFFCC44, false);
			for (int i = 0; i < rows.size(); i++) {
				int rowY = y + PADDING + (i + 1) * ROW_HEIGHT - scrollOffset;
				rows.get(i).widget().visible = rowY >= y && rowY + ROW_HEIGHT <= y + h;
			}
			renderScrollbar(guiGraphics);
			if (renderDropdown && dropdown != null) {
				this.renderDropdown(guiGraphics, mouseX, mouseY);
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

		// Disable/Enable + Delete buttons (top right)
		boolean isDisabled = currentAction instanceof SpellActions.DisabledAction;
		String toggleText = isDisabled ? "[Enable]" : "[Disable]";
		String deleteText = "[Delete]";
		int deleteX = x + w - font.width(deleteText) - PADDING;
		int toggleX = deleteX - font.width(toggleText) - 6;

		boolean toggleHovered = mouseX >= toggleX && mouseX < toggleX + font.width(toggleText)
				&& mouseY >= y + PADDING && mouseY < y + PADDING + 12;
		guiGraphics.drawString(font, toggleText, toggleX, y + PADDING + 2,
				toggleHovered ? 0xFFFFCC44 : (isDisabled ? 0xFF44AA44 : 0xFFAAAA44), false);

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
			row.widget().visible = visible;
			if (visible && !row.fullWidth() && !row.label().isEmpty()) {
				guiGraphics.drawString(font, row.label(), x + PADDING, rowY + 4, 0xFFBBBBBB, false);
			}
		}

		// Scrollbar for content area
		renderScrollbar(guiGraphics);

		// Dropdown overlay (rendered last, on top of everything)
		if (renderDropdown && dropdown != null) {
			doRenderDropdown(guiGraphics, mouseX, mouseY);
		}
	}

	private void renderScrollbar(GuiGraphics g) {
		int maxScroll = getContentMaxScroll();
		if (maxScroll <= 0) return;
		int sbW = 4;
		int sbX = x + w - sbW;
		// Track
		g.fill(sbX, y, sbX + sbW, y + h, 0x33FFFFFF);
		// Thumb
		int trackH = h - 2;
		int contentH = maxScroll + h;
		int thumbH = Math.max(10, trackH * h / contentH);
		int thumbY = y + 1 + (int) ((long) (trackH - thumbH) * scrollOffset / maxScroll);
		g.fill(sbX + 1, thumbY, sbX + sbW - 1, thumbY + thumbH, 0x88AAAACC);
	}

	/**
	 * Render only the dropdown overlay. Called from SpellPreviewScreen after super.render()
	 * to ensure the dropdown draws on top of all widgets.
	 */
	/** Optional callback for variable jump (Ctrl+Click on $var). */
	private java.util.function.Consumer<String> onVariableJump;
	/** Optional callback for toggle disable. */
	private Runnable onToggleDisable;

	public void setVariableJumpCallback(java.util.function.Consumer<String> callback) {
		this.onVariableJump = callback;
	}

	public void setToggleDisableCallback(Runnable callback) {
		this.onToggleDisable = callback;
	}

	public void renderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		// Red underline for invalid expressions, blue underline for $variables
		for (var eb : exprEditBoxes) {
			String text = eb.getValue().trim();
			if (!text.isEmpty() && NumberExprParser.parse(text) == null) {
				int ex = eb.getX();
				int ey = eb.getY() + eb.getHeight();
				int ew = eb.getWidth();
				guiGraphics.pose().pushPose();
				guiGraphics.pose().translate(0, 0, 200);
				guiGraphics.fill(ex, ey, ex + ew, ey + 2, 0xFFFF4444);
				guiGraphics.pose().popPose();
			}
			// Variable highlighting is now handled by EditBox.setFormatter() — no overlay needed
		}

		if (dropdown != null) {
			doRenderDropdown(guiGraphics, mouseX, mouseY);
		}
		doRenderExprCompletion(guiGraphics, mouseX, mouseY);
	}

	// --- Mouse handling ---

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Ctrl+Click on expression EditBox → find $variable under cursor and jump to definition
		if (button == 0 && net.minecraft.client.gui.screens.Screen.hasControlDown() && onVariableJump != null) {
			for (var eb : exprEditBoxes) {
				if (mouseX >= eb.getX() && mouseX < eb.getX() + eb.getWidth()
						&& mouseY >= eb.getY() && mouseY < eb.getY() + eb.getHeight()) {
					// Find cursor position in text, then check if it's inside a $var token
					String display = eb.getValue();
					// Approximate char index from click X
					Font font = Minecraft.getInstance().font;
					int relX = (int) mouseX - eb.getX() - 4;
					int charIdx = 0;
					for (int ci = 0; ci < display.length(); ci++) {
						if (font.width(display.substring(0, ci + 1)) > relX) break;
						charIdx = ci + 1;
					}
					// Search backwards for $ sign
					int dollar = -1;
					for (int s = charIdx; s >= 0; s--) {
						if (s < display.length() && display.charAt(s) == '$') { dollar = s; break; }
					}
					if (dollar >= 0) {
						int nameStart = dollar + 1;
						int nameEnd = nameStart;
						while (nameEnd < display.length() && (Character.isLetterOrDigit(display.charAt(nameEnd)) || display.charAt(nameEnd) == '_')) nameEnd++;
						if (nameEnd > nameStart && charIdx >= dollar && charIdx <= nameEnd) {
							String varName = display.substring(nameStart, nameEnd);
							onVariableJump.accept(varName);
							return true;
						}
					}
				}
			}
		}
		// Handle expression completion overlay
		if (exprCompletionItems != null) {
			if (button == 0) {
				// Compute hover index from click position
				int cx = exprCompletionTarget.getX();
				int cy = exprCompletionTarget.getY() + exprCompletionTarget.getHeight();
				int cw = Math.max(exprCompletionTarget.getWidth(), 120);
				int itemH = DROPDOWN_ITEM_H;
				int itemCount = exprCompletionItems.length;
				int totalH = Math.min(itemCount * itemH, DROPDOWN_MAX_VISIBLE * itemH);
				if (cy + totalH > y + h) totalH = y + h - cy;

				if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + totalH) {
					int idx = (int) ((mouseY - cy) / itemH);
					if (idx >= 0 && idx < itemCount) {
						exprCompletionHoverIndex = idx;
						applyExprCompletion();
						return true;
					}
				}
				closeExprCompletion();
				return true;
			}
			return true;
		}

		// Handle dropdown overlay first
		if (dropdown != null) {
			if (button == 0) {
				if (handleDropdownClick(mouseX, mouseY)) {
					closeDropdown();
					return true;
				}
				closeDropdown();
				return true;
			}
			return true; // block all clicks while dropdown is open
		}

		if (button != 0 || currentAction == null) return false;

		// Scrollbar click detection
		int maxScroll = getContentMaxScroll();
		if (maxScroll > 0 && isMouseOver(mouseX, mouseY)) {
			int sbW = 4;
			int sbX = x + w - sbW;
			if (mouseX >= sbX && mouseX < sbX + sbW) {
				scrollbarDragging = true;
				updateScrollbarDrag(mouseY);
				return true;
			}
		}

		Font font = Minecraft.getInstance().font;

		// Handle [Disable]/[Enable] button
		boolean isDisabled = currentAction instanceof SpellActions.DisabledAction;
		String toggleText = isDisabled ? "[Enable]" : "[Disable]";
		String deleteText = "[Delete]";
		int deleteX = x + w - font.width(deleteText) - PADDING;
		int toggleX = deleteX - font.width(toggleText) - 6;
		if (mouseX >= toggleX && mouseX < toggleX + font.width(toggleText)
				&& mouseY >= y + PADDING && mouseY < y + PADDING + 12) {
			if (onToggleDisable != null) onToggleDisable.run();
			return true;
		}

		// Handle [Delete] button
		if (mouseX >= deleteX && mouseX < x + w
				&& mouseY >= y + PADDING && mouseY < y + PADDING + 12) {
			onDeleteAction.run();
			return true;
		}
		return false;
	}

	private int getContentMaxScroll() {
		return Math.max(0, (rows.size() + 1) * ROW_HEIGHT + PADDING - h);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (dropdown != null) {
			String[] options = dropdown.options();
			if (options == null) return true;
			int[] bounds = computeDropdownBounds();
			int actualVisible = Math.max(1, bounds[3] / DROPDOWN_ITEM_H);
			int maxScroll = Math.max(0, options.length - actualVisible);
			dropdownScrollOffset = Math.max(0, Math.min(maxScroll,
					dropdownScrollOffset - (int) (delta * 3)));
			return true;
		}
		if (isMouseOver(mouseX, mouseY)) {
			int maxScroll = getContentMaxScroll();
			// Smooth scroll: 8px per notch (matching ActionListPanel feel)
			int step = 8;
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * step)));
			layoutWidgets();
			return true;
		}
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (scrollbarDragging && button == 0) {
			updateScrollbarDrag(mouseY);
			return true;
		}
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (scrollbarDragging && button == 0) {
			scrollbarDragging = false;
			return true;
		}
		return false;
	}

	private void updateScrollbarDrag(double mouseY) {
		int maxScroll = getContentMaxScroll();
		if (maxScroll <= 0) return;
		int trackH = h - 2;
		int contentH = maxScroll + h;
		int thumbH = Math.max(10, trackH * h / contentH);
		int thumbTravel = trackH - thumbH;
		if (thumbTravel <= 0) return;
		double relY = mouseY - (y + 1) - thumbH / 2.0;
		double ratio = relY / thumbTravel;
		ratio = Math.max(0, Math.min(1, ratio));
		scrollOffset = (int) (ratio * maxScroll);
		layoutWidgets();
	}

	/**
	 * Handle key presses. Returns true if the key was consumed (e.g., Escape closes dropdown).
	 */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (dropdown != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeDropdown();
				return true;
			}
			return true;
		}

		// Handle expression completion overlay
		if (exprCompletionItems != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeExprCompletion();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER) {
				applyExprCompletion();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP) {
				if (exprCompletionHoverIndex > 0) exprCompletionHoverIndex--;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DOWN) {
				if (exprCompletionHoverIndex < exprCompletionItems.length - 1) exprCompletionHoverIndex++;
				return true;
			}
			closeExprCompletion();
			return false;
		}

		// Tab in an expression editbox → open completion
		if (keyCode == GLFW.GLFW_KEY_TAB && exprCompletionTarget != null) {
			return true;
		}

		return false;
	}

	/**
	 * Called from SpellPreviewScreen when Tab is pressed in an EditBox.
	 */
	public boolean handleTabCompletion(EditBox editBox) {
		if (!exprEditBoxes.contains(editBox)) return false;
		String text = editBox.getValue();
		int cursor = editBox.getCursorPosition();
		int tokenStart = cursor;
		while (tokenStart > 0 && (Character.isLetterOrDigit(text.charAt(tokenStart - 1)) || text.charAt(tokenStart - 1) == '_')) {
			tokenStart--;
		}
		String prefix = text.substring(tokenStart, cursor).toLowerCase();
		List<String> matches = new ArrayList<>();
		for (String fn : EXPR_FUNCTIONS) {
			if (prefix.isEmpty() || fn.toLowerCase().startsWith(prefix)) {
				matches.add(fn);
			}
		}
		if (matches.isEmpty()) return false;
		exprCompletionItems = matches.toArray(new String[0]);
		exprCompletionHoverIndex = 0;
		exprCompletionTarget = editBox;
		exprCompletionInsertStart = tokenStart;
		return true;
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
			case "target_on_ground" -> new SpellConditions.TargetOnGround();
			case "target_speed" -> new SpellConditions.TargetSpeed(0.1, ">");
			case "random_chance" -> new SpellConditions.RandomChance(0.5f);
			case "target_health_below" -> new SpellConditions.TargetHealthBelow(0.5f);
			case "target_health_above" -> new SpellConditions.TargetHealthAbove(0.5f);
			case "target_is_flying" -> new SpellConditions.TargetIsFlying();
			case "target_is_fallflying" -> new SpellConditions.TargetIsFallFlying();
			case "dynamic_tick_interval" -> new SpellConditions.DynamicTickInterval(
					NumberProvider.constant(60), NumberProvider.constant(0));
			case "entity_trait" -> new SpellConditions.EntityTrait("is_lunatic");
			case "entity_flag" -> new SpellConditions.EntityFlagCondition(4);
			case "compare" -> new SpellConditions.CompareNumbers(
					new NumberProviders.PhaseTick(), "<", NumberProvider.constant(100));
			case "variable_check" -> new SpellConditions.VariableCheck("kind", "==", 0);
			case "difficulty_equals" -> new SpellConditions.DifficultyEquals(3);
			case "difficulty_above" -> new SpellConditions.DifficultyAbove(2);
			case "not" -> new SpellConditions.NotCondition(new SpellConditions.AlwaysCondition(true));
			case "and" -> new SpellConditions.AndCondition(List.of(
					new SpellConditions.TickInterval(20, 0),
					new SpellConditions.AlwaysCondition(true)));
			case "or" -> new SpellConditions.OrCondition(List.of(
					new SpellConditions.AlwaysCondition(true),
					new SpellConditions.AlwaysCondition(false)));
			default -> new SpellConditions.AlwaysCondition(true);
		};
	}

	// --- Utility ---

	private static final Map<String, String> ACTION_TYPE_NAMES = Map.ofEntries(
			Map.entry("fire_danmaku", "Fire Danmaku"),
			Map.entry("fire_laser", "Fire Laser"),
			Map.entry("conditional", "Conditional"),
			Map.entry("repeat", "Repeat"),
			Map.entry("delay", "Delay"),
			Map.entry("teleport", "Teleport"),
			Map.entry("spawn_shooter", "Spawn Shooter"),
			Map.entry("burst", "Burst"),
			Map.entry("set_variable", "Set Variable"),
			Map.entry("add_variable", "Add Variable"),
			Map.entry("clear_screen", "Clear Screen"),
			Map.entry("play_sound", "Play Sound"),
			Map.entry("force_phase", "Force Phase"),
			Map.entry("force_spell", "Force Spell"),
			Map.entry("sequence", "Sequence"),
			Map.entry("confine_target", "Confine Target"),
			Map.entry("set_entity_flag", "Set Entity Flag"),
			Map.entry("noop", "Noop"),
			Map.entry("legacy_ticker", "Legacy Ticker")
	);

	private static String difficultyName(int id) {
		return switch (id) {
			case 0 -> "PEACEFUL";
			case 1 -> "EASY";
			case 2 -> "NORMAL";
			case 3 -> "HARD";
			default -> "NORMAL";
		};
	}

	private static int difficultyId(String name) {
		return switch (name) {
			case "PEACEFUL" -> 0;
			case "EASY" -> 1;
			case "NORMAL" -> 2;
			case "HARD" -> 3;
			default -> 2;
		};
	}

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

	private record EditorRow(String label, AbstractWidget widget, boolean fullWidth, int customWidgetW) {
		EditorRow(String label, AbstractWidget widget) {
			this(label, widget, false, -1);
		}
		EditorRow(String label, AbstractWidget widget, boolean fullWidth) {
			this(label, widget, fullWidth, -1);
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
			case "direction_to_target" -> new AimMode.AimModes.DirectionToTarget();
			case "fixed" -> new AimMode.AimModes.FixedDirection(new net.minecraft.world.phys.Vec3(0, 0, 1));
			case "caster_facing" -> new AimMode.AimModes.CasterFacing();
			case "angle_offset" -> new AimMode.AimModes.AngleOffset(NumberProvider.constant(0));
			case "variable_angle" -> new AimMode.AimModes.VariableAngle("aim_angle");
			case "random_angle" -> new AimMode.AimModes.RandomAngle(NumberProvider.constant(360));
			default -> new AimMode.AimModes.Target();
		};
	}

	// --- Expression completion ---

	private void applyExprCompletion() {
		if (exprCompletionItems == null || exprCompletionTarget == null) return;
		if (exprCompletionHoverIndex < 0 || exprCompletionHoverIndex >= exprCompletionItems.length) return;
		String chosen = exprCompletionItems[exprCompletionHoverIndex];
		String template = getFuncInsertText(chosen);
		int cursorInTemplate = getFuncCursorInTemplate(chosen);
		String text = exprCompletionTarget.getValue();
		int cursor = exprCompletionTarget.getCursorPosition();
		String newText = text.substring(0, exprCompletionInsertStart) + template + text.substring(cursor);
		int newPos = exprCompletionInsertStart + cursorInTemplate;
		exprCompletionTarget.setValue(newText);
		exprCompletionTarget.setCursorPosition(newPos);
		try {
			// Clear selection so only cursor moves, not a range
			var method = net.minecraft.client.gui.components.EditBox.class.getDeclaredMethod("setHighlightPos", int.class);
			method.setAccessible(true);
			method.invoke(exprCompletionTarget, newPos);
		} catch (Exception ignored) {}
		closeExprCompletion();
	}

	private void closeExprCompletion() {
		exprCompletionItems = null;
		exprCompletionHoverIndex = -1;
		exprCompletionTarget = null;
	}

	private void doRenderExprCompletion(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (exprCompletionItems == null || exprCompletionTarget == null) return;
		Font font = Minecraft.getInstance().font;
		int itemCount = exprCompletionItems.length;
		int itemH = DROPDOWN_ITEM_H;
		int totalH = Math.min(itemCount * itemH, DROPDOWN_MAX_VISIBLE * itemH);
		int cx = exprCompletionTarget.getX();
		int cy = exprCompletionTarget.getY() + exprCompletionTarget.getHeight();
		int cw = Math.max(exprCompletionTarget.getWidth(), 120);
		if (cy + totalH > y + h) totalH = y + h - cy;
		if (totalH < itemH) return;

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200);
		guiGraphics.fill(cx + 2, cy + 2, cx + cw + 2, cy + totalH + 2, 0x88000000);
		guiGraphics.fill(cx, cy, cx + cw, cy + totalH, 0xFF1a1a30);
		guiGraphics.fill(cx, cy, cx + cw, cy + 1, 0xFF666688);
		guiGraphics.fill(cx, cy + totalH - 1, cx + cw, cy + totalH, 0xFF666688);
		guiGraphics.fill(cx, cy, cx + 1, cy + totalH, 0xFF666688);
		guiGraphics.fill(cx + cw - 1, cy, cx + cw, cy + totalH, 0xFF666688);

		exprCompletionHoverIndex = -1;
		if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + totalH) {
			int rawIdx = (mouseY - cy) / itemH;
			if (rawIdx >= 0 && rawIdx < itemCount) exprCompletionHoverIndex = rawIdx;
		}

		int visCount = Math.min(itemCount, totalH / itemH);
		for (int i = 0; i < visCount; i++) {
			if (i >= itemCount) break;
			int iy = cy + i * itemH;
			if (iy + itemH > cy + totalH) break;
			boolean hovered = i == exprCompletionHoverIndex;
			if (hovered) guiGraphics.fill(cx + 1, iy, cx + cw - 1, iy + itemH, 0x44FFFFFF);
			guiGraphics.drawString(font, getFuncDisplayName(exprCompletionItems[i]), cx + 4, iy + 4,
					hovered ? 0xFFFFDD66 : 0xFFDDDDDD, false);
		}
		guiGraphics.pose().popPose();
	}

	public boolean hasExprCompletion() {
		return exprCompletionItems != null;
	}

	public boolean isEditingExprBox() {
		for (var eb : exprEditBoxes) {
			if (eb.isFocused()) return true;
		}
		return false;
	}

}
