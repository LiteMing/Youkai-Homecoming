package dev.xkmc.fastprojectileapi.collision;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.atomic.AtomicLongArray;

public class UserMatrixCache implements IEntityCache {

	private static final int R = 13;

	private final EntityStorageCache parent;
	final ServerLevel sl;
	final long time;
	private final SectionCache[][][] cache;
	private final int x0, y0, z0;
	private final AtomicBitSet preheated;

	public UserMatrixCache(ServerLevel sl, int x0, int y0, int z0) {
		this.time = sl.getGameTime();
		this.sl = sl;
		parent = EntityStorageCache.get(sl);
		this.x0 = x0;
		this.y0 = y0;
		this.z0 = z0;
		int d = R * 2 + 1;
		cache = new SectionCache[d][d][d];
		preheated = new AtomicBitSet(d * d * d);
	}

	public SectionCache get(int x, int y, int z) {
		int ix = x - x0 + R;
		int iy = y - y0 + R;
		int iz = z - z0 + R;
		if (ix >= 0 && ix < R * 2 + 1 && iy >= 0 && iy < R * 2 + 1 && iz >= 0 && iz < R * 2 + 1) {
			if (cache[ix][iy][iz] == null) {
				cache[ix][iy][iz] = parent.get(x, y, z);
			}
			return cache[ix][iy][iz];
		} else {
			return parent.get(x, y, z);
		}
	}

	@Override
	public SectionCache asyncGet(int x, int y, int z) {
		int ix = x - x0 + R;
		int iy = y - y0 + R;
		int iz = z - z0 + R;
		if (ix >= 0 && ix < R * 2 + 1 && iy >= 0 && iy < R * 2 + 1 && iz >= 0 && iz < R * 2 + 1) {
			return cache[ix][iy][iz];
		}
		return parent.asyncGet(x, y, z);
	}

	public void preheat(AABB aabb) {
		int x0 = (((int) aabb.minX) >> 4) - 1;
		int y0 = (((int) aabb.minY) >> 4) - 1;
		int z0 = (((int) aabb.minZ) >> 4) - 1;
		int x1 = (((int) aabb.maxX) >> 4) + 1;
		int y1 = (((int) aabb.maxY) >> 4) + 1;
		int z1 = (((int) aabb.maxZ) >> 4) + 1;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					markPreheat(x, y, z);
				}
			}
		}
	}

	private void markPreheat(int x, int y, int z) {
		int ix = x - x0 + R;
		int iy = y - y0 + R;
		int iz = z - z0 + R;
		if (ix >= 0 && ix < R * 2 + 1 && iy >= 0 && iy < R * 2 + 1 && iz >= 0 && iz < R * 2 + 1) {
			preheated.set(index(ix, iy, iz, R * 2 + 1));
		}
	}

	public void flushPreheat() {
		int d = R * 2 + 1;
		for (int ix = 0; ix < d; ix++) {
			for (int iy = 0; iy < d; iy++) {
				for (int iz = 0; iz < d; iz++) {
					int index = index(ix, iy, iz, d);
					if (preheated.get(index)) {
						get(x0 + ix - R, y0 + iy - R, z0 + iz - R);
					}
				}
			}
		}
	}

	private static int index(int ix, int iy, int iz, int d) {
		return (ix * d + iy) * d + iz;
	}

	private static final class AtomicBitSet {

		private final AtomicLongArray words;

		private AtomicBitSet(int size) {
			words = new AtomicLongArray((size + Long.SIZE - 1) / Long.SIZE);
		}

		private boolean get(int bit) {
			int wordIndex = bit >>> 6;
			long mask = 1L << (bit & 63);
			return (words.get(wordIndex) & mask) != 0L;
		}

		private void set(int bit) {
			int wordIndex = bit >>> 6;
			long mask = 1L << (bit & 63);
			while (true) {
				long current = words.get(wordIndex);
				if ((current & mask) != 0L) return;
				if (words.compareAndSet(wordIndex, current, current | mask)) return;
			}
		}

	}

}
