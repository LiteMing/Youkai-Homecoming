package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A finite, server-authoritative plan for boss spell-card health segments.
 *
 * <p>The break edge is the certification path: its health and duration are
 * summed into the preview. A timeout is a failed certification attempt, but
 * its edge is still checked so an invalid boss graph cannot hide a cycle or a
 * missing definition.</p>
 */
public record SpellHealthPlan(List<Segment> breakChain, int totalHealth, int totalDurationTicks,
		Map<ResourceLocation, SpellDefinition> definitions) {

	public SpellHealthPlan {
		breakChain = List.copyOf(breakChain);
		totalHealth = Math.max(0, totalHealth);
		totalDurationTicks = Math.max(0, totalDurationTicks);
		definitions = Map.copyOf(definitions);
	}

	public record Target(ResourceLocation spellId, ResourceLocation phaseId, boolean clearScreen) {
	}

	public record Segment(ResourceLocation spellId, ResourceLocation phaseId,
						  int health, int durationTicks,
						  @Nullable Target onTimeout, @Nullable Target onBreak) {
		public Segment {
			health = Math.max(1, health);
			durationTicks = Math.max(0, durationTicks);
		}
	}

	public SpellDefinition rootDefinition() {
		if (breakChain.isEmpty()) throw invalid("break chain is empty");
		return definitions.get(breakChain.get(0).spellId());
	}

	@Nullable
	public SpellDefinition resolve(ResourceLocation spellId) {
		return definitions.get(spellId);
	}

	private record Node(ResourceLocation spellId, ResourceLocation phaseId) {
	}

	private static final int MAX_SEGMENTS = 64;
	private static final int MAX_DEFINITIONS = 64;
	private static final int MAX_VALUE = 1_000_000;

	/** Returns empty when the definition does not use set_spell_health. */
	public static java.util.Optional<SpellHealthPlan> analyzeIfPresent(
			SpellDefinition root, Function<ResourceLocation, SpellDefinition> resolver) {
		if (!containsHealth(root)) return java.util.Optional.empty();
		return java.util.Optional.of(analyze(root, resolver));
	}

	/** Analyzes a definition that is expected to declare a finite health plan. */
	public static SpellHealthPlan analyze(SpellDefinition root,
			Function<ResourceLocation, SpellDefinition> resolver) {
		if (root == null || root.id == null) {
			throw invalid("health plan root definition is missing");
		}
		Map<ResourceLocation, SpellDefinition> definitions = new HashMap<>();
		SpellDefinition frozenRoot = copyDefinition(root);
		definitions.put(frozenRoot.id, frozenRoot);
		Map<Node, Segment> nodes = new HashMap<>();
		Set<Node> visiting = new HashSet<>();
		Set<Node> visited = new HashSet<>();
		Node start = new Node(root.id, root.entryPhase);
		visit(start, resolver, definitions, nodes, visiting, visited);
		// Export validates every declared health node in the discovered closure,
		// including phases not on the successful break chain.
		boolean expanded;
		do {
			int definitionCount = definitions.size();
			int visitedCount = visited.size();
			for (SpellDefinition definition : List.copyOf(definitions.values())) {
				for (PhaseDefinition phase : definition.phases.values()) {
					if (containsHealth(phase.onEnter) || containsHealth(phase.onTick)
							|| containsHealth(phase.onExit) || containsHealth(phase.onDamage)) {
						visit(new Node(definition.id, phase.id), resolver, definitions,
								nodes, visiting, visited);
					}
				}
			}
			expanded = definitions.size() != definitionCount || visited.size() != visitedCount;
		} while (expanded);

		List<Segment> chain = new ArrayList<>();
		Set<Node> chainVisited = new HashSet<>();
		Node current = start;
		long health = 0;
		long duration = 0;
		while (current != null) {
			if (!chainVisited.add(current)) {
				throw invalid("spell-health break chain contains a cycle at " + current.phaseId());
			}
			Segment segment = nodes.get(current);
			if (segment == null) {
				throw invalid("spell-health plan is incomplete at " + current.phaseId());
			}
			chain.add(segment);
			health = saturatedAdd(health, segment.health());
			duration = saturatedAdd(duration, segment.durationTicks());
			if (chain.size() > MAX_SEGMENTS) {
				throw invalid("spell-health plan exceeds " + MAX_SEGMENTS + " segments");
			}
			current = nodeOf(segment.onBreak());
		}
		if (duration > 1200) {
			throw invalid("total break-chain duration must be in [0, 1200] ticks");
		}
		return new SpellHealthPlan(chain, toInt(health), toInt(duration), definitions);
	}

	private static void visit(Node node, Function<ResourceLocation, SpellDefinition> resolver,
			Map<ResourceLocation, SpellDefinition> definitions,
			Map<Node, Segment> nodes, Set<Node> visiting, Set<Node> visited) {
		if (visited.contains(node)) return;
		if (visited.size() + visiting.size() >= MAX_SEGMENTS) {
			throw invalid("spell-health graph exceeds " + MAX_SEGMENTS + " segments");
		}
		if (!visiting.add(node)) {
			throw invalid("spell-health transition cycle detected at " + node.spellId() + ":" + node.phaseId());
		}
		SpellDefinition definition = resolveFrozen(node.spellId(), resolver, definitions);
		if (definition == null) {
			throw invalid("spell-health target spell is missing: " + node.spellId());
		}
		if (!node.spellId().equals(definition.id)) {
			throw invalid("spell-health resolver returned a mismatched definition for " + node.spellId());
		}
		PhaseDefinition phase = definition.getPhase(node.phaseId());
		if (phase == null) {
			throw invalid("spell-health target phase is missing: " + node.spellId() + ":" + node.phaseId());
		}
		SetSpellHealthAction health = findHealthAction(phase.onEnter,
				"phase " + node.spellId() + ":" + node.phaseId());
		if (containsHealth(phase.onTick) || containsHealth(phase.onExit)
				|| containsHealth(phase.onDamage)) {
			throw invalid("set_spell_health may only run from on_enter: "
					+ node.spellId() + ":" + node.phaseId());
		}
		if (health == null || health.mode() != SetSpellHealthAction.Mode.SET) {
			throw invalid("every reachable health phase must set spell health: "
					+ node.spellId() + ":" + node.phaseId());
		}
		if (!(health.health() instanceof NumberProviders.Constant hp)
				|| !(health.duration() instanceof NumberProviders.Constant duration)) {
			throw invalid("spell health and duration must be constants at "
					+ node.spellId() + ":" + node.phaseId());
		}
		int maxHealth = clamp(hp.value(), 1, MAX_VALUE, "health", node);
		int durationTicks = clamp(duration.value(), 0, 1200, "duration", node);
		if (!phase.transitions.isEmpty()) {
			throw invalid("health phase may not use ordinary transitions: "
					+ node.spellId() + ":" + node.phaseId());
		}
		if (containsFreeSwitch(phase.onEnter) || containsFreeSwitch(phase.onTick)
				|| containsFreeSwitch(phase.onExit) || containsFreeSwitch(phase.onDamage)) {
			throw invalid("force_phase/force_spell may only be completion targets in health phases: "
					+ node.spellId() + ":" + node.phaseId());
		}
		Target timeout = targetOf(health.onTimeout(), node, resolver, definitions);
		Target broken = targetOf(health.onBreak(), node, resolver, definitions);
		Segment segment = new Segment(node.spellId(), node.phaseId(), maxHealth,
				durationTicks, timeout, broken);
		nodes.put(node, segment);
		for (Target target : new Target[]{timeout, broken}) {
			Node next = nodeOf(target);
			if (next != null) {
				visit(next, resolver, definitions, nodes,
						visiting, visited);
			}
		}
		visiting.remove(node);
		visited.add(node);
	}

	@Nullable
	private static Target targetOf(java.util.Optional<SpellAction> action, Node source,
			Function<ResourceLocation, SpellDefinition> resolver,
			Map<ResourceLocation, SpellDefinition> definitions) {
		if (action == null || action.isEmpty()) return null;
		SpellAction value = action.get();
		Target target;
		if (value instanceof SpellActions.ForcePhase phase) {
			target = new Target(source.spellId(), phase.phaseId(), phase.clearScreen());
		} else if (value instanceof SpellActions.ForceSpell spell) {
			SpellDefinition targetDefinition = resolveFrozen(spell.spellId(), resolver, definitions);
			if (targetDefinition == null) {
				throw invalid("spell-health target spell is missing: " + spell.spellId());
			}
			target = new Target(spell.spellId(), targetDefinition.entryPhase, spell.clearScreen());
		} else {
			throw invalid("spell-health completion target must be force_phase or force_spell");
		}
		return target;
	}

	@Nullable
	private static Node nodeOf(@Nullable Target target) {
		return target == null ? null : new Node(target.spellId(), target.phaseId());
	}

	@Nullable
	private static SetSpellHealthAction findHealthAction(List<SpellAction> actions, String path) {
		SetSpellHealthAction found = null;
		for (SpellAction action : actions) {
			if (action instanceof SetSpellHealthAction health) {
				if (found != null) throw invalid("multiple set_spell_health nodes in " + path);
				found = health;
			} else if (action instanceof SpellActions.SequenceAction sequence) {
				SetSpellHealthAction nested = findHealthAction(sequence.actions(), path + "/sequence");
				if (nested != null) {
					if (found != null) throw invalid("multiple set_spell_health nodes in " + path);
					found = nested;
				}
			} else if (containsHealth(action)) {
				throw invalid("set_spell_health must be a direct on_enter node or sequence: " + path);
			}
		}
		return found;
	}

	private static boolean containsHealth(SpellDefinition definition) {
		for (PhaseDefinition phase : definition.phases.values()) {
			if (containsHealth(phase.onEnter) || containsHealth(phase.onTick)
					|| containsHealth(phase.onExit) || containsHealth(phase.onDamage)) return true;
		}
		return false;
	}

	private static boolean containsHealth(List<SpellAction> actions) {
		for (SpellAction action : actions) if (containsHealth(action)) return true;
		return false;
	}

	private static boolean containsHealth(SpellAction action) {
		if (action instanceof SetSpellHealthAction) return true;
		if (action instanceof SpellActions.ConditionalAction conditional) {
			return containsHealth(conditional.ifTrue()) || containsHealth(conditional.ifFalse());
		}
		if (action instanceof SpellActions.SequenceAction sequence) return containsHealth(sequence.actions());
		if (action instanceof SpellActions.RepeatAction repeat) return containsHealth(repeat.body());
		if (action instanceof DelayAction delay) return containsHealth(delay.body());
		if (action instanceof BurstAction burst) return containsHealth(burst.body());
		if (action instanceof SpellActions.DisabledAction) return false;
		return false;
	}

	private static boolean containsFreeSwitch(List<SpellAction> actions) {
		for (SpellAction action : actions) if (containsFreeSwitch(action)) return true;
		return false;
	}

	private static boolean containsFreeSwitch(SpellAction action) {
		if (action instanceof SpellActions.ForcePhase || action instanceof SpellActions.ForceSpell) return true;
		// Completion targets stored inside set_spell_health are the only allowed switches.
		if (action instanceof SetSpellHealthAction || action instanceof SpellActions.DisabledAction) return false;
		if (action instanceof SpellActions.ConditionalAction conditional) {
			return containsFreeSwitch(conditional.ifTrue()) || containsFreeSwitch(conditional.ifFalse());
		}
		if (action instanceof SpellActions.SequenceAction sequence) return containsFreeSwitch(sequence.actions());
		if (action instanceof SpellActions.RepeatAction repeat) return containsFreeSwitch(repeat.body());
		if (action instanceof DelayAction delay) return containsFreeSwitch(delay.body());
		if (action instanceof BurstAction burst) return containsFreeSwitch(burst.body());
		return false;
	}

	private static int clamp(double value, int min, int max, String label, Node node) {
		if (!Double.isFinite(value)) throw invalid("spell " + label + " is not finite at " + node.phaseId());
		if (value < min || value > max) {
			throw invalid("spell " + label + " must be in [" + min + ", " + max + "] at " + node.phaseId());
		}
		return Math.max(min, Math.min(max, (int) Math.round(value)));
	}

	private static long saturatedAdd(long a, long b) {
		return Math.min(Integer.MAX_VALUE, a + Math.max(0, b));
	}

	private static int toInt(long value) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
	}

	@Nullable
	private static SpellDefinition resolveFrozen(ResourceLocation spellId,
			Function<ResourceLocation, SpellDefinition> resolver,
			Map<ResourceLocation, SpellDefinition> definitions) {
		SpellDefinition existing = definitions.get(spellId);
		if (existing != null) return existing;
		SpellDefinition resolved = resolver.apply(spellId);
		if (resolved == null) return null;
		if (definitions.size() >= MAX_DEFINITIONS) {
			throw invalid("spell dependency closure exceeds " + MAX_DEFINITIONS + " definitions");
		}
		SpellDefinition frozen = copyDefinition(resolved);
		definitions.put(spellId, frozen);
		return frozen;
	}

	private static SpellDefinition copyDefinition(SpellDefinition definition) {
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.result().orElseThrow(() -> invalid("cannot freeze definition " + definition.id));
		return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
				.result().orElseThrow(() -> invalid("cannot restore frozen definition " + definition.id));
	}

	private static IllegalArgumentException invalid(String message) {
		return new IllegalArgumentException("Invalid spell-health plan: " + message);
	}
}
