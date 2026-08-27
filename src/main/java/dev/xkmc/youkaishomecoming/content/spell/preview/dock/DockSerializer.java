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
	/** 旧版单布局文件迁移后归属的模式键。 */
	private static final String LEGACY_MODE_KEY = "spell";

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
	 * 保存某个编辑器模式的布局。其余模式已存的布局原样保留。
	 *
	 * @param modeKey 模式键名，见 {@code EditorMode.key()}
	 */
	public static void saveLayout(String modeKey, DockNode root) {
		try {
			File file = getConfigFile();
			file.getParentFile().mkdirs();
			JsonObject modes = readModes();
			modes.add(modeKey, serialize(root));
			JsonObject json = new JsonObject();
			json.add("modes", modes);
			try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to save editor layout for mode {}", modeKey, e);
		}
	}

	public static boolean hasSavedLayout(String modeKey) {
		return readModeTree(modeKey) != null;
	}

	public static boolean savedLayoutContainsPanel(String modeKey, String panelId) {
		return containsPanelId(readModeTree(modeKey), panelId);
	}

	/**
	 * 读取配置文件里的模式表。
	 *
	 * <p>迁移：旧版本存的是单棵扁平布局树（根节点带 {@code "type"}），
	 * 整棵树归入符卡模式，魔法阵模式则回退到默认布局。
	 */
	private static JsonObject readModes() {
		File file = getConfigFile();
		if (!file.exists()) {
			return new JsonObject();
		}
		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonObject()) {
				return new JsonObject();
			}
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("modes") && obj.get("modes").isJsonObject()) {
				return obj.getAsJsonObject("modes");
			}
			if (obj.has("type")) {
				// 旧格式：单棵树 → 符卡模式
				JsonObject migrated = new JsonObject();
				migrated.add(LEGACY_MODE_KEY, obj);
				return migrated;
			}
			return new JsonObject();
		} catch (Exception e) {
			LOGGER.error("Failed to read editor layout file", e);
			return new JsonObject();
		}
	}

	@Nullable
	private static JsonObject readModeTree(String modeKey) {
		JsonElement tree = readModes().get(modeKey);
		return tree != null && tree.isJsonObject() ? tree.getAsJsonObject() : null;
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
	 * 从配置文件加载指定模式的布局。
	 *
	 * @param modeKey        模式键名
	 * @param panelMap       面板 ID → DockPanel 实例映射
	 * @param defaultLayout  加载失败时的默认布局供给
	 * @return 加载的根节点
	 */
	public static DockNode loadLayout(String modeKey, Map<String, DockPanel> panelMap,
			Function<Map<String, DockPanel>, DockNode> defaultLayout) {
		JsonObject tree = readModeTree(modeKey);
		if (tree == null) {
			return defaultLayout.apply(panelMap);
		}
		try {
			Set<String> usedIds = new HashSet<>();
			DockNode node = deserialize(tree, panelMap, usedIds);
			if (node == null) {
				LOGGER.warn("Invalid layout for mode {}, falling back to default", modeKey);
				return defaultLayout.apply(panelMap);
			}
			addMissingPanels(node, panelMap, usedIds);
			return node;
		} catch (Exception e) {
			LOGGER.error("Failed to load editor layout for mode {}, falling back to default", modeKey, e);
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
	 * 删除指定模式的布局（重置布局时调用）。其余模式的布局保留。
	 */
	public static void deleteLayout(String modeKey) {
		File file = getConfigFile();
		if (!file.exists()) {
			return;
		}
		JsonObject modes = readModes();
		if (modes.remove(modeKey) == null) {
			return;
		}
		try {
			if (modes.size() == 0) {
				file.delete();
				return;
			}
			JsonObject json = new JsonObject();
			json.add("modes", modes);
			try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to reset editor layout for mode {}", modeKey, e);
		}
	}

	private static File getConfigFile() {
		return new File(Minecraft.getInstance().gameDirectory, CONFIG_FILE);
	}
}
