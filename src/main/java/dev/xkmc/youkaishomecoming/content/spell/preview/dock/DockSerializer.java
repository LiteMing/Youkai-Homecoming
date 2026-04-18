package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * 布局持久化。将 Dock 分割树序列化为 JSON 并保存到
 * {@code config/youkaishomecoming_editor_layout.json}。
 *
 * <p>JSON 结构示例：
 * <pre>
 * {
 *   "type": "split",
 *   "horizontal": false,
 *   "ratio": 0.8,
 *   "first": {
 *     "type": "split",
 *     "horizontal": true,
 *     "ratio": 0.6,
 *     "first": { "type": "group", "panels": ["viewport"], "active": 0 },
 *     "second": { ... }
 *   },
 *   "second": { "type": "group", "panels": ["controls"], "active": 0 }
 * }
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class DockSerializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/DockLayout");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String CONFIG_FILE = "config/youkaishomecoming_editor_layout.json";

	// ---- 序列化 ----

	/**
	 * 将 DockNode 树序列化为 JsonObject。
	 */
	public static JsonObject serialize(DockNode node) {
		JsonObject obj = new JsonObject();
		if (node instanceof DockGroup group) {
			obj.addProperty("type", "group");
			JsonArray panels = new JsonArray();
			for (DockPanel panel : group.getPanels()) {
				panels.add(panel.dockId());
			}
			obj.add("panels", panels);
			obj.addProperty("active", group.getActiveIndex());
		} else if (node instanceof DockSplit split) {
			obj.addProperty("type", "split");
			obj.addProperty("horizontal", split.isHorizontal());
			obj.addProperty("ratio", split.getRatio());
			obj.add("first", serialize(split.getFirst()));
			obj.add("second", serialize(split.getSecond()));
		}
		return obj;
	}

	// ---- 反序列化 ----

	/**
	 * 从 JsonObject 反序列化为 DockNode 树。
	 *
	 * @param json      JSON 对象
	 * @param panelMap  面板 ID → DockPanel 实例的映射
	 * @param usedIds   已使用的面板 ID 集合（防止重复分配）
	 * @return 反序列化的节点，如果格式无效返回 null
	 */
	@Nullable
	public static DockNode deserialize(JsonObject json, Map<String, DockPanel> panelMap, Set<String> usedIds) {
		String type = json.has("type") ? json.get("type").getAsString() : "";
		if ("group".equals(type)) {
			return deserializeGroup(json, panelMap, usedIds);
		} else if ("split".equals(type)) {
			return deserializeSplit(json, panelMap, usedIds);
		}
		return null;
	}

	@Nullable
	private static DockGroup deserializeGroup(JsonObject json, Map<String, DockPanel> panelMap, Set<String> usedIds) {
		if (!json.has("panels")) return null;
		JsonArray panelIds = json.getAsJsonArray("panels");
		DockGroup group = new DockGroup();
		for (JsonElement elem : panelIds) {
			String id = elem.getAsString();
			DockPanel panel = panelMap.get(id);
			if (panel != null && !usedIds.contains(id)) {
				group.addPanel(panel);
				usedIds.add(id);
			}
		}
		if (group.isEmpty()) return null;
		int active = json.has("active") ? json.get("active").getAsInt() : 0;
		if (active >= 0 && active < group.getPanelCount()) {
			group.setActiveIndex(active);
		}
		return group;
	}

	@Nullable
	private static DockNode deserializeSplit(JsonObject json, Map<String, DockPanel> panelMap, Set<String> usedIds) {
		if (!json.has("first") || !json.has("second")) return null;
		boolean horizontal = json.has("horizontal") && json.get("horizontal").getAsBoolean();
		float ratio = json.has("ratio") ? json.get("ratio").getAsFloat() : 0.5f;

		// 防御性检查：确保子节点是 JsonObject
		JsonElement firstElem = json.get("first");
		JsonElement secondElem = json.get("second");
		if (!firstElem.isJsonObject() || !secondElem.isJsonObject()) return null;

		DockNode first = deserialize(firstElem.getAsJsonObject(), panelMap, usedIds);
		DockNode second = deserialize(secondElem.getAsJsonObject(), panelMap, usedIds);

		if (first == null && second == null) return null;
		if (first == null) return second;
		if (second == null) return first;

		return new DockSplit(horizontal, ratio, first, second);
	}

	// ---- 文件 I/O ----

	/**
	 * 保存布局到配置文件。
	 */
	public static void saveLayout(DockNode root) {
		try {
			File file = getConfigFile();
			file.getParentFile().mkdirs();
			JsonObject json = serialize(root);
			try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to save editor layout", e);
		}
	}

	public static boolean hasSavedLayout() {
		return getConfigFile().exists();
	}

	public static boolean savedLayoutContainsPanel(String panelId) {
		File file = getConfigFile();
		if (!file.exists()) {
			return false;
		}
		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);
			return containsPanelId(json, panelId);
		} catch (Exception e) {
			return false;
		}
	}

	public static DockNode loadLayout(@Nullable JsonObject json, Map<String, DockPanel> panelMap,
			Function<Map<String, DockPanel>, DockNode> defaultLayout) {
		if (json == null) {
			return defaultLayout.apply(panelMap);
		}
		try {
			Set<String> usedIds = new HashSet<>();
			DockNode node = deserialize(json, panelMap, usedIds);
			if (node == null) {
				LOGGER.warn("Invalid in-memory layout, falling back to default");
				return defaultLayout.apply(panelMap);
			}
			addMissingPanels(node, panelMap, usedIds);
			return node;
		} catch (Exception e) {
			LOGGER.error("Failed to load in-memory editor layout, falling back to default", e);
			return defaultLayout.apply(panelMap);
		}
	}

	/**
	 * 从配置文件加载布局。
	 *
	 * @param panelMap       面板 ID → DockPanel 实例映射
	 * @param defaultLayout  加载失败时的默认布局供给
	 * @return 加载的根节点
	 */
	public static DockNode loadLayout(Map<String, DockPanel> panelMap, Function<Map<String, DockPanel>, DockNode> defaultLayout) {
		File file = getConfigFile();
		if (!file.exists()) {
			return defaultLayout.apply(panelMap);
		}
		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			Set<String> usedIds = new HashSet<>();
			DockNode node = deserialize(json, panelMap, usedIds);
			if (node == null) {
				LOGGER.warn("Invalid layout file, falling back to default");
				return defaultLayout.apply(panelMap);
			}
			addMissingPanels(node, panelMap, usedIds);
			return node;
		} catch (Exception e) {
			LOGGER.error("Failed to load editor layout, falling back to default", e);
			return defaultLayout.apply(panelMap);
		}
	}

	private static void addMissingPanels(DockNode node, Map<String, DockPanel> panelMap, Set<String> usedIds) {
		for (Map.Entry<String, DockPanel> entry : panelMap.entrySet()) {
			if (!usedIds.contains(entry.getKey())) {
				LOGGER.warn("Panel '{}' missing from layout, adding to first group", entry.getKey());
				addPanelToFirstGroup(node, entry.getValue());
			}
		}
	}

	/**
	 * 将面板添加到树中的第一个 DockGroup。
	 */
	private static void addPanelToFirstGroup(DockNode node, DockPanel panel) {
		if (node instanceof DockGroup group) {
			group.addPanel(panel);
		} else if (node instanceof DockSplit split) {
			addPanelToFirstGroup(split.getFirst(), panel);
		}
	}

	private static boolean containsPanelId(JsonElement element, String panelId) {
		if (element == null || !element.isJsonObject()) {
			return false;
		}
		JsonObject object = element.getAsJsonObject();
		String type = object.has("type") ? object.get("type").getAsString() : "";
		if ("group".equals(type) && object.has("panels")) {
			for (JsonElement panel : object.getAsJsonArray("panels")) {
				if (panelId.equals(panel.getAsString())) {
					return true;
				}
			}
			return false;
		}
		if ("split".equals(type)) {
			return containsPanelId(object.get("first"), panelId) || containsPanelId(object.get("second"), panelId);
		}
		return false;
	}

	/**
	 * 删除配置文件（重置布局时调用）。
	 */
	public static void deleteLayout() {
		File file = getConfigFile();
		if (file.exists()) {
			file.delete();
		}
	}

	private static File getConfigFile() {
		return new File(Minecraft.getInstance().gameDirectory, CONFIG_FILE);
	}
}
