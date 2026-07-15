package dev.xkmc.youkaishomecoming.content.spell.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 已点赞符卡的磁盘持久化存储。
 * 将已点赞的符卡 UUID 列表和点赞计数保存到
 * {@code config/youkaishomecoming_liked_spells.json}，
 * 使点赞状态在游戏重启后依然保留。
 *
 * <p>JSON 结构：
 * <pre>
 * {
 *   "liked": ["uuid1", "uuid2"],
 *   "like_counts": { "uuid1": 5, "uuid2": 10 }
 * }
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class LikedSpellsStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("SpellMarket/LikedSpells");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String CONFIG_FILE = "config/youkaishomecoming_liked_spells.json";

	private static final Set<String> likedSpells = new HashSet<>();
	private static final Map<String, Integer> likeCounts = new HashMap<>();
	private static boolean loaded = false;

	/**
	 * 从磁盘加载已点赞符卡数据。仅在首次访问时加载，之后使用内存缓存。
	 */
	public static void load() {
		if (loaded) return;
		loaded = true;
		File file = getConfigFile();
		if (!file.exists()) return;
		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			if (json.has("liked")) {
				for (var elem : json.getAsJsonArray("liked")) {
					likedSpells.add(elem.getAsString());
				}
			}
			if (json.has("like_counts")) {
				JsonObject counts = json.getAsJsonObject("like_counts");
				for (var entry : counts.entrySet()) {
					likeCounts.put(entry.getKey(), entry.getValue().getAsInt());
				}
			}
			LOGGER.info("Loaded {} liked spells from disk", likedSpells.size());
		} catch (Exception e) {
			LOGGER.error("Failed to load liked spells file", e);
		}
	}

	/**
	 * 将当前状态保存到磁盘。
	 */
	public static void save() {
		try {
			File file = getConfigFile();
			file.getParentFile().mkdirs();
			JsonObject json = new JsonObject();
			var likedArray = new com.google.gson.JsonArray();
			for (String uuid : likedSpells) {
				likedArray.add(uuid);
			}
			json.add("liked", likedArray);
			JsonObject counts = new JsonObject();
			for (var entry : likeCounts.entrySet()) {
				counts.addProperty(entry.getKey(), entry.getValue());
			}
			json.add("like_counts", counts);
			try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to save liked spells file", e);
		}
	}

	/**
	 * 标记一个符卡为已点赞。
	 */
	public static void add(String uuid) {
		likedSpells.add(uuid);
		save();
	}

	/**
	 * 标记一个符卡为已点赞并更新其点赞计数。
	 */
	public static void add(String uuid, int count) {
		likedSpells.add(uuid);
		likeCounts.put(uuid, count);
		save();
	}

	/**
	 * 更新符卡的点赞计数（不改变已点赞状态）。
	 */
	public static void setLikeCount(String uuid, int count) {
		likeCounts.put(uuid, count);
		save();
	}

	/**
	 * 移除符卡的点赞状态（取消点赞）。
	 */
	public static void remove(String uuid) {
		likedSpells.remove(uuid);
		likeCounts.remove(uuid);
		save();
	}

	/**
	 * 检查符卡是否已被点赞。
	 */
	public static boolean contains(String uuid) {
		load();
		return likedSpells.contains(uuid);
	}

	/**
	 * 获取符卡的本地缓存的点赞计数。
	 */
	public static int getLikeCount(String uuid) {
		load();
		return likeCounts.getOrDefault(uuid, -1);
	}

	/**
	 * 获取已点赞符卡 UUID 集合的副本。
	 */
	public static Set<String> getLikedSet() {
		load();
		return new HashSet<>(likedSpells);
	}

	/**
	 * 获取点赞计数的映射副本。
	 */
	public static Map<String, Integer> getLikeCountsMap() {
		load();
		return new HashMap<>(likeCounts);
	}

	private static File getConfigFile() {
		return new File(Minecraft.getInstance().gameDirectory, CONFIG_FILE);
	}
}
