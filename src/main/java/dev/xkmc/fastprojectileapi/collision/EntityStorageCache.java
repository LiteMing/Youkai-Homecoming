package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.server.level.ServerLevel;

public class EntityStorageCache implements IEntityCache {

	private static EntityStorageCache CACHE = null;

	public static EntityStorageCache get(ServerLevel sl) {
		return get(sl, sl.getGameTime());
	}

	/**
	 * Pre-fetched gameTime overload for PC optimization.
	 * Avoids repeated sl.getGameTime() calls when gameTime is known.
	 */
	public static EntityStorageCache get(ServerLevel sl, long gameTime) {
		if (CACHE != null) {
			if (CACHE.sl == sl && CACHE.time == gameTime) {
				return CACHE;
			}
		}
		CACHE = new EntityStorageCache(sl, gameTime);
		return CACHE;
	}

	private final ServerLevel sl;
	private final long time;
	final FastMap<SectionCache> map = FastMapInit.createFastMap();

	public EntityStorageCache(ServerLevel sl) {
		this(sl, sl.getGameTime());
	}

	public EntityStorageCache(ServerLevel sl, long gameTime) {
		this.sl = sl;
		this.time = gameTime;
	}

	@Override
	public SectionCache get(int x, int y, int z) {
		var ans = map.get(x, y, z);
		if (ans == null) {
			ans = new SectionCache(sl, x, y, z);
			map.put(x, y, z, ans);
		}
		return ans;
	}

}
