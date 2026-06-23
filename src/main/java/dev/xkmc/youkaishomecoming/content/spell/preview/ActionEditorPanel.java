package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Editor panel for editing SpellAction properties within the preview screen.
 * Supports editing all action types and a type selector mode for creating new actions.
 */
@OnlyIn(Dist.CLIENT)
public class ActionEditorPanel {

	private static final int ROW_HEIGHT = 20;
	private static final int SECTION_HEADER_HEIGHT = 14;
	private static final int LABEL_WIDTH = 70;
	private static final int PADDING = 4;
	private static final int DROPDOWN_ITEM_H = 16;
	private static final int DROPDOWN_MAX_VISIBLE = 10;
	private static final int STRING_DROPDOWN_W = 14;

	private static final String[] CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count",
			"target_on_ground", "target_speed", "random_chance",
			"target_health_below", "target_health_above",
			"target_is_flying", "target_is_fallflying",
			"dynamic_tick_interval", "entity_trait", "entity_flag", "compare",
			"difficulty_equals", "difficulty_above",
			"always", "not", "and", "or"
	};

	private static final String[] SIMPLE_CONDITION_TYPES = {
			"tick_interval", "health_below", "health_above", "tick_elapsed",
			"distance_above", "distance_below", "hit_count",
			"target_on_ground", "target_speed", "random_chance",
			"target_health_below", "target_health_above",
			"target_is_flying", "target_is_fallflying",
			"dynamic_tick_interval", "entity_trait", "entity_flag", "compare",
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
	private final Map<Integer, Integer> scrollStateMap = new HashMap<>();
	private boolean widgetsRegistered = false;
	private boolean scrollbarDragging = false;

	// Depth tracking for nested mover editors
	private int currentDepth = 0;
	// Collapsed sections: key = section label at specific row index
	private final java.util.Set<String> collapsedSections = new java.util.HashSet<>();

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
		// Save current scroll state before switching
		if (actionIndex >= 0) {
			scrollStateMap.put(actionIndex, scrollOffset);
		}
		clearWidgets();
		this.currentAction = action;
		this.actionIndex = index;
		this.scrollOffset = scrollStateMap.getOrDefault(index, 0);
		this.typeSelectorMode = false;
		buildActionRows(action);
		layoutWidgets();
		// Clamp restored offset to valid range after layout
		int maxScroll = getContentMaxScroll();
		if (scrollOffset > maxScroll) {
			scrollOffset = Math.max(0, maxScroll);
		}
	}

	public void clearAction() {
		clearWidgets();
		currentAction = null;
		actionIndex = -1;
		typeSelectorMode = false;
	}

	/**
	 * Clears all stored scroll state entries.
	 * Called when actions are deleted or reordered (indices may shift),
	 * making previously stored scroll positions invalid.
	 */
	public void clearScrollState() {
		scrollStateMap.clear();
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
		closeStringCompletion();
		for (var row : rows) {
			removeWidget.accept(row.widget());
		}
		rows.clear();
		exprEditBoxes.clear();
		stringCompletionSuppliers.clear();
		listCompletionTargets.clear();
		widgetsRegistered = false;
	}

	/**
	 * 取消所有 EditBox 的焦点高亮，但不清除面板内容。
	 * 用于用户点击 viewport 等非编辑区域时移除输入框选中状态。
	 */
	public void unfocusAllEditBoxes() {
		for (var row : rows) {
			if (row.widget() instanceof EditBox eb) {
				eb.setFocused(false);
			}
		}
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
		currentDepth = 0;
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
		} else if (action instanceof EraseEnemyDanmakuAction ee) {
			buildEraseEnemyDanmakuRows(ee);
		} else if (action instanceof SpellActions.PlaySoundAction ps) {
			buildPlaySoundRows(ps);
		} else if (action instanceof SpellActions.ForcePhase fp) {
			buildForcePhaseRows(fp);
		} else if (action instanceof SpellActions.ForceSpell fs) {
			buildForceSpellRows(fs);
		} else if (action instanceof SpellActions.FireSpell fs) {
			buildFireSpellRows(fs);
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
		} else if (action instanceof YsmRenderAction yra) {
			buildYsmRenderRows(yra);
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
		addFullWidthButton("Erase Enemy Danmaku", () -> selectType("erase_enemy_danmaku"));
		addFullWidthButton("Play Sound", () -> selectType("play_sound"));
		addFullWidthButton("Force Phase", () -> selectType("force_phase"));
		addFullWidthButton("Force Spell", () -> selectType("force_spell"));
		addFullWidthButton("Fire Spell", () -> selectType("fire_spell"));
		addFullWidthButton("Confine Target", () -> selectType("confine_target"));
		addFullWidthButton("Set Entity Flag", () -> selectType("set_entity_flag"));
		addFullWidthButton("YSM Render", () -> selectType("ysm_render"));
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
			case "erase_enemy_danmaku" -> new EraseEnemyDanmakuAction(NumberProvider.constant(4), false);
			case "play_sound" -> new SpellActions.PlaySoundAction(
					new ResourceLocation("minecraft", "entity.experience_orb.pickup"), 1f, 1f);
			case "force_phase" -> new SpellActions.ForcePhase(
					new ResourceLocation("youkaishomecoming", "main"), true);
			case "force_spell" -> new SpellActions.ForceSpell(
					new ResourceLocation("youkaishomecoming", "main"), true);
			case "fire_spell" -> new SpellActions.FireSpell(
					new ResourceLocation("youkaishomecoming", "main"), Optional.empty(), NumberProvider.constant(1));
			case "delay" -> new DelayAction(20, new ArrayList<>());
			case "teleport" -> new TeleportAction(OriginConfig.caster(), true);
			case "spawn_shooter" -> new SpawnShooterAction(40, 4f, 100,
					OriginConfig.caster(),
					NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0),
					NumberProvider.constant(1), NumberProvider.constant(0),
					NumberProvider.constant(0), NumberProvider.constant(360), NumberProvider.constant(0),
					PatternType.AIMED, new AimMode.AimModes.Target(),
					Optional.empty(), Optional.empty(), Optional.empty(),
					Optional.empty(), new ArrayList<>());
		case "burst" -> new BurstAction(3, 5, new ArrayList<>());
		case "sequence" -> new SpellActions.SequenceAction(new ArrayList<>());
		case "confine_target" -> new ConfineTargetAction(32, 1.0);
		case "set_entity_flag" -> new SetEntityFlagAction(4, true);
		case "ysm_render" -> new YsmRenderAction("", "", "special", 40, false);
		case "teleport_random" -> new TeleportRandomAction(32, 0.8, 0.4, 16, true, true);
		default -> new SpellActions.NoopAction();
		};
	}

	// --- FireDanmaku rows ---

	private void buildFireDanmakuRows(FireDanmakuAction a) {
		// Compute mover override state
		var overrides = MoverOverrideResolver.resolve(a.mover());

		// === Base group (always visible) ===
		addBulletProviderRows(a);
		addColorProviderRows(a);

		addNumberRow("Count", a.count(), v ->
				notifyDanmaku(old -> old.withCount(v), false));

		addNumberRow("Speed", a.speed(), v ->
				notifyDanmaku(old -> old.withSpeed(v), false), MoverOverrideResolver.isLabelOverridden("Speed", overrides));

		addNumberRow("Lifetime", a.lifetime(), v ->
				notifyDanmaku(old -> old.withLifetime(v), false));

		addNumberRow("Size", a.size(), v ->
				notifyDanmaku(old -> old.withSize(v), false));

		// === Pattern group ===
		addSectionHeader("Pattern");
		if (!isSectionCollapsed("Pattern")) {
			currentDepth++;
			addNumberRow("Angle", a.angleOffset(), v ->
					notifyDanmaku(old -> old.withAngleOffset(v), false));

			addNumberRow("Spread", a.spread(), v ->
					notifyDanmaku(old -> old.withSpread(v), false));

			addNumberRow("Elevation", a.elevation(), v ->
					notifyDanmaku(old -> old.withElevation(v), false));

			addEnumRow("Pattern", PatternType.values(), a.pattern(), v ->
					notifyDanmaku(old -> old.withPattern(v)));

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

			// Tilt angle
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
			currentDepth--;
		}

		// === Group Rotation (applied AFTER origin.rotation and tilt_angle, as an outer transform) ===
		addSectionHeader("Group Rotation (post-origin/tilt)");
		if (!isSectionCollapsed("Group Rotation (post-origin/tilt)")) {
			currentDepth++;
			if (a.groupRotation().isPresent()) {
				var gr = a.groupRotation().get();
				addNumberRow("Rot X", gr.rotX(), v ->
						notifyDanmaku(old -> old.withGroupRotation(Optional.of(new GroupRotation(v,
								old.groupRotation().map(GroupRotation::rotY).orElse(NumberProvider.constant(0)),
								old.groupRotation().map(GroupRotation::rotZ).orElse(NumberProvider.constant(0))))), false));
				addNumberRow("Rot Y", gr.rotY(), v ->
						notifyDanmaku(old -> old.withGroupRotation(Optional.of(new GroupRotation(
								old.groupRotation().map(GroupRotation::rotX).orElse(NumberProvider.constant(0)),
								v,
								old.groupRotation().map(GroupRotation::rotZ).orElse(NumberProvider.constant(0))))), false));
				addNumberRow("Rot Z", gr.rotZ(), v ->
						notifyDanmaku(old -> old.withGroupRotation(Optional.of(new GroupRotation(
								old.groupRotation().map(GroupRotation::rotX).orElse(NumberProvider.constant(0)),
								old.groupRotation().map(GroupRotation::rotY).orElse(NumberProvider.constant(0)),
								v))), false));
				addFullWidthButton("[Remove Group Rotation]", () ->
						notifyDanmaku(old -> old.withGroupRotation(Optional.empty())));
			} else {
				addFullWidthButton("[+ Group Rotation]", () ->
						notifyDanmaku(old -> old.withGroupRotation(Optional.of(new GroupRotation(
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0))))));
			}
			currentDepth--;
		}

		// === Origin group ===
		addSectionHeader("Origin");
		if (!isSectionCollapsed("Origin")) {
			currentDepth++;
			addEnumRow("Origin", OriginConfig.OriginMode.values(), a.origin().mode(), v -> {
				var newOrigin = new OriginConfig(v, a.origin().offsetX(), a.origin().offsetY(),
						a.origin().offsetZ(), a.origin().rotation());
				notifyDanmaku(old -> old.withOrigin(newOrigin));
			});
			buildOriginOffsetRows(a.origin(), newOrigin -> notifyDanmaku(old -> old.withOrigin(newOrigin), false), overrides);
			currentDepth--;
		}

		// === Mover group ===
		addSectionHeader("Mover");
		if (!isSectionCollapsed("Mover")) {
			currentDepth++;
			buildMoverRows(a.mover(),
					newMover -> notifyDanmaku(old -> old.withMover(newMover)),
					newMover -> notifyDanmaku(old -> old.withMover(newMover), false));
			currentDepth--;
		}

		// === Advanced group ===
		addSectionHeader("Advanced");
		if (!isSectionCollapsed("Advanced")) {
			currentDepth++;
			// Trail interval (only show if onTrail is used)
			if (a.onTrail().isPresent()) {
				addIntRow("Trail Intv", a.trailInterval(), v ->
						notifyDanmaku(old -> old.withTrailInterval(v), false));
			}

			// Hit behavior: separate entity/block controls
			addEnumRow("Hit Entity", HitBehavior.values(), a.hitBehaviorEntity(), v ->
					notifyDanmaku(old -> old.withHitBehaviorEntity(v)));
			addEnumRow("Hit Block", HitBehavior.values(), a.hitBehaviorBlock(), v ->
					notifyDanmaku(old -> old.withHitBehaviorBlock(v)));

			// Damage type override
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
			currentDepth--;
		}
	}

	private void addBulletProviderRows(FireDanmakuAction a) {
		BulletProvider provider = a.bulletType();
		String mode = provider instanceof BulletProvider.Indexed ? "indexed" :
				provider instanceof BulletProvider.RandomChoice ? "random_choice" : "constant";
		YHDanmaku.Bullet fallback = firstBullet(provider);
		addStringOptionRow("Bullet Mode",
				new String[]{"constant", "indexed", "random_choice"},
				new String[]{"Constant", "Indexed", "Random"},
				mode,
				next -> notifyDanmaku(old -> old.withBulletProvider(createBulletProvider(next, provider, fallback)), true));
		if (provider instanceof BulletProvider.Constant bc) {
			addEnumRow("Bullet", YHDanmaku.Bullet.values(), bc.bullet(), v ->
					notifyDanmaku(old -> old.withBulletType(v)));
		} else if (provider instanceof BulletProvider.Indexed indexed) {
			addNumberRow("Bullet Index", indexed.index(), v ->
					notifyDanmaku(old -> {
						List<YHDanmaku.Bullet> palette = old.bulletType() instanceof BulletProvider.Indexed cur ?
								cur.palette() : indexed.palette();
						return old.withBulletProvider(new BulletProvider.Indexed(v, palette));
					}, false));
			addListSuggestStringRow("Bullet List", formatBulletList(indexed.palette()), ActionEditorPanel::bulletListOptions, v -> {
				var parsed = parseBulletList(v);
				if (!parsed.isEmpty()) {
					notifyDanmaku(old -> {
						NumberProvider index = old.bulletType() instanceof BulletProvider.Indexed cur ?
								cur.index() : indexed.index();
						return old.withBulletProvider(new BulletProvider.Indexed(index, parsed));
					}, false);
				}
			});
		} else if (provider instanceof BulletProvider.RandomChoice random) {
			addListSuggestStringRow("Bullet List", formatBulletList(random.palette()), ActionEditorPanel::bulletListOptions, v -> {
				var parsed = parseBulletList(v);
				if (!parsed.isEmpty()) {
					notifyDanmaku(old -> old.withBulletProvider(new BulletProvider.RandomChoice(parsed)), false);
				}
			});
		}
	}

	private void addColorProviderRows(FireDanmakuAction a) {
		ColorProvider provider = a.color();
		String mode = provider instanceof ColorProvider.Indexed ? "indexed" :
				provider instanceof ColorProvider.ByVariable ? "by_variable" :
						provider instanceof ColorProvider.Cycle ? "cycle" :
								provider instanceof ColorProvider.RandomChoice ? "random_choice" : "constant";
		DyeColor fallback = firstColor(provider);
		addStringOptionRow("Color Mode",
				new String[]{"constant", "indexed", "by_variable", "cycle", "random_choice"},
				new String[]{"Constant", "Indexed", "Variable", "Cycle", "Random"},
				mode,
				next -> notifyDanmaku(old -> old.withColor(createColorProvider(next, provider, fallback)), true));
		if (provider instanceof ColorProvider.Constant cc) {
			addEnumRow("Color", DyeColor.values(), cc.color(), v ->
					notifyDanmaku(old -> old.withColor(ColorProvider.constant(v))));
		} else if (provider instanceof ColorProvider.Indexed indexed) {
			addNumberRow("Color Index", indexed.index(), v ->
					notifyDanmaku(old -> {
						List<DyeColor> palette = old.color() instanceof ColorProvider.Indexed cur ?
								cur.palette() : indexed.palette();
						return old.withColor(new ColorProvider.Indexed(v, palette));
					}, false));
			addColorPaletteRow(indexed.palette(), list ->
					notifyDanmaku(old -> {
						NumberProvider index = old.color() instanceof ColorProvider.Indexed cur ?
								cur.index() : indexed.index();
						return old.withColor(new ColorProvider.Indexed(index, list));
					}, false));
		} else if (provider instanceof ColorProvider.ByVariable variable) {
			addStringRow("Color Var", variable.key(), v ->
					notifyDanmaku(old -> {
						List<DyeColor> palette = old.color() instanceof ColorProvider.ByVariable cur ?
								cur.palette() : variable.palette();
						return old.withColor(new ColorProvider.ByVariable(v, palette));
					}, true));
			addColorPaletteRow(variable.palette(), list ->
					notifyDanmaku(old -> {
						String key = old.color() instanceof ColorProvider.ByVariable cur ?
								cur.key() : variable.key();
						return old.withColor(new ColorProvider.ByVariable(key, list));
					}, false));
		} else if (provider instanceof ColorProvider.Cycle cycle) {
			addIntRow("Color Interval", cycle.interval(), v ->
					notifyDanmaku(old -> {
						List<DyeColor> palette = old.color() instanceof ColorProvider.Cycle cur ?
								cur.palette() : cycle.palette();
						return old.withColor(new ColorProvider.Cycle(palette, Math.max(1, v)));
					}, false));
			addColorPaletteRow(cycle.palette(), list ->
					notifyDanmaku(old -> {
						int interval = old.color() instanceof ColorProvider.Cycle cur ?
								cur.interval() : cycle.interval();
						return old.withColor(new ColorProvider.Cycle(list, interval));
					}, false));
		} else if (provider instanceof ColorProvider.RandomChoice random) {
			addColorPaletteRow(random.palette(), list ->
					notifyDanmaku(old -> old.withColor(new ColorProvider.RandomChoice(list)), false));
		}
	}

	private void addColorPaletteRow(List<DyeColor> palette, Consumer<List<DyeColor>> onChange) {
		addListSuggestStringRow("Color List", formatColorList(palette), ActionEditorPanel::colorListOptions, v -> {
			var parsed = parseColorList(v);
			if (!parsed.isEmpty()) {
				onChange.accept(parsed);
			}
		});
	}

	private BulletProvider createBulletProvider(String mode, BulletProvider old, YHDanmaku.Bullet fallback) {
		List<YHDanmaku.Bullet> palette = bulletPalette(old, fallback);
		return switch (mode) {
			case "indexed" -> old instanceof BulletProvider.Indexed indexed ? indexed :
					new BulletProvider.Indexed(NumberProvider.constant(0), palette);
			case "random_choice" -> old instanceof BulletProvider.RandomChoice random ? random :
					new BulletProvider.RandomChoice(palette);
			default -> BulletProvider.constant(fallback);
		};
	}

	private ColorProvider createColorProvider(String mode, ColorProvider old, DyeColor fallback) {
		List<DyeColor> palette = colorPalette(old, fallback);
		return switch (mode) {
			case "indexed" -> old instanceof ColorProvider.Indexed indexed ? indexed :
					new ColorProvider.Indexed(NumberProvider.constant(0), palette);
			case "by_variable" -> old instanceof ColorProvider.ByVariable variable ? variable :
					new ColorProvider.ByVariable("i", palette);
			case "cycle" -> old instanceof ColorProvider.Cycle cycle ? cycle :
					new ColorProvider.Cycle(palette, 1);
			case "random_choice" -> old instanceof ColorProvider.RandomChoice random ? random :
					new ColorProvider.RandomChoice(palette);
			default -> ColorProvider.constant(fallback);
		};
	}

	private YHDanmaku.Bullet firstBullet(BulletProvider provider) {
		if (provider instanceof BulletProvider.Constant c) return c.bullet();
		if (provider instanceof BulletProvider.Indexed indexed && !indexed.palette().isEmpty()) return indexed.palette().get(0);
		if (provider instanceof BulletProvider.RandomChoice random && !random.palette().isEmpty()) return random.palette().get(0);
		return YHDanmaku.Bullet.CIRCLE;
	}

	private DyeColor firstColor(ColorProvider provider) {
		if (provider instanceof ColorProvider.Constant c) return c.color();
		if (provider instanceof ColorProvider.Indexed indexed && !indexed.palette().isEmpty()) return indexed.palette().get(0);
		if (provider instanceof ColorProvider.ByVariable variable && !variable.palette().isEmpty()) return variable.palette().get(0);
		if (provider instanceof ColorProvider.Cycle cycle && !cycle.palette().isEmpty()) return cycle.palette().get(0);
		if (provider instanceof ColorProvider.RandomChoice random && !random.palette().isEmpty()) return random.palette().get(0);
		return DyeColor.WHITE;
	}

	private List<YHDanmaku.Bullet> bulletPalette(BulletProvider provider, YHDanmaku.Bullet fallback) {
		if (provider instanceof BulletProvider.Indexed indexed && !indexed.palette().isEmpty()) return indexed.palette();
		if (provider instanceof BulletProvider.RandomChoice random && !random.palette().isEmpty()) return random.palette();
		return List.of(fallback);
	}

	private List<DyeColor> colorPalette(ColorProvider provider, DyeColor fallback) {
		if (provider instanceof ColorProvider.Indexed indexed && !indexed.palette().isEmpty()) return indexed.palette();
		if (provider instanceof ColorProvider.ByVariable variable && !variable.palette().isEmpty()) return variable.palette();
		if (provider instanceof ColorProvider.Cycle cycle && !cycle.palette().isEmpty()) return cycle.palette();
		if (provider instanceof ColorProvider.RandomChoice random && !random.palette().isEmpty()) return random.palette();
		return List.of(fallback);
	}

	private String formatBulletList(List<YHDanmaku.Bullet> values) {
		return values.stream().map(e -> e.name().toLowerCase(java.util.Locale.ROOT))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	private String formatColorList(List<DyeColor> values) {
		return values.stream().map(e -> e.name().toLowerCase(java.util.Locale.ROOT))
				.collect(java.util.stream.Collectors.joining(", "));
	}

	private static List<String> bulletListOptions() {
		List<String> ans = new ArrayList<>();
		for (YHDanmaku.Bullet bullet : YHDanmaku.Bullet.values()) {
			ans.add(bullet.name().toLowerCase(java.util.Locale.ROOT));
		}
		return ans;
	}

	private static List<String> colorListOptions() {
		List<String> ans = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			ans.add(color.name().toLowerCase(java.util.Locale.ROOT));
		}
		return ans;
	}

	private static boolean isBulletOption(String option) {
		for (YHDanmaku.Bullet bullet : YHDanmaku.Bullet.values()) {
			if (bullet.name().equalsIgnoreCase(option)) {
				return true;
			}
		}
		return false;
	}

	private List<YHDanmaku.Bullet> parseBulletList(String raw) {
		List<YHDanmaku.Bullet> ans = new ArrayList<>();
		for (String token : splitList(raw)) {
			try {
				ans.add(YHDanmaku.Bullet.valueOf(token.toUpperCase(java.util.Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return ans;
	}

	private List<DyeColor> parseColorList(String raw) {
		List<DyeColor> ans = new ArrayList<>();
		for (String token : splitList(raw)) {
			try {
				ans.add(DyeColor.valueOf(token.toUpperCase(java.util.Locale.ROOT)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return ans;
	}

	private List<String> splitList(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		List<String> ans = new ArrayList<>();
		for (String token : raw.split("[,\\s]+")) {
			String trimmed = token.trim();
			if (!trimmed.isEmpty()) ans.add(trimmed);
		}
		return ans;
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

		addNumberRow("Thickness", a.thickness(), v ->
				notifyLaser(old -> old.withThickness(v), false));

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
			buildConditionParamRows("", cond, (newCond, rebuild) ->
					notifyConditional(old -> new SpellActions.ConditionalAction(newCond, old.ifTrue(), old.ifFalse()), rebuild));
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
			buildConditionParamRows((idx + 1) + ":", sub, (newSub, rebuild) ->
					notifyCompoundSubCondition(idx, newSub, isAnd, rebuild));
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

	private void buildConditionParamRows(String prefix, SpellCondition cond, BiConsumer<SpellCondition, Boolean> onChanged) {
		if (cond instanceof SpellConditions.TickInterval ti) {
			addIntRow(prefix + "Interval", ti.interval(), v ->
					onChanged.accept(new SpellConditions.TickInterval(v, ti.offset()), false));
			addIntRow(prefix + "Offset", ti.offset(), v ->
					onChanged.accept(new SpellConditions.TickInterval(ti.interval(), v), false));
		} else if (cond instanceof SpellConditions.HealthBelow hb) {
			addFloatRow(prefix + "Threshold", hb.threshold(), v ->
					onChanged.accept(new SpellConditions.HealthBelow(v), false));
		} else if (cond instanceof SpellConditions.HealthAbove ha) {
			addFloatRow(prefix + "Threshold", ha.threshold(), v ->
					onChanged.accept(new SpellConditions.HealthAbove(v), false));
		} else if (cond instanceof SpellConditions.TickElapsed te) {
			addIntRow(prefix + "Ticks", te.ticks(), v ->
					onChanged.accept(new SpellConditions.TickElapsed(v), false));
		} else if (cond instanceof SpellConditions.DistanceAbove da) {
			addDoubleRow(prefix + "Distance", da.distance(), v ->
					onChanged.accept(new SpellConditions.DistanceAbove(v), false));
		} else if (cond instanceof SpellConditions.DistanceBelow db) {
			addDoubleRow(prefix + "Distance", db.distance(), v ->
					onChanged.accept(new SpellConditions.DistanceBelow(v), false));
		} else if (cond instanceof SpellConditions.HitCountCondition hc) {
			addIntRow(prefix + "Count", hc.count(), v ->
					onChanged.accept(new SpellConditions.HitCountCondition(v), false));
		} else if (cond instanceof SpellConditions.TargetOnGround) {
			// No parameters - just a label
		} else if (cond instanceof SpellConditions.TargetSpeed ts) {
			addDoubleRow(prefix + "Threshold", ts.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetSpeed(v, ts.op()), false));
			addStringCycleRow(prefix + "Op", new String[]{">", ">=", "<", "<="}, ts.op(), v ->
					onChanged.accept(new SpellConditions.TargetSpeed(ts.threshold(), v), true));
		} else if (cond instanceof SpellConditions.RandomChance rc) {
			addFloatRow(prefix + "Probability", rc.probability(), v ->
					onChanged.accept(new SpellConditions.RandomChance(v), false));
		} else if (cond instanceof SpellConditions.TargetHealthBelow thb) {
			addFloatRow(prefix + "Threshold", thb.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetHealthBelow(v), false));
		} else if (cond instanceof SpellConditions.TargetHealthAbove tha) {
			addFloatRow(prefix + "Threshold", tha.threshold(), v ->
					onChanged.accept(new SpellConditions.TargetHealthAbove(v), false));
		} else if (cond instanceof SpellConditions.TargetIsFlying) {
			// No parameters
		} else if (cond instanceof SpellConditions.TargetIsFallFlying) {
			// No parameters
		} else if (cond instanceof SpellConditions.AlwaysCondition ac) {
			addStringCycleRow(prefix + "Value", new String[]{"true", "false"},
					ac.value() ? "true" : "false", v ->
					onChanged.accept(new SpellConditions.AlwaysCondition(v.equals("true")), true));
		} else if (cond instanceof SpellConditions.DynamicTickInterval dti) {
			addNumberRow(prefix + "Period", dti.period(), v ->
					onChanged.accept(new SpellConditions.DynamicTickInterval(v, dti.offset()), false));
			addNumberRow(prefix + "Offset", dti.offset(), v ->
					onChanged.accept(new SpellConditions.DynamicTickInterval(dti.period(), v), false));
		} else if (cond instanceof SpellConditions.EntityTrait et) {
			addStringRow(prefix + "Trait", et.trait(), v ->
					onChanged.accept(new SpellConditions.EntityTrait(v), false));
		} else if (cond instanceof SpellConditions.EntityFlagCondition ef) {
			addIntRow(prefix + "Flag", ef.flag(), v ->
					onChanged.accept(new SpellConditions.EntityFlagCondition(v), false));
		} else if (cond instanceof SpellConditions.CompareNumbers cn) {
			addNumberRow(prefix + "Left", cn.left(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(v, cn.op(), cn.right()), false));
			addStringCycleRow(prefix + "Op", new String[]{"<", ">", "==", "!=", "<=", ">="}, cn.op(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(cn.left(), v, cn.right()), true));
			addNumberRow(prefix + "Right", cn.right(), v ->
					onChanged.accept(new SpellConditions.CompareNumbers(cn.left(), cn.op(), v), false));
		} else if (cond instanceof SpellConditions.VariableCheck vc) {
			addStringRow(prefix + "Key", vc.key(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(v, vc.op(), vc.value()), false));
			addStringCycleRow(prefix + "Op", new String[]{"==", "!=", "<", ">", "<=", ">="}, vc.op(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(vc.key(), v, vc.value()), true));
			addDoubleRow(prefix + "Value", vc.value(), v ->
					onChanged.accept(new SpellConditions.VariableCheck(vc.key(), vc.op(), v), false));
		} else if (cond instanceof SpellConditions.DifficultyEquals de) {
			addStringCycleRow(prefix + "Difficulty", new String[]{"PEACEFUL", "EASY", "NORMAL", "HARD"},
					difficultyName(de.difficultyId()), v ->
					onChanged.accept(new SpellConditions.DifficultyEquals(difficultyId(v)), true));
		} else if (cond instanceof SpellConditions.DifficultyAbove da) {
			addStringCycleRow(prefix + "Min Diff", new String[]{"PEACEFUL", "EASY", "NORMAL", "HARD"},
					difficultyName(da.minDifficultyId()), v ->
					onChanged.accept(new SpellConditions.DifficultyAbove(difficultyId(v)), true));
		} else if (cond instanceof SpellConditions.NotCondition nc) {
			// Show inner condition type and params
			addStringCycleRow(prefix + "Inner", SIMPLE_CONDITION_TYPES, getConditionType(nc.condition()), newType ->
					onChanged.accept(new SpellConditions.NotCondition(createDefaultCondition(newType)), true));
			buildConditionParamRows(prefix + "!", nc.condition(), (inner, rebuild) ->
					onChanged.accept(new SpellConditions.NotCondition(inner), rebuild));
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

	private void buildEraseEnemyDanmakuRows(EraseEnemyDanmakuAction ee) {
		addNumberRow("Radius", ee.radius(), v ->
				notifySimple(old -> new EraseEnemyDanmakuAction(v, ((EraseEnemyDanmakuAction) old).sessionsOnly())));
		addBoolRow("Sessions Only", ee.sessionsOnly(), v ->
				notifySimple(old -> new EraseEnemyDanmakuAction(((EraseEnemyDanmakuAction) old).radius(), v), true));
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

	private void buildFireSpellRows(SpellActions.FireSpell fs) {
		List<ResourceLocation> spellOptions = spellOptionsSupplier.get();
		if (spellOptions != null && !spellOptions.isEmpty()) {
			addChoiceRow("Spell ID", spellOptions, fs.spellId(), this::formatSpellOption, id ->
					notifySimple(old -> new SpellActions.FireSpell(id,
							((SpellActions.FireSpell) old).phaseId(), ((SpellActions.FireSpell) old).duration())));
		} else {
			addStringRow("Spell ID", fs.spellId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.FireSpell(id,
						((SpellActions.FireSpell) old).phaseId(), ((SpellActions.FireSpell) old).duration()));
			});
		}
		if (spellOptions == null || !spellOptions.contains(fs.spellId())) {
			addStringRow("Raw ID", fs.spellId().toString(), v -> {
				ResourceLocation id = ResourceLocation.tryParse(v);
				if (id != null) notifySimple(old -> new SpellActions.FireSpell(id,
						((SpellActions.FireSpell) old).phaseId(), ((SpellActions.FireSpell) old).duration()));
			});
		}
		addStringRow("Phase ID", fs.phaseId().map(ResourceLocation::toString).orElse(""), v -> {
			ResourceLocation id = v == null || v.isBlank() ? null : ResourceLocation.tryParse(v);
			if (id != null || v == null || v.isBlank()) {
				notifySimple(old -> new SpellActions.FireSpell(((SpellActions.FireSpell) old).spellId(),
						Optional.ofNullable(id), ((SpellActions.FireSpell) old).duration()));
			}
		});
		addNumberRow("Duration", fs.duration(), v ->
				notifySimple(old -> new SpellActions.FireSpell(((SpellActions.FireSpell) old).spellId(),
						((SpellActions.FireSpell) old).phaseId(), v)));
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

	// --- YSM Render rows ---

	private void buildYsmRenderRows(YsmRenderAction yra) {
		addStringOptionRow("Mode", new String[]{"set", "clear"}, new String[]{"Set / switch", "Clear overrides"},
				yra.clear() ? "clear" : "set", v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					boolean clear = "clear".equals(v);
					return new YsmRenderAction(y.model(), y.texture(), y.animation(), y.duration(), clear, clear ? "all" : "changed");
				}, true));
		if (yra.clear()) {
			addStringOptionRow("Clear Fields", ysmClearTargets(), ysmClearTargetLabels(), normalizeYsmClearTarget(yra.clearTarget(), "all"), v ->
					notifySimple(old -> {
						var y = (YsmRenderAction) old;
						return new YsmRenderAction(y.model(), y.texture(), y.animation(), y.duration(), y.clear(), v);
					}));
			return;
		}
		addSuggestStringRow("Model ID", yra.model(), YSMClientCompat::loadedModelIds, v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					return new YsmRenderAction(v, y.texture(), y.animation(), y.duration(), y.clear(), y.clearTarget());
				}, true));
		addSuggestStringRow("Texture", yra.texture(), () -> YSMClientCompat.loadedTextureNames(currentYsmModel(yra)), v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					return new YsmRenderAction(y.model(), v, y.animation(), y.duration(), y.clear(), y.clearTarget());
				}));
		addSuggestStringRow("Anim Hint", yra.animation(), () -> YSMClientCompat.loadedAnimationNames(currentYsmModel(yra)), v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					return new YsmRenderAction(y.model(), y.texture(), v, y.duration(), y.clear(), y.clearTarget());
				}));
		addIntRow("Duration", yra.duration(), v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					return new YsmRenderAction(y.model(), y.texture(), y.animation(), v, y.clear(), y.clearTarget());
				}));
		addStringOptionRow("Expire Fields", ysmClearTargets(), ysmClearTargetLabels(), normalizeYsmClearTarget(yra.clearTarget(), "changed"), v ->
				notifySimple(old -> {
					var y = (YsmRenderAction) old;
					return new YsmRenderAction(y.model(), y.texture(), y.animation(), y.duration(), y.clear(), v);
				}));
	}

	private static String currentYsmModel(YsmRenderAction action) {
		return action.model().isBlank() ? "" : action.model();
	}

	private static String currentYsmModel(SpawnShooterAction action) {
		return action.ysmModel().isBlank() ? "" : action.ysmModel();
	}

	private static String[] ysmClearTargets() {
		return new String[]{"changed", "animation", "model", "texture", "model_texture", "all"};
	}

	private static String[] ysmClearTargetLabels() {
		return new String[]{"Changed fields", "Animation", "Model", "Texture", "Model + texture", "All fields"};
	}

	private static String normalizeYsmClearTarget(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		for (String target : ysmClearTargets()) {
			if (target.equals(value)) {
				return value;
			}
		}
		return fallback;
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
				notifySimple(old -> ((SpawnShooterAction) old).withHealth(v)));
		addIntRow("Lifetime", ssa.lifetime(), v ->
				notifySimple(old -> ((SpawnShooterAction) old).withLifetime(v)));
		addNumberRow("Vel X", ssa.velocityX(), v ->
				notifySimple(old -> ((SpawnShooterAction) old).withVelocityX(v)));
		addNumberRow("Vel Y", ssa.velocityY(), v ->
				notifySimple(old -> ((SpawnShooterAction) old).withVelocityY(v)));
		addNumberRow("Vel Z", ssa.velocityZ(), v ->
				notifySimple(old -> ((SpawnShooterAction) old).withVelocityZ(v)));
		addFloatRow("Damage", ssa.damage(), v ->
				notifySimple(old -> ((SpawnShooterAction) old).withDamage(v)));
		addSectionHeader("YSM");
		if (!isSectionCollapsed("YSM")) {
			currentDepth++;
			addSuggestStringRow("Model ID", ssa.ysmModel(), YSMClientCompat::loadedModelIds, v ->
					notifySimple(old -> ((SpawnShooterAction) old).withYsmModel(v), true));
			addSuggestStringRow("Texture", ssa.ysmTexture(), () -> YSMClientCompat.loadedTextureNames(currentYsmModel(ssa)), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withYsmTexture(v)));
			addSuggestStringRow("Anim Hint", ssa.ysmAnimation(), () -> YSMClientCompat.loadedAnimationNames(currentYsmModel(ssa)), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withYsmAnimation(v)));
			addIntRow("Duration", ssa.ysmDuration(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withYsmDuration(v)));
			addStringOptionRow("Expire Fields", ysmClearTargets(), ysmClearTargetLabels(), normalizeYsmClearTarget(ssa.ysmClearTarget(), "changed"), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withYsmClearTarget(v)));
			currentDepth--;
		}
		addSectionHeader("Pattern");
		if (!isSectionCollapsed("Pattern")) {
			currentDepth++;
			addNumberRow("Count", ssa.count(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withCount(v), false));
			addNumberRow("Speed", ssa.speed(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withSpeed(v), false));
			addNumberRow("Angle", ssa.angleOffset(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withAngleOffset(v), false));
			addNumberRow("Spread", ssa.spread(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withSpread(v), false));
			addNumberRow("Elevation", ssa.elevation(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withElevation(v), false));
			addEnumRow("Pattern", PatternType.values(), ssa.pattern(), v ->
					notifySimple(old -> ((SpawnShooterAction) old).withPattern(v), true));
			if (ssa.pattern() == PatternType.NESTED_RING || ssa.pattern() == PatternType.GRID) {
				String label = ssa.pattern() == PatternType.GRID ? "Cols" : "Outer Cnt";
				NumberProvider outerProv = ssa.outerCount().orElse(NumberProvider.constant(1));
				addNumberRow(label, outerProv, v ->
						notifySimple(old -> ((SpawnShooterAction) old).withOuterCount(Optional.of(v)), false));
			}
			String currentAim = getAimModeType(ssa.aimMode());
			addStringCycleRow("Aim Mode", AIM_MODE_TYPES, currentAim, newType ->
					notifySimple(old -> ((SpawnShooterAction) old).withAimMode(createDefaultAimMode(newType)), true));
			if (ssa.pattern() == PatternType.NESTED_RING) {
				NumberProvider tiltProv = ssa.tiltAngle().orElse(NumberProvider.constant(0));
				addNumberRow("Axis Tilt", tiltProv, v ->
						notifySimple(old -> ((SpawnShooterAction) old).withTiltAngle(Optional.of(v)), false));
			} else if (ssa.tiltAngle().isPresent()) {
				addNumberRow("Tilt Angle", ssa.tiltAngle().get(), v ->
						notifySimple(old -> ((SpawnShooterAction) old).withTiltAngle(Optional.of(v)), false));
				addFullWidthButton("[Remove Tilt]", () ->
						notifySimple(old -> ((SpawnShooterAction) old).withTiltAngle(Optional.empty()), true));
			} else {
				addFullWidthButton("[+ Tilt Angle]", () ->
						notifySimple(old -> ((SpawnShooterAction) old).withTiltAngle(Optional.of(NumberProvider.constant(0))), true));
			}
			currentDepth--;
		}
		addSectionHeader("Group Rotation");
		if (!isSectionCollapsed("Group Rotation")) {
			currentDepth++;
			if (ssa.groupRotation().isPresent()) {
				var gr = ssa.groupRotation().get();
				addNumberRow("Rot X", gr.rotX(), v ->
						notifySimple(old -> {
							var s = (SpawnShooterAction) old;
							return s.withGroupRotation(Optional.of(new GroupRotation(v,
									s.groupRotation().map(GroupRotation::rotY).orElse(NumberProvider.constant(0)),
									s.groupRotation().map(GroupRotation::rotZ).orElse(NumberProvider.constant(0)))));
						}, false));
				addNumberRow("Rot Y", gr.rotY(), v ->
						notifySimple(old -> {
							var s = (SpawnShooterAction) old;
							return s.withGroupRotation(Optional.of(new GroupRotation(
									s.groupRotation().map(GroupRotation::rotX).orElse(NumberProvider.constant(0)),
									v,
									s.groupRotation().map(GroupRotation::rotZ).orElse(NumberProvider.constant(0)))));
						}, false));
				addNumberRow("Rot Z", gr.rotZ(), v ->
						notifySimple(old -> {
							var s = (SpawnShooterAction) old;
							return s.withGroupRotation(Optional.of(new GroupRotation(
									s.groupRotation().map(GroupRotation::rotX).orElse(NumberProvider.constant(0)),
									s.groupRotation().map(GroupRotation::rotY).orElse(NumberProvider.constant(0)),
									v)));
						}, false));
				addFullWidthButton("[Remove Group Rotation]", () ->
						notifySimple(old -> ((SpawnShooterAction) old).withGroupRotation(Optional.empty()), true));
			} else {
				addFullWidthButton("[+ Group Rotation]", () ->
						notifySimple(old -> ((SpawnShooterAction) old).withGroupRotation(Optional.of(new GroupRotation(
								NumberProvider.constant(0), NumberProvider.constant(0), NumberProvider.constant(0)))), true));
			}
			currentDepth--;
		}
		addEnumRow("Origin", OriginConfig.OriginMode.values(), ssa.origin().mode(), v -> {
			notifySimple(old -> {
				var s = (SpawnShooterAction) old;
				var newOrigin = new OriginConfig(v, s.origin().offsetX(), s.origin().offsetY(),
						s.origin().offsetZ(), s.origin().rotation());
				return s.withOrigin(newOrigin);
			}, true);
		});
		// Origin offsets
		buildOriginOffsetRows(ssa.origin(), newOrigin ->
				notifySimple(old -> ((SpawnShooterAction) old).withOrigin(newOrigin), false));
		// Mover
		buildMoverRows(ssa.mover(),
				newMover -> notifySimple(old -> ((SpawnShooterAction) old).withMover(newMover), true),
				newMover -> notifySimple(old -> ((SpawnShooterAction) old).withMover(newMover), false));
	}

	// --- Shared Origin/Mover row builders ---

	// Top-level mover types (includes "none" for removal and special types like attached)
	private static final String[] MOVER_TYPES = {"none", "acceleration", "deceleration", "rotate", "polar", "composite", "layered", "zero", "bezier", "multi_bezier", "spline", "formula", "orbital", "translate", "attached", "attached_free_rot", "fixed_dir"};

	/**
	 * Sub-mover types available inside composite segments, layered layers, and fixed_dir inner.
	 * This is the single source of truth — when adding a new mover type, add it here too.
	 * Excludes "none" (sub-movers must exist) and special types (attached/space) that don't
	 * make sense as sub-movers.
	 */
	private static final String[] SUB_MOVER_TYPES = {"acceleration", "deceleration", "rotate", "polar", "composite", "layered", "zero", "bezier", "multi_bezier", "spline", "formula", "orbital", "translate"};

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
		if (currentAction instanceof SpawnShooterAction ssa) return ssa.origin();
		return OriginConfig.caster();
	}

	private void buildOriginOffsetRows(OriginConfig cfg, Consumer<OriginConfig> onChanged) {
		buildOriginOffsetRows(cfg, onChanged, java.util.Collections.emptySet());
	}

	private void buildOriginOffsetRows(OriginConfig cfg, Consumer<OriginConfig> onChanged, Set<MoverOverrideResolver.OverriddenParam> overrides) {
		addNumberRow("Off X", cfg.offsetX(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), v, cur.offsetY(), cur.offsetZ(), cur.rotation()));
		}, MoverOverrideResolver.isLabelOverridden("Off X", overrides));
		addNumberRow("Off Y", cfg.offsetY(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), cur.offsetX(), v, cur.offsetZ(), cur.rotation()));
		}, MoverOverrideResolver.isLabelOverridden("Off Y", overrides));
		addNumberRow("Off Z", cfg.offsetZ(), v -> {
			var cur = getCurrentOrigin();
			onChanged.accept(new OriginConfig(cur.mode(), cur.offsetX(), cur.offsetY(), v, cur.rotation()));
		}, MoverOverrideResolver.isLabelOverridden("Off Z", overrides));
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
				addNumberRow("Accel X", acc.x(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(v, a.y(), a.z())));
					}
				});
				addNumberRow("Accel Y", acc.y(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(a.x(), v, a.z())));
					}
				});
				addNumberRow("Accel Z", acc.z(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.AccelerationConfig a) {
						onParamChanged.accept(Optional.of(new MoverConfigs.AccelerationConfig(a.x(), a.y(), v)));
					}
				});
			} else if (cfg instanceof MoverConfigs.DecelerationConfig dc) {
				addNumberRow("Factor", dc.factor(), v ->
						onParamChanged.accept(Optional.of(new MoverConfigs.DecelerationConfig(v))));
			} else if (cfg instanceof MoverConfigs.RotateConfig rot) {
				addNumberRow("Deg/tick", rot.degreesPerTick(), v ->
						onParamChanged.accept(Optional.of(new MoverConfigs.RotateConfig(v))));
			} else if (cfg instanceof MoverConfigs.PolarMoverConfig polar) {
				addNumberRow("Radius", polar.radius(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel())));
					}
				});
				addNumberRow("Rad Spd", polar.radialSpeed(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel())));
					}
				});
				addNumberRow("Ang Spd", polar.angularSpeed(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
						onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
								p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel())));
					}
				});
			addNumberRow("Init Ang", polar.initialAngle(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
					onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel())));
				}
			});
			addNumberRow("Rad Acc", polar.radialAccel(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.PolarMoverConfig p) {
					onParamChanged.accept(Optional.of(new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel())));
				}
			});
			addNumberRow("Ang Acc", polar.angularAccel(), v -> {
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
				String segLabel = "Seg " + (si + 1) + " [" + getMoverType(Optional.of(seg.mover())) + "]";
				addSectionHeader(segLabel);
				if (!isSectionCollapsed(segLabel)) {
					currentDepth++;
					addNumberRow("Seg " + (si + 1) + " Dur", seg.duration(), v -> {
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
				addStringCycleRow("  Type", SUB_MOVER_TYPES, subType, newSubType -> {
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
				currentDepth--;
				} // end if (!isSectionCollapsed)
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
				String layerLabel = "Layer " + (li + 1) + " [" + getMoverType(Optional.of(layerCfg)) + "]";
				addSectionHeader(layerLabel);
				if (!isSectionCollapsed(layerLabel)) {
					currentDepth++;
					// Show sub-mover type as cycle selector
					String subType = getMoverType(Optional.of(layerCfg));
					// Allow nesting: composite and layered can contain each other
					String[] layerTypes = SUB_MOVER_TYPES;
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
					currentDepth--;
				} // end if (!isSectionCollapsed)
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
				addNumberRow("CP1 Fwd", bez.cp1Forward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								v, b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("CP1 Right", bez.cp1Right(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), v, b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("CP1 Up", bez.cp1Up(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), v, b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("CP2 Fwd", bez.cp2Forward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), v, b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("CP2 Right", bez.cp2Right(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), v, b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("CP2 Up", bez.cp2Up(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), v,
								b.endForward(), b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("End Fwd", bez.endForward(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								v, b.endRight(), b.endUp(), b.duration())));
					}
				});
				addNumberRow("End Right", bez.endRight(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), v, b.endUp(), b.duration())));
					}
				});
				addNumberRow("End Up", bez.endUp(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), v, b.duration())));
					}
				});
				addNumberRow("Duration", bez.duration(), v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.BezierMoverConfig b) {
						onParamChanged.accept(Optional.of(new MoverConfigs.BezierMoverConfig(
								b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(),
								b.endForward(), b.endRight(), b.endUp(), v)));
					}
				});
		} else if (cfg instanceof MoverConfigs.MultiBezierMoverConfig mb) {
			// Multi-segment bezier: show per-segment editors with add/remove
			addStringRow("Segments", String.valueOf(mb.segments().size()), v -> {});
			for (int si = 0; si < mb.segments().size(); si++) {
				var seg = mb.segments().get(si);
				final int segIdx = si;
				addStringRow("--- Seg " + (si + 1), "---", v -> {});
				addNumberRow("  CP1 Fwd", seg.cp1Forward(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(v, s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  CP1 Rt", seg.cp1Right(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), v, s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  CP1 Up", seg.cp1Up(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), v, s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  CP2 Fwd", seg.cp2Forward(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), v, s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  CP2 Rt", seg.cp2Right(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), v, s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  CP2 Up", seg.cp2Up(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), v, s.endForward(), s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  End Fwd", seg.endForward(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), v, s.endRight(), s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  End Rt", seg.endRight(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), v, s.endUp(), s.duration()), onParamChanged); });
				addNumberRow("  End Up", seg.endUp(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), v, s.duration()), onParamChanged); });
				addNumberRow("  Duration", seg.duration(), v -> { var s = getMultiBezierSegment(segIdx); if (s != null) updateMultiBezierSegment(segIdx, new MoverConfigs.MultiBezierMoverConfig.BezierSegment(s.cp1Forward(), s.cp1Right(), s.cp1Up(), s.cp2Forward(), s.cp2Right(), s.cp2Up(), s.endForward(), s.endRight(), s.endUp(), v), onParamChanged); });
			}
			addFullWidthButton("[+] Add Bezier Segment", () -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.MultiBezierMoverConfig m) {
					var segs = new java.util.ArrayList<>(m.segments());
					segs.add(new MoverConfigs.MultiBezierMoverConfig.BezierSegment(5, 3, 0, 10, -3, 0, 15, 0, 0, 40));
					onTypeChanged.accept(Optional.of(new MoverConfigs.MultiBezierMoverConfig(segs)));
				}
			});
			if (mb.segments().size() > 1) {
				addFullWidthButton("[-] Remove Last Segment", () -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.MultiBezierMoverConfig m) {
						var segs = new java.util.ArrayList<>(m.segments());
						if (segs.size() > 1) {
							segs.remove(segs.size() - 1);
							onTypeChanged.accept(Optional.of(new MoverConfigs.MultiBezierMoverConfig(segs)));
						}
					}
				});
			}
		} else if (cfg instanceof MoverConfigs.SplineMoverConfig sp) {
			// Spline: waypoints list + duration + closed toggle
			addNumberRow("Duration", sp.duration(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
					onParamChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(s.waypoints(), v, s.closed())));
				}
			});
			addBoolRow("Closed", sp.closed(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
					onTypeChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(s.waypoints(), s.duration(), v)));
				}
			});
			addStringRow("Points", String.valueOf(sp.waypoints().size()), v -> {});
			for (int wi = 0; wi < sp.waypoints().size(); wi++) {
				var wp = sp.waypoints().get(wi);
				final int wpIdx = wi;
				addDoubleRow("P" + (wi + 1) + " Fwd", wp[0], v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
						var wps = new java.util.ArrayList<>(s.waypoints());
						if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{v, wps.get(wpIdx)[1], wps.get(wpIdx)[2]}); }
						onParamChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed())));
					}
				});
				addDoubleRow("P" + (wi + 1) + " Rt", wp[1], v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
						var wps = new java.util.ArrayList<>(s.waypoints());
						if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{wps.get(wpIdx)[0], v, wps.get(wpIdx)[2]}); }
						onParamChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed())));
					}
				});
				addDoubleRow("P" + (wi + 1) + " Up", wp[2], v -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
						var wps = new java.util.ArrayList<>(s.waypoints());
						if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{wps.get(wpIdx)[0], wps.get(wpIdx)[1], v}); }
						onParamChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed())));
					}
				});
			}
			addFullWidthButton("[+] Add Waypoint", () -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
					var wps = new java.util.ArrayList<>(s.waypoints());
					wps.add(new double[]{5, 0, 0});
					onTypeChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed())));
				}
			});
			if (sp.waypoints().size() > 2) {
				addFullWidthButton("[-] Remove Last Waypoint", () -> {
					var cur = getCurrentMover();
					if (cur.isPresent() && cur.get() instanceof MoverConfigs.SplineMoverConfig s) {
						var wps = new java.util.ArrayList<>(s.waypoints());
						if (wps.size() > 2) { wps.remove(wps.size() - 1); }
						onTypeChanged.accept(Optional.of(new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed())));
					}
				});
			}
		} else if (cfg instanceof MoverConfigs.FormulaMoverConfig fm) {
			// Formula mover: three expression strings for x/y/z + base speed
			addNumberRow("Speed", fm.speed(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.FormulaMoverConfig f) {
					onParamChanged.accept(Optional.of(new MoverConfigs.FormulaMoverConfig(f.x(), f.y(), f.z(), v)));
				}
			});
			addStringRow("X (fwd)", fm.x(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.FormulaMoverConfig f) {
					onParamChanged.accept(Optional.of(new MoverConfigs.FormulaMoverConfig(v, f.y(), f.z(), f.speed())));
				}
			});
			addStringRow("Y (right)", fm.y(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.FormulaMoverConfig f) {
					onParamChanged.accept(Optional.of(new MoverConfigs.FormulaMoverConfig(f.x(), v, f.z(), f.speed())));
				}
			});
			addStringRow("Z (up)", fm.z(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.FormulaMoverConfig f) {
					onParamChanged.accept(Optional.of(new MoverConfigs.FormulaMoverConfig(f.x(), f.y(), v, f.speed())));
				}
			});
		} else if (cfg instanceof MoverConfigs.OrbitalMoverConfig orb) {
			// Orbital mover: angular_speed, radius formula, drift formula
			addNumberRow("Ang Spd (°/t)", orb.angularSpeed(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.OrbitalMoverConfig o) {
					onParamChanged.accept(Optional.of(new MoverConfigs.OrbitalMoverConfig(v, o.radius(), o.drift())));
				}
			});
			addStringRow("Radius", orb.radius(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.OrbitalMoverConfig o) {
					onParamChanged.accept(Optional.of(new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), v, o.drift())));
				}
			});
			addStringRow("Drift", orb.drift(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.OrbitalMoverConfig o) {
					onParamChanged.accept(Optional.of(new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), o.radius(), v)));
				}
			});
		} else if (cfg instanceof MoverConfigs.TranslateMoverConfig tr) {
			// Translate mover: aim mode + speed, or raw x/y/z formulas
			String[] aimModes = {"none", "target", "forward"};
			addStringCycleRow("Aim", aimModes, tr.aim(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.TranslateMoverConfig t) {
					onTypeChanged.accept(Optional.of(new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), t.speed(), v)));
				}
			});
			addNumberRow("Speed", tr.speed(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.TranslateMoverConfig t) {
					onParamChanged.accept(Optional.of(new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), v, t.aim())));
				}
			});
			addStringRow("X (east)", tr.x(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.TranslateMoverConfig t) {
					onParamChanged.accept(Optional.of(new MoverConfigs.TranslateMoverConfig(v, t.y(), t.z(), t.speed(), t.aim())));
				}
			});
			addStringRow("Y (up)", tr.y(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.TranslateMoverConfig t) {
					onParamChanged.accept(Optional.of(new MoverConfigs.TranslateMoverConfig(t.x(), v, t.z(), t.speed(), t.aim())));
				}
			});
			addStringRow("Z (south)", tr.z(), v -> {
				var cur = getCurrentMover();
				if (cur.isPresent() && cur.get() instanceof MoverConfigs.TranslateMoverConfig t) {
					onParamChanged.accept(Optional.of(new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), v, t.speed(), t.aim())));
				}
			});
		} else if (cfg instanceof MoverConfigs.AttachedMoverConfig) {
			// No parameters; show a hint
			addStringRow("Mode", "Locks pos to owner", v -> {});
		} else if (cfg instanceof MoverConfigs.AttachedFreeRotMoverConfig) {
			addStringRow("Mode", "Locks pos+facing to owner", v -> {});
		} else if (cfg instanceof MoverConfigs.FixedDirMoverConfig fdm) {
			// fixed_dir wraps an inner mover; expose inner type selector. Inner params are not edited inline here.
			String innerType = getMoverType(Optional.of(fdm.inner()));
			String[] innerTypes = SUB_MOVER_TYPES;
			addStringCycleRow("Inner", innerTypes, innerType, newType -> {
				var newInner = createDefaultMover(newType);
				if (newInner.isPresent()) {
					onTypeChanged.accept(Optional.of(new MoverConfigs.FixedDirMoverConfig(newInner.get())));
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
		if (cfg instanceof MoverConfigs.MultiBezierMoverConfig) return "multi_bezier";
		if (cfg instanceof MoverConfigs.SplineMoverConfig) return "spline";
		if (cfg instanceof MoverConfigs.FormulaMoverConfig) return "formula";
		if (cfg instanceof MoverConfigs.OrbitalMoverConfig) return "orbital";
		if (cfg instanceof MoverConfigs.TranslateMoverConfig) return "translate";
		if (cfg instanceof MoverConfigs.AttachedMoverConfig) return "attached";
		if (cfg instanceof MoverConfigs.AttachedFreeRotMoverConfig) return "attached_free_rot";
		if (cfg instanceof MoverConfigs.FixedDirMoverConfig) return "fixed_dir";
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
			case "multi_bezier" -> Optional.of(new MoverConfigs.MultiBezierMoverConfig(List.of(
					new MoverConfigs.MultiBezierMoverConfig.BezierSegment(5, 3, 0, 10, -3, 0, 15, 0, 0, 40)
			)));
			case "spline" -> Optional.of(new MoverConfigs.SplineMoverConfig(List.of(
					new double[]{5, 5, 0},
					new double[]{5, -5, 0},
					new double[]{-5, -5, 0},
					new double[]{-5, 5, 0}
			), 60, true));
			case "formula" -> Optional.of(new MoverConfigs.FormulaMoverConfig(
					"0", "3 * sin(tick * 0.15)", "3 * cos(tick * 0.15)", 0.3));
			case "orbital" -> Optional.of(new MoverConfigs.OrbitalMoverConfig(5.0, "3 * sin(tick * 0.05)", "0"));
			case "translate" -> Optional.of(new MoverConfigs.TranslateMoverConfig("0", "0", "0", 0.3, "target"));
			case "attached" -> Optional.of(new MoverConfigs.AttachedMoverConfig());
			case "attached_free_rot" -> Optional.of(new MoverConfigs.AttachedFreeRotMoverConfig());
			case "fixed_dir" -> Optional.of(new MoverConfigs.FixedDirMoverConfig(new MoverConfigs.ZeroMoverConfig()));
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
			addNumberRow("  Acc X", acc.x(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(v, a.y(), a.z()), onParamChanged);
				}
			});
			addNumberRow("  Acc Y", acc.y(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(a.x(), v, a.z()), onParamChanged);
				}
			});
			addNumberRow("  Acc Z", acc.z(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateCompositeSegment(segIdx, new MoverConfigs.AccelerationConfig(a.x(), a.y(), v), onParamChanged);
				}
			});
		} else if (subCfg instanceof MoverConfigs.DecelerationConfig dc) {
			addNumberRow("  Factor", dc.factor(), v -> updateCompositeSegment(segIdx,
					new MoverConfigs.DecelerationConfig(v), onParamChanged));
		} else if (subCfg instanceof MoverConfigs.RotateConfig rot) {
			addNumberRow("  Deg/t", rot.degreesPerTick(), v -> updateCompositeSegment(segIdx,
					new MoverConfigs.RotateConfig(v), onParamChanged));
		} else if (subCfg instanceof MoverConfigs.PolarMoverConfig polar) {
			addNumberRow("  Radius", polar.radius(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Rad Spd", polar.radialSpeed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Ang Spd", polar.angularSpeed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Init Ang", polar.initialAngle(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Rad Acc", polar.radialAccel(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Ang Acc", polar.angularAccel(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateCompositeSegment(segIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), v), onParamChanged);
				}
			});
		} else if (subCfg instanceof MoverConfigs.BezierMoverConfig bez) {
			buildNestedBezierParams(bez, segIdx, true, onParamChanged);
		} else if (subCfg instanceof MoverConfigs.FormulaMoverConfig fm) {
			buildNestedFormulaParams(fm, segIdx, true, onParamChanged);
		} else if (subCfg instanceof MoverConfigs.SplineMoverConfig sp) {
			buildNestedSplineParams(sp, segIdx, true, onTypeChanged, onParamChanged);
		} else if (subCfg instanceof MoverConfigs.CompositeMoverConfig || subCfg instanceof MoverConfigs.LayeredMoverConfig) {
			// Recursive: render nested mover using the full mover editor at increased depth
			buildNestedMoverRows(Optional.of(subCfg), segIdx, true, onTypeChanged, onParamChanged);
		} else if (subCfg instanceof MoverConfigs.OrbitalMoverConfig orb) {
			addNumberRow("  Ang Spd", orb.angularSpeed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateCompositeSegment(segIdx, new MoverConfigs.OrbitalMoverConfig(v, o.radius(), o.drift()), onParamChanged);
				}
			});
			addStringRow("  Radius", orb.radius(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateCompositeSegment(segIdx, new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), v, o.drift()), onParamChanged);
				}
			});
			addStringRow("  Drift", orb.drift(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateCompositeSegment(segIdx, new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), o.radius(), v), onParamChanged);
				}
			});
		} else if (subCfg instanceof MoverConfigs.TranslateMoverConfig tr) {
			String[] aimModes = {"none", "target", "forward"};
			addStringCycleRow("  Aim", aimModes, tr.aim(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateCompositeSegment(segIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), t.speed(), v), onTypeChanged);
				}
			});
			addNumberRow("  Speed", tr.speed(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateCompositeSegment(segIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), v, t.aim()), onParamChanged);
				}
			});
			addStringRow("  X (east)", tr.x(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateCompositeSegment(segIdx, new MoverConfigs.TranslateMoverConfig(v, t.y(), t.z(), t.speed(), t.aim()), onParamChanged);
				}
			});
			addStringRow("  Y (up)", tr.y(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateCompositeSegment(segIdx, new MoverConfigs.TranslateMoverConfig(t.x(), v, t.z(), t.speed(), t.aim()), onParamChanged);
				}
			});
			addStringRow("  Z (south)", tr.z(), v -> {
				MoverConfig current = getCompositeSegmentMover(segIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateCompositeSegment(segIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), v, t.speed(), t.aim()), onParamChanged);
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
			addNumberRow("  Acc X", acc.x(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(v, a.y(), a.z()), onParamChanged);
				}
			});
			addNumberRow("  Acc Y", acc.y(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(a.x(), v, a.z()), onParamChanged);
				}
			});
			addNumberRow("  Acc Z", acc.z(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.AccelerationConfig a) {
					updateLayeredLayer(layerIdx, new MoverConfigs.AccelerationConfig(a.x(), a.y(), v), onParamChanged);
				}
			});
		} else if (layerCfg instanceof MoverConfigs.DecelerationConfig dc) {
			addNumberRow("  Factor", dc.factor(), v -> updateLayeredLayer(layerIdx,
					new MoverConfigs.DecelerationConfig(v), onParamChanged));
		} else if (layerCfg instanceof MoverConfigs.RotateConfig rot) {
			addNumberRow("  Deg/t", rot.degreesPerTick(), v -> updateLayeredLayer(layerIdx,
					new MoverConfigs.RotateConfig(v), onParamChanged));
		} else if (layerCfg instanceof MoverConfigs.PolarMoverConfig polar) {
			addNumberRow("  Radius", polar.radius(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							v, p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Rad Spd", polar.radialSpeed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), v, p.radialAccel(), p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Rad Acc", polar.radialAccel(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), v, p.initialAngle(), p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Init Ang", polar.initialAngle(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), v, p.angularSpeed(), p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Ang Spd", polar.angularSpeed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), v, p.angularAccel()), onParamChanged);
				}
			});
			addNumberRow("  Ang Acc", polar.angularAccel(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.PolarMoverConfig p) {
					updateLayeredLayer(layerIdx, new MoverConfigs.PolarMoverConfig(
							p.radius(), p.radialSpeed(), p.radialAccel(), p.initialAngle(), p.angularSpeed(), v), onParamChanged);
				}
			});
		} else if (layerCfg instanceof MoverConfigs.BezierMoverConfig bez) {
			buildNestedBezierParams(bez, layerIdx, false, onParamChanged);
		} else if (layerCfg instanceof MoverConfigs.FormulaMoverConfig fm) {
			buildNestedFormulaParams(fm, layerIdx, false, onParamChanged);
		} else if (layerCfg instanceof MoverConfigs.SplineMoverConfig sp) {
			buildNestedSplineParams(sp, layerIdx, false, onTypeChanged, onParamChanged);
		} else if (layerCfg instanceof MoverConfigs.CompositeMoverConfig || layerCfg instanceof MoverConfigs.LayeredMoverConfig) {
			// Recursive: render nested mover using the full mover editor at increased depth
			buildNestedMoverRows(Optional.of(layerCfg), layerIdx, false, onTypeChanged, onParamChanged);
		} else if (layerCfg instanceof MoverConfigs.OrbitalMoverConfig orb) {
			addNumberRow("  Ang Spd", orb.angularSpeed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateLayeredLayer(layerIdx, new MoverConfigs.OrbitalMoverConfig(v, o.radius(), o.drift()), onParamChanged);
				}
			});
			addStringRow("  Radius", orb.radius(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateLayeredLayer(layerIdx, new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), v, o.drift()), onParamChanged);
				}
			});
			addStringRow("  Drift", orb.drift(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.OrbitalMoverConfig o) {
					updateLayeredLayer(layerIdx, new MoverConfigs.OrbitalMoverConfig(o.angularSpeed(), o.radius(), v), onParamChanged);
				}
			});
		} else if (layerCfg instanceof MoverConfigs.TranslateMoverConfig tr) {
			String[] aimModes = {"none", "target", "forward"};
			addStringCycleRow("  Aim", aimModes, tr.aim(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateLayeredLayer(layerIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), t.speed(), v), onTypeChanged);
				}
			});
			addNumberRow("  Speed", tr.speed(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateLayeredLayer(layerIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), t.z(), v, t.aim()), onParamChanged);
				}
			});
			addStringRow("  X (east)", tr.x(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateLayeredLayer(layerIdx, new MoverConfigs.TranslateMoverConfig(v, t.y(), t.z(), t.speed(), t.aim()), onParamChanged);
				}
			});
			addStringRow("  Y (up)", tr.y(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateLayeredLayer(layerIdx, new MoverConfigs.TranslateMoverConfig(t.x(), v, t.z(), t.speed(), t.aim()), onParamChanged);
				}
			});
			addStringRow("  Z (south)", tr.z(), v -> {
				MoverConfig current = getLayeredLayerMover(layerIdx);
				if (current instanceof MoverConfigs.TranslateMoverConfig t) {
					updateLayeredLayer(layerIdx, new MoverConfigs.TranslateMoverConfig(t.x(), t.y(), v, t.speed(), t.aim()), onParamChanged);
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

	/**
	 * Shared bezier parameter editor for nested contexts (composite segments or layered layers).
	 * @param isComposite true = update via composite segment, false = update via layered layer
	 */
	private void buildNestedBezierParams(MoverConfigs.BezierMoverConfig bez, int idx,
										 boolean isComposite, Consumer<Optional<MoverConfig>> onParamChanged) {
		addNumberRow("  CP1 Fwd", bez.cp1Forward(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(v, b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  CP1 Rt", bez.cp1Right(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), v, b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  CP1 Up", bez.cp1Up(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), v, b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  CP2 Fwd", bez.cp2Forward(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), v, b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  CP2 Rt", bez.cp2Right(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), v, b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  CP2 Up", bez.cp2Up(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), v, b.endForward(), b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  End Fwd", bez.endForward(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), v, b.endRight(), b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  End Rt", bez.endRight(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), v, b.endUp(), b.duration()), onParamChanged); });
		addNumberRow("  End Up", bez.endUp(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), v, b.duration()), onParamChanged); });
		addNumberRow("  Duration", bez.duration(), v -> { var b = getNestedBezier(idx, isComposite); if (b != null) updateNested(idx, isComposite, new MoverConfigs.BezierMoverConfig(b.cp1Forward(), b.cp1Right(), b.cp1Up(), b.cp2Forward(), b.cp2Right(), b.cp2Up(), b.endForward(), b.endRight(), b.endUp(), v), onParamChanged); });
	}

	private MoverConfigs.BezierMoverConfig getNestedBezier(int idx, boolean isComposite) {
		MoverConfig cfg = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
		return cfg instanceof MoverConfigs.BezierMoverConfig b ? b : null;
	}

	private void updateNested(int idx, boolean isComposite, MoverConfig newMover,
							  Consumer<Optional<MoverConfig>> onParamChanged) {
		if (isComposite) {
			updateCompositeSegment(idx, newMover, onParamChanged);
		} else {
			updateLayeredLayer(idx, newMover, onParamChanged);
		}
	}

	/** Nested formula mover params editor. */
	private void buildNestedFormulaParams(MoverConfigs.FormulaMoverConfig fm, int idx, boolean isComposite,
										  Consumer<Optional<MoverConfig>> onParamChanged) {
		addNumberRow("  Speed", fm.speed(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.FormulaMoverConfig f) {
				updateNested(idx, isComposite, new MoverConfigs.FormulaMoverConfig(f.x(), f.y(), f.z(), v), onParamChanged);
			}
		});
		addStringRow("  X (fwd)", fm.x(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.FormulaMoverConfig f) {
				updateNested(idx, isComposite, new MoverConfigs.FormulaMoverConfig(v, f.y(), f.z(), f.speed()), onParamChanged);
			}
		});
		addStringRow("  Y (right)", fm.y(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.FormulaMoverConfig f) {
				updateNested(idx, isComposite, new MoverConfigs.FormulaMoverConfig(f.x(), v, f.z(), f.speed()), onParamChanged);
			}
		});
		addStringRow("  Z (up)", fm.z(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.FormulaMoverConfig f) {
				updateNested(idx, isComposite, new MoverConfigs.FormulaMoverConfig(f.x(), f.y(), v, f.speed()), onParamChanged);
			}
		});
	}

	/** Nested spline mover params editor. */
	private void buildNestedSplineParams(MoverConfigs.SplineMoverConfig sp, int idx, boolean isComposite,
										 Consumer<Optional<MoverConfig>> onTypeChanged,
										 Consumer<Optional<MoverConfig>> onParamChanged) {
		addNumberRow("  Duration", sp.duration(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.SplineMoverConfig s) {
				updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(s.waypoints(), v, s.closed()), onParamChanged);
			}
		});
		addBoolRow("  Closed", sp.closed(), v -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.SplineMoverConfig s) {
				updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(s.waypoints(), s.duration(), v), onTypeChanged);
			}
		});
		for (int wi = 0; wi < sp.waypoints().size(); wi++) {
			var wp = sp.waypoints().get(wi);
			final int wpIdx = wi;
			addDoubleRow("  P" + (wi + 1) + " Fwd", wp[0], v -> {
				MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
				if (current instanceof MoverConfigs.SplineMoverConfig s) {
					var wps = new java.util.ArrayList<>(s.waypoints());
					if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{v, wps.get(wpIdx)[1], wps.get(wpIdx)[2]}); }
					updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed()), onParamChanged);
				}
			});
			addDoubleRow("  P" + (wi + 1) + " Rt", wp[1], v -> {
				MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
				if (current instanceof MoverConfigs.SplineMoverConfig s) {
					var wps = new java.util.ArrayList<>(s.waypoints());
					if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{wps.get(wpIdx)[0], v, wps.get(wpIdx)[2]}); }
					updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed()), onParamChanged);
				}
			});
			addDoubleRow("  P" + (wi + 1) + " Up", wp[2], v -> {
				MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
				if (current instanceof MoverConfigs.SplineMoverConfig s) {
					var wps = new java.util.ArrayList<>(s.waypoints());
					if (wpIdx < wps.size()) { wps.set(wpIdx, new double[]{wps.get(wpIdx)[0], wps.get(wpIdx)[1], v}); }
					updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed()), onParamChanged);
				}
			});
		}
		addFullWidthButton("[+] Add Waypoint", () -> {
			MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
			if (current instanceof MoverConfigs.SplineMoverConfig s) {
				var wps = new java.util.ArrayList<>(s.waypoints());
				wps.add(new double[]{5, 0, 0});
				updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed()), onTypeChanged);
			}
		});
		if (sp.waypoints().size() > 2) {
			addFullWidthButton("[-] Remove Last Waypoint", () -> {
				MoverConfig current = isComposite ? getCompositeSegmentMover(idx) : getLayeredLayerMover(idx);
				if (current instanceof MoverConfigs.SplineMoverConfig s) {
					var wps = new java.util.ArrayList<>(s.waypoints());
					if (wps.size() > 2) { wps.remove(wps.size() - 1); }
					updateNested(idx, isComposite, new MoverConfigs.SplineMoverConfig(wps, s.duration(), s.closed()), onTypeChanged);
				}
			});
		}
	}

	/**
	 * Recursively render a nested mover's internal parameters (for composite/layered inside composite/layered).
	 * Uses the parent's update callbacks to propagate changes up the tree.
	 */
	private void buildNestedMoverRows(Optional<MoverConfig> nestedMoverOpt, int parentIdx, boolean parentIsComposite,
									  Consumer<Optional<MoverConfig>> onTypeChanged,
									  Consumer<Optional<MoverConfig>> onParamChanged) {
		if (nestedMoverOpt.isEmpty()) return;
		MoverConfig nestedCfg = nestedMoverOpt.get();

		if (nestedCfg instanceof MoverConfigs.CompositeMoverConfig comp) {
			addStringRow("  Segments", String.valueOf(comp.segments().size()), v -> {});
			for (int si = 0; si < comp.segments().size(); si++) {
				var seg = comp.segments().get(si);
				final int segIdx = si;
				final int pIdx = parentIdx;
				String segLabel = "Nested Seg " + (si + 1) + " [" + getMoverType(Optional.of(seg.mover())) + "]";
				addSectionHeader(segLabel);
				if (!isSectionCollapsed(segLabel)) {
					currentDepth++;
					addNumberRow("  Duration", seg.duration(), v -> {
						MoverConfig parentMover = parentIsComposite ? getCompositeSegmentMover(pIdx) : getLayeredLayerMover(pIdx);
						if (parentMover instanceof MoverConfigs.CompositeMoverConfig c) {
							var segs = new java.util.ArrayList<>(c.segments());
							if (segIdx < segs.size()) {
								segs.set(segIdx, new MoverConfigs.CompositeMoverConfig.Segment(v, segs.get(segIdx).mover()));
								updateNested(pIdx, parentIsComposite, new MoverConfigs.CompositeMoverConfig(segs), onParamChanged);
							}
						}
					});
					String subType = getMoverType(Optional.of(seg.mover()));
					addStringCycleRow("  Type", SUB_MOVER_TYPES, subType, newSubType -> {
						MoverConfig parentMover = parentIsComposite ? getCompositeSegmentMover(pIdx) : getLayeredLayerMover(pIdx);
						if (parentMover instanceof MoverConfigs.CompositeMoverConfig c) {
							var segs = new java.util.ArrayList<>(c.segments());
							if (segIdx < segs.size()) {
								var newMover = createDefaultMover(newSubType);
								if (newMover.isPresent()) {
									segs.set(segIdx, new MoverConfigs.CompositeMoverConfig.Segment(segs.get(segIdx).duration(), newMover.get()));
									updateNested(pIdx, parentIsComposite, new MoverConfigs.CompositeMoverConfig(segs), onTypeChanged);
								}
							}
						}
					});
					// Render sub-mover params (non-recursive for now to prevent infinite depth)
					if (currentDepth < 4) {
						buildCompositeSegmentParams(seg.mover(), parentIdx, onTypeChanged, onParamChanged);
					}
					currentDepth--;
				}
			}
		} else if (nestedCfg instanceof MoverConfigs.LayeredMoverConfig layered) {
			addStringRow("  Layers", String.valueOf(layered.layers().size()), v -> {});
			for (int li = 0; li < layered.layers().size(); li++) {
				var layerCfg = layered.layers().get(li);
				final int layerIdx = li;
				final int pIdx = parentIdx;
				String layerLabel = "Nested L" + (li + 1) + " [" + getMoverType(Optional.of(layerCfg)) + "]";
				addSectionHeader(layerLabel);
				if (!isSectionCollapsed(layerLabel)) {
					currentDepth++;
					String subType = getMoverType(Optional.of(layerCfg));
					addStringCycleRow("  Type", SUB_MOVER_TYPES, subType, newSubType -> {
						MoverConfig parentMover = parentIsComposite ? getCompositeSegmentMover(pIdx) : getLayeredLayerMover(pIdx);
						if (parentMover instanceof MoverConfigs.LayeredMoverConfig lm) {
							var layers = new java.util.ArrayList<>(lm.layers());
							if (layerIdx < layers.size()) {
								var newMover = createDefaultMover(newSubType);
								if (newMover.isPresent()) {
									layers.set(layerIdx, newMover.get());
									updateNested(pIdx, parentIsComposite, new MoverConfigs.LayeredMoverConfig(layers), onTypeChanged);
								}
							}
						}
					});
					// Render sub-layer params (non-recursive for now to prevent infinite depth)
					if (currentDepth < 4) {
						buildLayeredLayerParams(layerCfg, parentIdx, onTypeChanged, onParamChanged);
					}
					currentDepth--;
				}
			}
		}
	}

	// --- Multi-bezier helpers ---

	private MoverConfigs.MultiBezierMoverConfig.BezierSegment getMultiBezierSegment(int segIdx) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.MultiBezierMoverConfig m) {
			if (segIdx < m.segments().size()) {
				return m.segments().get(segIdx);
			}
		}
		return null;
	}

	private void updateMultiBezierSegment(int segIdx, MoverConfigs.MultiBezierMoverConfig.BezierSegment newSeg,
										  Consumer<Optional<MoverConfig>> onParamChanged) {
		var cur = getCurrentMover();
		if (cur.isPresent() && cur.get() instanceof MoverConfigs.MultiBezierMoverConfig m) {
			var segs = new java.util.ArrayList<>(m.segments());
			if (segIdx < segs.size()) {
				segs.set(segIdx, newSeg);
				onParamChanged.accept(Optional.of(new MoverConfigs.MultiBezierMoverConfig(segs)));
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
			displayNames[i] = SpellEditorLocalization.t(formatEnum(values[i]));
		}
		int selectedIndex = current.ordinal();
		int rowIndex = rows.size();
		var btn = Button.builder(Component.literal(displayNames[selectedIndex] + " \u25BC"), b -> {
			openDropdown(displayNames, selectedIndex, idx -> onChange.accept(values[idx]), rowIndex);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	private void addStringCycleRow(String label, String[] values, String current, Consumer<String> onChange) {
		addStringOptionRow(label, values, values, current, onChange);
	}

	private void addStringOptionRow(String label, String[] values, String[] displayNames, String current, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		String[] localizedDisplayNames = new String[displayNames.length];
		for (int i = 0; i < displayNames.length; i++) {
			localizedDisplayNames[i] = SpellEditorLocalization.t(displayNames[i]);
		}
		int selectedIdx = -1;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(current)) {
				selectedIdx = i;
				break;
			}
		}
		final int selectedIndex = selectedIdx;
		String display = selectedIndex >= 0 && selectedIndex < localizedDisplayNames.length ? localizedDisplayNames[selectedIndex] : SpellEditorLocalization.t(current);
		int rowIndex = rows.size();
		var btn = Button.builder(Component.literal(display + " \u25BC"), b -> {
			openDropdown(localizedDisplayNames, selectedIndex, idx -> onChange.accept(values[idx]), rowIndex);
		}).bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow(label, btn, false));
	}

	// Expression autocomplete keywords
	private static final String[] EXPR_FUNCTIONS = {
			"rand", "random", "lerp", "lerp_time", "hp", "health", "by_health",
			"tick_mod", "sin", "cos", "sqrt", "abs", "floor", "ceil", "round",
			"pow", "root", "log", "ln", "exp", "max", "min", "clamp", "gaussian", "choose",
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
		if (name.equals("abs")) return "abs()";
		if (name.equals("floor")) return "floor()";
		if (name.equals("ceil")) return "ceil()";
		if (name.equals("round")) return "round()";
		if (name.equals("pow")) return "pow(, )";
		if (name.equals("root")) return "root(, )";
		if (name.equals("log") || name.equals("ln")) return "log()";
		if (name.equals("exp")) return "exp()";
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
		if (name.equals("abs")) return 4;
		if (name.equals("floor")) return 6;
		if (name.equals("ceil")) return 5;
		if (name.equals("round")) return 6;
		if (name.equals("pow")) return 4;
		if (name.equals("root")) return 5;
		if (name.equals("log") || name.equals("ln")) return name.length() + 1;
		if (name.equals("exp")) return 4;
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
		if (name.equals("abs")) return "abs(input)";
		if (name.equals("floor")) return "floor(input)";
		if (name.equals("ceil")) return "ceil(input)";
		if (name.equals("round")) return "round(input)";
		if (name.equals("pow")) return "pow(base, exp)";
		if (name.equals("root")) return "root(value, degree)";
		if (name.equals("log") || name.equals("ln")) return "log(input, base?)";
		if (name.equals("exp")) return "exp(input)";
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
			"tick_mod", "sin", "cos", "sqrt", "abs", "floor", "ceil", "round",
			"pow", "root", "log", "ln", "exp", "max", "min", "clamp", "gaussian", "choose"
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
	private final Map<EditBox, java.util.function.Supplier<List<String>>> stringCompletionSuppliers = new HashMap<>();
	private final Set<EditBox> listCompletionTargets = new HashSet<>();

	// Expression completion overlay
	private String[] exprCompletionItems = null;
	private int exprCompletionHoverIndex = -1;
	private EditBox exprCompletionTarget = null;
	private int exprCompletionInsertStart = -1;

	// Plain string field completion overlay
	private String[] stringCompletionItems = null;
	private int stringCompletionHoverIndex = -1;
	private EditBox stringCompletionTarget = null;
	private int stringCompletionInsertStart = -1;
	private int stringCompletionInsertEnd = -1;
	private int stringCompletionScrollOffset = 0;

	private void addNumberRow(String label, NumberProvider provider, Consumer<NumberProvider> onChange) {
		addNumberRow(label, provider, onChange, false);
	}

	private void addNumberRow(String label, NumberProvider provider, Consumer<NumberProvider> onChange, boolean overridden) {
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
		rows.add(new EditorRow(displayLabel, editBox, false, -1, currentDepth, false, overridden));
	}

	private void addBoolRow(String label, boolean value, Consumer<Boolean> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var btn = Button.builder(Component.literal(SpellEditorLocalization.t(value ? "ON" : "OFF")), b -> {
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

	private void addSuggestStringRow(String label, String value, java.util.function.Supplier<List<String>> suggestions, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setMaxLength(256);
		editBox.setValue(value);
		editBox.setResponder(onChange::accept);
		stringCompletionSuppliers.put(editBox, suggestions);
		rows.add(new EditorRow(label, editBox, false));
	}

	private void addListSuggestStringRow(String label, String value, java.util.function.Supplier<List<String>> suggestions, Consumer<String> onChange) {
		int widgetW = w - LABEL_WIDTH - PADDING * 3;
		var editBox = new EditBox(Minecraft.getInstance().font, 0, 0,
				widgetW, ROW_HEIGHT - 4, Component.literal(label));
		editBox.setMaxLength(256);
		editBox.setValue(value);
		editBox.setResponder(onChange::accept);
		stringCompletionSuppliers.put(editBox, suggestions);
		listCompletionTargets.add(editBox);
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
		var btn = Button.builder(Component.literal(SpellEditorLocalization.t(text)), b -> onClick.run())
				.bounds(0, 0, widgetW, ROW_HEIGHT - 2).build();
		rows.add(new EditorRow("", btn, true));
	}

	/**
	 * Add a collapsible section header. When clicked, toggles visibility of subsequent rows
	 * at deeper depth levels until the next section at the same or lower depth.
	 * The button text color indicates nesting depth.
	 */
	private void addSectionHeader(String label) {
		boolean collapsed = collapsedSections.contains(label);
		String prefix = collapsed ? "\u25B6 " : "\u25BC ";
		int depthColor = getSectionHeaderColor(currentDepth);
		// Use an invisible placeholder widget instead of Button for compact 14px section headers
		var placeholder = createInvisiblePlaceholder(w - PADDING * 2, SECTION_HEADER_HEIGHT - 2);
		rows.add(new EditorRow(prefix + label, placeholder, true, -1, currentDepth, true, false));
	}

	/** Color for section header text based on depth. */
	private static int getSectionHeaderColor(int depth) {
		return switch (depth) {
			case 0 -> 0xFFFFCC44; // gold (top level)
			case 1 -> 0xFF66BBFF; // blue
			case 2 -> 0xFF66FF88; // green
			case 3 -> 0xFFFFAA44; // orange
			case 4 -> 0xFFCC77FF; // purple
			default -> 0xFFBBBBBB; // gray
		};
	}

	/** Check if a section is currently collapsed. */
	private boolean isSectionCollapsed(String label) {
		return collapsedSections.contains(label);
	}

	private void addInlineRow(String text, Runnable onDelete) {
		int deleteW = 20;
		var btn = Button.builder(Component.literal(SpellEditorLocalization.t("[x]")), b -> onDelete.run())
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

		int triggerRowY = y + getRowY(dropdown.triggerRowIndex()) - scrollOffset;
		int dropdownX = x + LABEL_WIDTH + PADDING * 2;
		int dropdownW = w - LABEL_WIDTH - PADDING * 3;
		if (dropdownW < 20) dropdownW = 20;

		int triggerRowH = getRowHeight(dropdown.triggerRowIndex());
		int dropdownY = triggerRowY + triggerRowH;
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
			int rowY = y + getRowY(i) - scrollOffset;
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
			guiGraphics.drawString(font, SpellEditorLocalization.t("Add Action"), x + PADDING, y + PADDING + 2, 0xFFFFCC44, false);
			for (int i = 0; i < rows.size(); i++) {
				int rowY = y + getRowY(i) - scrollOffset;
				int rowH = getRowHeight(i);
				rows.get(i).widget().visible = rowY >= y && rowY + rowH <= y + h;
			}
			renderScrollbar(guiGraphics);
			if (renderDropdown && dropdown != null) {
				this.renderDropdown(guiGraphics, mouseX, mouseY);
			}
			return;
		}

		// Title
		String title = currentAction == null ? SpellEditorLocalization.t("Select an action") : actionTypeName(currentAction);
		guiGraphics.drawString(font, title, x + PADDING, y + PADDING + 2, 0xFFFFCC44, false);

		if (currentAction == null) {
			guiGraphics.drawString(font, SpellEditorLocalization.t("Click an action in"), x + PADDING, y + 30, 0xFF888888, false);
			guiGraphics.drawString(font, SpellEditorLocalization.t("the list below to"), x + PADDING, y + 42, 0xFF888888, false);
			guiGraphics.drawString(font, SpellEditorLocalization.t("edit its properties"), x + PADDING, y + 54, 0xFF888888, false);
			return;
		}

		// Disable/Enable + Delete buttons (top right)
		boolean isDisabled = currentAction instanceof SpellActions.DisabledAction;
		String toggleText = SpellEditorLocalization.t(isDisabled ? "[Enable]" : "[Disable]");
		String deleteText = SpellEditorLocalization.t("[Delete]");
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
			guiGraphics.drawString(font, SpellEditorLocalization.t("Read-only action"), x + PADDING, y + 30, 0xFF888888, false);
			return;
		}

		// Row labels
		String overrideTooltipText = null;
		for (int i = 0; i < rows.size(); i++) {
			int rowY = y + getRowY(i) - scrollOffset;
			int rowH = getRowHeight(i);
			var row = rows.get(i);
			boolean visible = rowY >= y && rowY + rowH <= y + h;
			// Section header placeholders are always invisible; rendering is handled directly
			row.widget().visible = visible && !row.sectionHeader();
			if (visible) {
				if (row.sectionHeader()) {
					// Draw 1px separator line above section header at 50% opacity of section text color
					int sectionColor = getSectionHeaderColor(row.depth());
					int separatorColor = (sectionColor & 0x00FFFFFF) | 0x80000000; // 50% opacity
					guiGraphics.fill(x + PADDING, rowY, x + w - PADDING, rowY + 1, separatorColor);
					// Draw section header text (includes ▶/▼ prefix) colored by depth
					guiGraphics.drawString(font, SpellEditorLocalization.t(row.label()), x + PADDING, rowY + 3, sectionColor, false);
				} else if (!row.fullWidth() && !row.label().isEmpty()) {
					String rowLabel = SpellEditorLocalization.t(row.label());
					if (row.overridden()) {
						// Overridden row: reduced opacity (50% alpha) and strikethrough
						int labelColor = 0x80BBBBBB; // ~50% opacity
						int labelX = x + PADDING;
						int labelY = rowY + 4;
						guiGraphics.drawString(font, rowLabel, labelX, labelY, labelColor, false);
						// Draw 1px strikethrough line through the middle of the text
						int textWidth = font.width(rowLabel);
						int strikeY = labelY + font.lineHeight / 2;
						guiGraphics.fill(labelX, strikeY, labelX + textWidth, strikeY + 1, labelColor);
						// Check if mouse is hovering over the label area for tooltip
						if (mouseX >= labelX && mouseX < labelX + textWidth
								&& mouseY >= rowY && mouseY < rowY + rowH) {
							overrideTooltipText = MoverOverrideResolver.getTooltip(getCurrentMover());
						}
					} else {
						guiGraphics.drawString(font, rowLabel, x + PADDING, rowY + 4, 0xFFBBBBBB, false);
					}
				}
			}
		}

		// Scrollbar for content area
		renderScrollbar(guiGraphics);

		// Render override tooltip on top of other content (but below dropdown)
		if (overrideTooltipText != null && !overrideTooltipText.isEmpty()) {
			guiGraphics.renderTooltip(font, Component.literal(SpellEditorLocalization.t(overrideTooltipText)), mouseX, mouseY);
		}

		// Dropdown overlay (rendered last, on top of everything)
		if (renderDropdown && dropdown != null) {
			doRenderDropdown(guiGraphics, mouseX, mouseY);
		}
	}

	/** Returns a solid color for the depth indicator bar on the left edge. */
	@SuppressWarnings("unused")
	private static int getDepthBarColor(int depth) {
		return switch (depth) {
			case 1 -> 0xFF4488CC;
			case 2 -> 0xFF44CC66;
			case 3 -> 0xFFCC8844;
			case 4 -> 0xFF9944CC;
			default -> 0xFF888888;
		};
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

		renderStringDropdownArrows(guiGraphics, mouseX, mouseY);
		if (dropdown != null) {
			doRenderDropdown(guiGraphics, mouseX, mouseY);
		}
		doRenderStringCompletion(guiGraphics, mouseX, mouseY);
		doRenderExprCompletion(guiGraphics, mouseX, mouseY);
	}

	private void renderStringDropdownArrows(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (stringCompletionSuppliers.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 210);
		for (EditBox editBox : stringCompletionSuppliers.keySet()) {
			if (!editBox.visible) {
				continue;
			}
			int arrowX = editBox.getX() + editBox.getWidth() - STRING_DROPDOWN_W;
			int arrowY = editBox.getY() + 1;
			int arrowH = editBox.getHeight() - 2;
			boolean hovered = mouseX >= arrowX && mouseX < editBox.getX() + editBox.getWidth()
					&& mouseY >= editBox.getY() && mouseY < editBox.getY() + editBox.getHeight();
			guiGraphics.fill(arrowX, arrowY, editBox.getX() + editBox.getWidth() - 1,
					arrowY + arrowH, hovered ? 0x66556688 : 0x44334455);
			guiGraphics.drawString(font, "\u25BE", arrowX + 4, editBox.getY() + 4,
					hovered ? 0xFFFFDD66 : 0xFFBBBBCC, false);
		}
		guiGraphics.pose().popPose();
	}

	private EditBox getStringDropdownTarget(double mouseX, double mouseY) {
		for (EditBox editBox : stringCompletionSuppliers.keySet()) {
			if (!editBox.visible) {
				continue;
			}
			int arrowX = editBox.getX() + editBox.getWidth() - STRING_DROPDOWN_W;
			if (mouseX >= arrowX && mouseX < editBox.getX() + editBox.getWidth()
					&& mouseY >= editBox.getY() && mouseY < editBox.getY() + editBox.getHeight()) {
				return editBox;
			}
		}
		return null;
	}

	private EditBox getListCompletionTarget(double mouseX, double mouseY) {
		for (EditBox editBox : listCompletionTargets) {
			if (!editBox.visible) {
				continue;
			}
			if (mouseX >= editBox.getX() && mouseX < editBox.getX() + editBox.getWidth()
					&& mouseY >= editBox.getY() && mouseY < editBox.getY() + editBox.getHeight()) {
				return editBox;
			}
		}
		return null;
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
		// Handle string completion overlay
		if (stringCompletionItems != null) {
			if (stringCompletionTarget == null) {
				closeStringCompletion();
				return true;
			}
			if (button == 0) {
				int cx = stringCompletionTarget.getX();
				int cy = stringCompletionTarget.getY() + stringCompletionTarget.getHeight();
				int cw = Math.max(stringCompletionTarget.getWidth(), 120);
				int itemH = DROPDOWN_ITEM_H;
				int itemCount = stringCompletionItems.length;
				int totalH = Math.min(itemCount * itemH, DROPDOWN_MAX_VISIBLE * itemH);
				if (cy + totalH > y + h) totalH = y + h - cy;
				int visibleItems = Math.max(1, totalH / itemH);
				int scrollbarW = itemCount > visibleItems ? 6 : 0;
				int contentW = cw - scrollbarW;

				if (mouseX >= cx && mouseX < cx + contentW && mouseY >= cy && mouseY < cy + totalH) {
					int idx = (int) ((mouseY - cy) / itemH) + stringCompletionScrollOffset;
					if (idx >= 0 && idx < itemCount) {
						stringCompletionHoverIndex = idx;
						applyStringCompletion();
						return true;
					}
				}
				if (listCompletionTargets.contains(stringCompletionTarget)
						&& mouseX >= stringCompletionTarget.getX()
						&& mouseX < stringCompletionTarget.getX() + stringCompletionTarget.getWidth()
						&& mouseY >= stringCompletionTarget.getY()
						&& mouseY < stringCompletionTarget.getY() + stringCompletionTarget.getHeight()) {
					return false;
				}
				closeStringCompletion();
				return true;
			}
			return true;
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

		if (button == 0) {
			EditBox listTarget = getListCompletionTarget(mouseX, mouseY);
			if (listTarget != null) {
				openStringDropdown(listTarget);
				return false;
			}
			EditBox stringDropdownTarget = getStringDropdownTarget(mouseX, mouseY);
			if (stringDropdownTarget != null) {
				openStringDropdown(stringDropdownTarget);
				return true;
			}
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

		// Section header click detection
		if (isMouseOver(mouseX, mouseY)) {
			for (int i = 0; i < rows.size(); i++) {
				EditorRow row = rows.get(i);
				if (!row.sectionHeader()) continue;
				int rowY = y + getRowY(i) - scrollOffset;
				int rowH = getRowHeight(i);
				if (mouseY >= rowY && mouseY < rowY + rowH
						&& mouseX >= x + PADDING && mouseX < x + w - PADDING) {
					// Extract section label without the collapse indicator prefix (▶ or ▼ + space)
					String fullLabel = row.label();
					String sectionLabel = fullLabel.length() > 2 ? fullLabel.substring(2) : fullLabel;
					// Toggle collapsed state
					if (collapsedSections.contains(sectionLabel)) {
						collapsedSections.remove(sectionLabel);
					} else {
						collapsedSections.add(sectionLabel);
					}
					// Rebuild panel to reflect new collapsed/expanded state
					refreshCurrentView();
					return true;
				}
			}
		}

		Font font = Minecraft.getInstance().font;

		// Handle [Disable]/[Enable] button
		boolean isDisabled = currentAction instanceof SpellActions.DisabledAction;
		String toggleText = SpellEditorLocalization.t(isDisabled ? "[Enable]" : "[Disable]");
		String deleteText = SpellEditorLocalization.t("[Delete]");
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
		int totalHeight = PADDING + ROW_HEIGHT; // title row
		for (int i = 0; i < rows.size(); i++) {
			totalHeight += getRowHeight(i);
		}
		return Math.max(0, totalHeight - h);
	}

	/** Row height depends on whether it's a section header (14px) or normal row (20px). */
	private int getRowHeight(int rowIndex) {
		return rows.get(rowIndex).sectionHeader() ? SECTION_HEADER_HEIGHT : ROW_HEIGHT;
	}

	/** Cumulative Y position for row i, accounting for variable row heights. */
	private int getRowY(int rowIndex) {
		int cumY = PADDING + ROW_HEIGHT; // title row
		for (int i = 0; i < rowIndex; i++) {
			cumY += getRowHeight(i);
		}
		return cumY;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (stringCompletionItems != null) {
			int visible = getStringCompletionVisibleItems();
			int maxScroll = Math.max(0, stringCompletionItems.length - visible);
			stringCompletionScrollOffset = Math.max(0, Math.min(maxScroll,
					stringCompletionScrollOffset - (int) (delta * 3)));
			return true;
		}
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

		if (stringCompletionItems != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				closeStringCompletion();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER) {
				applyStringCompletion();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP) {
				if (stringCompletionHoverIndex > 0) stringCompletionHoverIndex--;
				ensureStringCompletionHoverVisible();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_DOWN) {
				if (stringCompletionHoverIndex < stringCompletionItems.length - 1) stringCompletionHoverIndex++;
				ensureStringCompletionHoverVisible();
				return true;
			}
			closeStringCompletion();
			return false;
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

		if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_TAB ||
				keyCode == GLFW.GLFW_KEY_SPACE && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
			if (Minecraft.getInstance().screen != null &&
					Minecraft.getInstance().screen.getFocused() instanceof EditBox editBox &&
					stringCompletionSuppliers.containsKey(editBox)) {
				return openStringCompletion(editBox);
			}
		}

		return false;
	}

	/**
	 * Called from SpellPreviewScreen when Tab is pressed in an EditBox.
	 */
	public boolean handleTabCompletion(EditBox editBox) {
		if (stringCompletionSuppliers.containsKey(editBox)) {
			return openStringCompletion(editBox);
		}
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
			Map.entry("erase_enemy_danmaku", "Erase Enemy Danmaku"),
			Map.entry("play_sound", "Play Sound"),
			Map.entry("force_phase", "Force Phase"),
			Map.entry("force_spell", "Force Spell"),
			Map.entry("sequence", "Sequence"),
			Map.entry("confine_target", "Confine Target"),
			Map.entry("set_entity_flag", "Set Entity Flag"),
			Map.entry("ysm_render", "YSM Render"),
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
			if (name != null) return SpellEditorLocalization.t(name);
			return SpellEditorLocalization.t(id);
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

	/**
	 * Creates a minimal invisible widget used as a placeholder for section header rows.
	 * Does not render anything but occupies space for layout and hit-testing purposes.
	 * Section header text is rendered directly by the panel's render method.
	 */
	private static AbstractWidget createInvisiblePlaceholder(int width, int height) {
		var btn = Button.builder(Component.empty(), b -> {})
				.bounds(0, 0, width, height).build();
		btn.active = false;
		return btn;
	}

	private record EditorRow(String label, AbstractWidget widget, boolean fullWidth, int customWidgetW, int depth, boolean sectionHeader, boolean overridden) {
		EditorRow(String label, AbstractWidget widget) {
			this(label, widget, false, -1, 0, false, false);
		}
		EditorRow(String label, AbstractWidget widget, boolean fullWidth) {
			this(label, widget, fullWidth, -1, 0, false, false);
		}
		EditorRow(String label, AbstractWidget widget, boolean fullWidth, int customWidgetW) {
			this(label, widget, fullWidth, customWidgetW, 0, false, false);
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

	// --- String completion ---

	private boolean openStringCompletion(EditBox editBox) {
		return openStringOptions(editBox, true);
	}

	private boolean openStringDropdown(EditBox editBox) {
		return openStringOptions(editBox, false);
	}

	private boolean openStringOptions(EditBox editBox, boolean filterByPrefix) {
		var supplier = stringCompletionSuppliers.get(editBox);
		if (supplier == null) {
			return false;
		}
		String text = editBox.getValue();
		int cursor = editBox.getCursorPosition();
		int tokenStart = filterByPrefix ? stringTokenStart(text, cursor) : 0;
		int tokenEnd = filterByPrefix ? cursor : text.length();
		String prefix = filterByPrefix ? text.substring(tokenStart, cursor).toLowerCase(java.util.Locale.ROOT) : "";
		java.util.LinkedHashSet<String> matches = new java.util.LinkedHashSet<>();
		List<String> options = supplier.get();
		if (options == null) {
			return false;
		}
		for (String option : options) {
			if (option != null && !option.isBlank() && option.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
				matches.add(option);
			}
		}
		for (String option : options) {
			if (option != null && !option.isBlank() && option.toLowerCase(java.util.Locale.ROOT).contains(prefix)) {
				matches.add(option);
			}
		}
		if (matches.isEmpty()) {
			return false;
		}
		stringCompletionItems = matches.toArray(new String[0]);
		stringCompletionHoverIndex = 0;
		stringCompletionTarget = editBox;
		stringCompletionInsertStart = tokenStart;
		stringCompletionInsertEnd = tokenEnd;
		stringCompletionScrollOffset = 0;
		return true;
	}

	private static int stringTokenStart(String text, int cursor) {
		int tokenStart = Math.min(cursor, text.length());
		while (tokenStart > 0) {
			char c = text.charAt(tokenStart - 1);
			if (Character.isWhitespace(c) || c == ',' || c == ';' || c == '|') {
				break;
			}
			tokenStart--;
		}
		return tokenStart;
	}

	private void applyStringCompletion() {
		EditBox target = stringCompletionTarget;
		String[] items = stringCompletionItems;
		int hoverIndex = stringCompletionHoverIndex;
		int insertStart = stringCompletionInsertStart;
		int insertEnd = stringCompletionInsertEnd;
		if (items == null || target == null) return;
		if (hoverIndex < 0 || hoverIndex >= items.length) return;
		String chosen = items[hoverIndex];
		String text = target.getValue();
		if (listCompletionTargets.contains(target)) {
			String newText = appendListCompletion(text, chosen);
			int newPos = newText.length();
			target.setValue(newText);
			target.setCursorPosition(newPos);
			try {
				var method = net.minecraft.client.gui.components.EditBox.class.getDeclaredMethod("setHighlightPos", int.class);
				method.setAccessible(true);
				method.invoke(target, newPos);
			} catch (Exception ignored) {}
			return;
		}
		int safeStart = Math.max(0, Math.min(insertStart, text.length()));
		int replaceEnd = insertEnd >= 0 ? Math.min(insertEnd, text.length()) : target.getCursorPosition();
		replaceEnd = Math.max(safeStart, Math.min(replaceEnd, text.length()));
		String newText = text.substring(0, safeStart) + chosen + text.substring(replaceEnd);
		int newPos = safeStart + chosen.length();
		target.setValue(newText);
		target.setCursorPosition(newPos);
		try {
			var method = net.minecraft.client.gui.components.EditBox.class.getDeclaredMethod("setHighlightPos", int.class);
			method.setAccessible(true);
			method.invoke(target, newPos);
		} catch (Exception ignored) {}
		closeStringCompletion();
	}

	private static String appendListCompletion(String text, String chosen) {
		String base = text == null ? "" : text.trim();
		while (base.endsWith(",") || base.endsWith(";") || base.endsWith("|")) {
			base = base.substring(0, base.length() - 1).trim();
		}
		if (base.isEmpty()) {
			return chosen;
		}
		return base + ", " + chosen;
	}

	private void closeStringCompletion() {
		stringCompletionItems = null;
		stringCompletionHoverIndex = -1;
		stringCompletionTarget = null;
		stringCompletionInsertStart = -1;
		stringCompletionInsertEnd = -1;
		stringCompletionScrollOffset = 0;
	}

	private int getStringCompletionVisibleItems() {
		if (stringCompletionItems == null || stringCompletionTarget == null) {
			return 1;
		}
		int itemCount = stringCompletionItems.length;
		int itemH = DROPDOWN_ITEM_H;
		int totalH = Math.min(itemCount * itemH, DROPDOWN_MAX_VISIBLE * itemH);
		int cy = stringCompletionTarget.getY() + stringCompletionTarget.getHeight();
		if (cy + totalH > y + h) {
			totalH = y + h - cy;
		}
		return Math.max(1, totalH / itemH);
	}

	private void ensureStringCompletionHoverVisible() {
		if (stringCompletionItems == null) {
			return;
		}
		int visible = getStringCompletionVisibleItems();
		if (stringCompletionHoverIndex < stringCompletionScrollOffset) {
			stringCompletionScrollOffset = stringCompletionHoverIndex;
		} else if (stringCompletionHoverIndex >= stringCompletionScrollOffset + visible) {
			stringCompletionScrollOffset = stringCompletionHoverIndex - visible + 1;
		}
		int maxScroll = Math.max(0, stringCompletionItems.length - visible);
		stringCompletionScrollOffset = Math.max(0, Math.min(maxScroll, stringCompletionScrollOffset));
	}

	private void doRenderStringCompletion(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (stringCompletionItems == null || stringCompletionTarget == null) return;
		Font font = Minecraft.getInstance().font;
		int itemCount = stringCompletionItems.length;
		int itemH = DROPDOWN_ITEM_H;
		int totalH = Math.min(itemCount * itemH, DROPDOWN_MAX_VISIBLE * itemH);
		int cx = stringCompletionTarget.getX();
		int cy = stringCompletionTarget.getY() + stringCompletionTarget.getHeight();
		int cw = Math.max(stringCompletionTarget.getWidth(), 120);
		if (cy + totalH > y + h) totalH = y + h - cy;
		if (totalH < itemH) return;
		int visibleItems = Math.max(1, totalH / itemH);
		int maxScroll = Math.max(0, itemCount - visibleItems);
		stringCompletionScrollOffset = Math.max(0, Math.min(maxScroll, stringCompletionScrollOffset));
		int scrollbarW = itemCount > visibleItems ? 6 : 0;
		int contentW = cw - scrollbarW;
		boolean listMode = listCompletionTargets.contains(stringCompletionTarget);
		Map<String, Integer> selectedCounts = listMode ? listTokenCounts(stringCompletionTarget.getValue()) : Map.of();

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(0, 0, 200);
		guiGraphics.fill(cx + 2, cy + 2, cx + cw + 2, cy + totalH + 2, 0x88000000);
		guiGraphics.fill(cx, cy, cx + cw, cy + totalH, 0xFF1a1a30);
		guiGraphics.fill(cx, cy, cx + cw, cy + 1, 0xFF666688);
		guiGraphics.fill(cx, cy + totalH - 1, cx + cw, cy + totalH, 0xFF666688);
		guiGraphics.fill(cx, cy, cx + 1, cy + totalH, 0xFF666688);
		guiGraphics.fill(cx + cw - 1, cy, cx + cw, cy + totalH, 0xFF666688);

		if (mouseX >= cx && mouseX < cx + contentW && mouseY >= cy && mouseY < cy + totalH) {
			int rawIdx = (mouseY - cy) / itemH + stringCompletionScrollOffset;
			if (rawIdx >= 0 && rawIdx < itemCount) stringCompletionHoverIndex = rawIdx;
		}

		int visCount = Math.min(itemCount - stringCompletionScrollOffset, visibleItems);
		for (int i = 0; i < visCount; i++) {
			int optIdx = i + stringCompletionScrollOffset;
			if (optIdx >= itemCount) break;
			int iy = cy + i * itemH;
			if (iy + itemH > cy + totalH) break;
			boolean hovered = optIdx == stringCompletionHoverIndex;
			if (hovered) guiGraphics.fill(cx + 1, iy, cx + contentW - 1, iy + itemH, 0x44FFFFFF);
			String option = stringCompletionItems[optIdx];
			int textX = cx + 4;
			int selectedCount = selectedCounts.getOrDefault(option.toLowerCase(java.util.Locale.ROOT), 0);
			if (listMode) {
				String marker = selectedCount <= 0 ? "" : selectedCount == 1 ? "\u2713" : "x" + selectedCount;
				if (!marker.isEmpty()) {
					guiGraphics.drawString(font, marker, cx + 4, iy + 4,
							hovered ? 0xFFFFDD66 : 0xFFFFCC88, false);
				}
				textX = cx + 24;
			}
			String display = listMode ? listCompletionDisplayName(option) : option;
			guiGraphics.drawString(font, display, textX, iy + 4,
					hovered ? 0xFFFFDD66 : 0xFFDDDDDD, false);
		}
		if (itemCount > visibleItems) {
			int sbX = cx + cw - scrollbarW;
			guiGraphics.fill(sbX, cy, sbX + scrollbarW, cy + totalH, 0x33FFFFFF);
			int trackH = totalH - 2;
			int thumbH = Math.max(10, trackH * visibleItems / itemCount);
			int thumbTravel = trackH - thumbH;
			if (thumbTravel > 0) {
				int thumbY = cy + 1 + thumbTravel * stringCompletionScrollOffset / Math.max(1, maxScroll);
				guiGraphics.fill(sbX + 1, thumbY, sbX + scrollbarW - 1, thumbY + thumbH, 0xAAAAAACC);
			}
		}
		guiGraphics.pose().popPose();
	}

	private Map<String, Integer> listTokenCounts(String text) {
		Map<String, Integer> ans = new HashMap<>();
		for (String token : splitList(text)) {
			String key = token.toLowerCase(java.util.Locale.ROOT);
			ans.put(key, ans.getOrDefault(key, 0) + 1);
		}
		return ans;
	}

	private String listCompletionDisplayName(String option) {
		if (!SpellEditorLocalization.isChinese()) {
			return option;
		}
		if (isBulletOption(option)) {
			return SpellEditorLocalization.danmakuBulletShapeName(option);
		}
		return SpellEditorLocalization.t(option.replace('_', ' '));
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
