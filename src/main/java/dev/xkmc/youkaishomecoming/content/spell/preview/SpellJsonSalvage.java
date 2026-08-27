package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器专用的抢救式解析。
 *
 * <p>严格解析下，符卡定义里任何一个动作片段坏掉都会让整份 JSON 被拒绝，节点面板完全
 * 建不起来 —— 用户只能对着一行错误信息在文本里猜是哪个节点出了问题。本类改为逐个
 * 动作解析：能解析的照常还原，解析不了的降级成惰性的
 * {@link SpellActions.BrokenAction} 占位节点，于是节点树照常建立，坏节点可以被选中、
 * 替换或删除。
 *
 * <p><b>范围限制</b>：抢救只覆盖动作节点层。若 {@code id} / {@code display} /
 * {@code entry_phase} / {@code phases} 这层骨架本身不可用，就没有树可建，
 * 调用方应回退到原本的硬错误路径。
 *
 * <p><b>边界</b>：本类只用于编辑器的 raw json 面板。数据包加载、
 * {@link SpellEditorSyncToServer} 的服务端解析与认证一律保持严格 —— 服务端权威
 * 不允许静默接受半解析的内容。
 */
@OnlyIn(Dist.CLIENT)
public final class SpellJsonSalvage {

	private static final Gson GSON = new Gson();

	/** 容器动作里承载子动作列表的字段名。 */
	private static final String[] CHILD_LISTS = {
			"if_true", "if_false", "body", "actions",
			"on_expiry", "on_trail", "on_hit_entity", "on_hit_block",
	};

	private static final String[] PHASE_SECTIONS = {"on_enter", "on_tick", "on_exit", "on_damage"};

	public record Result(SpellDefinition definition, int brokenCount, List<String> messages) {
		public Result {
			messages = List.copyOf(messages);
		}
	}

	private SpellJsonSalvage() {
	}

	/**
	 * 尝试抢救一份严格解析失败的符卡 JSON。
	 *
	 * @return 抢救结果；骨架不可用或抢救后仍无法解析时返回 {@code null}，
	 *         调用方应继续走硬错误路径。
	 */
	@Nullable
	public static Result salvage(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return null;
		}
		JsonObject root = json.getAsJsonObject().deepCopy();
		if (!root.has("phases") || !root.get("phases").isJsonObject()) {
			// 没有 phases 就没有节点树可建，交回硬错误路径。
			return null;
		}
		List<String> messages = new ArrayList<>();
		JsonObject phases = root.getAsJsonObject("phases");
		for (String phaseKey : List.copyOf(phases.keySet())) {
			JsonElement phaseElement = phases.get(phaseKey);
			if (phaseElement == null || !phaseElement.isJsonObject()) {
				continue;
			}
			JsonObject phase = phaseElement.getAsJsonObject();
			for (String section : PHASE_SECTIONS) {
				JsonElement list = phase.get(section);
				if (list != null && list.isJsonArray()) {
					phase.add(section, salvageList(list.getAsJsonArray(),
							phaseKey + "." + section, messages));
				}
			}
		}
		SpellDefinition definition = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, root)
				.resultOrPartial(ignored -> {})
				.orElse(null);
		if (definition == null) {
			// 骨架本身（id / display / entry_phase / transitions 等）仍然坏着。
			return null;
		}
		return new Result(definition, messages.size(), messages);
	}

	private static JsonArray salvageList(JsonArray list, String path, List<String> messages) {
		JsonArray out = new JsonArray();
		for (int i = 0; i < list.size(); i++) {
			out.add(salvageAction(list.get(i), path + "[" + i + "]", messages));
		}
		return out;
	}

	/**
	 * 先递归修好子列表再解析本节点，这样一个坏叶子不会连坐整棵子树 ——
	 * 父节点在孩子被替换成占位符后往往就能正常解析了。
	 */
	private static JsonElement salvageAction(JsonElement element, String path, List<String> messages) {
		if (element == null || !element.isJsonObject()) {
			return broken(element, path, "not a JSON object", messages);
		}
		JsonObject action = element.getAsJsonObject();
		for (String childList : CHILD_LISTS) {
			JsonElement child = action.get(childList);
			if (child != null && child.isJsonArray()) {
				action.add(childList, salvageList(child.getAsJsonArray(), path + "." + childList, messages));
			}
		}
		String[] error = new String[1];
		boolean parsed = SpellAction.CODEC.parse(JsonOps.INSTANCE, action)
				.resultOrPartial(msg -> error[0] = msg)
				.isPresent();
		if (parsed) {
			return action;
		}
		return broken(action, path, error[0], messages);
	}

	private static JsonElement broken(@Nullable JsonElement original, String path,
									  @Nullable String error, List<String> messages) {
		String type = original != null && original.isJsonObject()
				&& original.getAsJsonObject().has("type")
				&& original.getAsJsonObject().get("type").isJsonPrimitive()
				? original.getAsJsonObject().get("type").getAsString()
				: "unknown";
		String reason = error == null || error.isBlank() ? "unreadable" : error;
		messages.add(path + " (" + type + "): " + reason);

		JsonObject node = new JsonObject();
		node.addProperty("type", "broken");
		node.addProperty("original_type", type);
		// 原文以字符串保存，重新编码时不可能二次失败。
		node.addProperty("raw", original == null ? "" : GSON.toJson(original));
		node.addProperty("error", reason);
		return node;
	}

	/**
	 * 定义中是否含有抢救占位节点。Apply / Export 等出口据此拒绝执行。
	 * 复用 {@link SpecialNodeCounter} 已有的整树遍历，不另写一份。
	 */
	public static boolean containsBrokenNodes(@Nullable SpellDefinition definition) {
		return definition != null && SpecialNodeCounter.summarize(definition).brokenNodes() > 0;
	}

}
