package dev.xkmc.youkaishomecoming.compat.llmcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime capability snapshot for an external authoring agent such as LLM Core.
 * It is deliberately generated from the loaded registries and policy table; the
 * skill files are guidance and must not be treated as the runtime contract.
 */
public record YhSpellCapabilityReport(
		String protocolVersion,
		String modVersion,
		List<String> actionTypes,
		List<String> conditionTypes,
		List<String> spellCardTypes,
		Map<String, String> capabilityPolicies,
		List<String> operatorOnlyCapabilities,
		List<String> deniedCapabilities
) {

	public static final String PROTOCOL_VERSION = "0.29.2";

	public YhSpellCapabilityReport {
		actionTypes = List.copyOf(actionTypes);
		conditionTypes = List.copyOf(conditionTypes);
		spellCardTypes = List.copyOf(spellCardTypes);
		capabilityPolicies = Map.copyOf(capabilityPolicies);
		operatorOnlyCapabilities = List.copyOf(operatorOnlyCapabilities);
		deniedCapabilities = List.copyOf(deniedCapabilities);
	}

	/** Build a snapshot from the registries and current server policy overrides. */
	public static YhSpellCapabilityReport current() {
		Map<String, String> policies = new LinkedHashMap<>();
		List<String> op = new ArrayList<>();
		List<String> denied = new ArrayList<>();
		for (SpellCapability capability : SpellCapability.values()) {
			SpellCapabilityPolicy policy = SpellCapabilityPolicies.currentPolicy(capability);
			policies.put(capability.id(), policy.name().toLowerCase(java.util.Locale.ROOT));
			if (policy == SpellCapabilityPolicy.OP_ONLY) op.add(capability.id());
			if (policy == SpellCapabilityPolicy.DENY) denied.add(capability.id());
		}
		op.sort(String::compareTo);
		denied.sort(String::compareTo);
		List<String> cards = new ArrayList<>();
		for (SpellCardType type : SpellCardType.values()) cards.add(type.getSerializedName());
		String version = PROTOCOL_VERSION;
		try {
			version = ModList.get().getModContainerById("youkaishomecoming")
					.map(container -> container.getModInfo().getVersion().toString())
					.orElse(PROTOCOL_VERSION);
		} catch (RuntimeException ignored) {
			// Headless analyzer/editor tests can run before Forge has initialized ModList.
		}
		return new YhSpellCapabilityReport(PROTOCOL_VERSION, version,
				new ArrayList<>(SpellActions.typeIds()), new ArrayList<>(SpellConditions.typeIds()),
				cards, policies, op, denied);
	}

	/** Stable JSON payload suitable for an LLM system prompt or adapter response. */
	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("protocol_version", protocolVersion);
		root.addProperty("mod_version", modVersion);
		addStrings(root, "actions", actionTypes);
		addStrings(root, "conditions", conditionTypes);
		addStrings(root, "spell_card_types", spellCardTypes);
		JsonObject policies = new JsonObject();
		capabilityPolicies.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> policies.addProperty(entry.getKey(), entry.getValue()));
		root.add("capability_policies", policies);
		addStrings(root, "operator_only_capabilities", operatorOnlyCapabilities);
		addStrings(root, "denied_capabilities", deniedCapabilities);
		return root;
	}

	private static void addStrings(JsonObject root, String key, List<String> values) {
		JsonArray array = new JsonArray();
		values.stream().sorted(Comparator.naturalOrder()).forEach(array::add);
		root.add(key, array);
	}
}
