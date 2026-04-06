package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class UserCacheHolder {

	private UserMatrixCache cache = null;
	private long cachedGameTime = Long.MIN_VALUE;

	/**
	 * Set the cached gameTime once per tick to avoid repeated sl.getGameTime() calls.
	 */
	public void setGameTime(long gameTime) {
		cachedGameTime = gameTime;
	}

	public UserMatrixCache get(ServerLevel sl, LivingEntity user) {
		long time = cachedGameTime != Long.MIN_VALUE ? cachedGameTime : sl.getGameTime();
		if (cache != null) {
			if (cache.sl == sl && cache.time == time) {
				return cache;
			}
		}
		var bpos = SectionPos.of(user.blockPosition());
		cache = new UserMatrixCache(sl, bpos.x(), bpos.y(), bpos.z(), time);
		return cache;
	}

}
