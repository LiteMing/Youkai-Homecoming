package dev.xkmc.youkaishomecoming.content.spell.market;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端限流器
 * 防止频繁请求
 */
public class SpellMarketRateLimiter {

	private long lastUploadTime = 0;
	private final Map<String, Long> likedSpells = new HashMap<>();
	private long lastSearchTime = 0;

	private static final long UPLOAD_COOLDOWN_MS = 60_000; // 1分钟
	private static final long LIKE_COOLDOWN_MS = 1_000; // 1秒
	private static final long SEARCH_DEBOUNCE_MS = 500; // 500ms

	public boolean canUpload() {
		long now = System.currentTimeMillis();
		return (now - lastUploadTime) >= UPLOAD_COOLDOWN_MS;
	}

	public long getUploadCooldownRemaining() {
		long now = System.currentTimeMillis();
		long elapsed = now - lastUploadTime;
		return Math.max(0, UPLOAD_COOLDOWN_MS - elapsed) / 1000;
	}

	public void markUpload() {
		lastUploadTime = System.currentTimeMillis();
	}

	public boolean canLike(String uuid) {
		Long lastLike = likedSpells.get(uuid);
		if (lastLike == null) return true;
		long now = System.currentTimeMillis();
		return (now - lastLike) >= LIKE_COOLDOWN_MS;
	}

	public void markLike(String uuid) {
		likedSpells.put(uuid, System.currentTimeMillis());
	}

	public boolean canSearch() {
		long now = System.currentTimeMillis();
		return (now - lastSearchTime) >= SEARCH_DEBOUNCE_MS;
	}

	public void markSearch() {
		lastSearchTime = System.currentTimeMillis();
	}

}
