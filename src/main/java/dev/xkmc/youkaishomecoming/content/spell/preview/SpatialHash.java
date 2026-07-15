package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spatial hash grid for fast AABB collision queries in the preview system.
 * Divides the world into cells of fixed size and bins entities by their position.
 * Queries only check the cell containing the target and its neighbors.
 * <p>
 * This reduces hit detection from O(N) to O(k) where k is the number of entities
 * in the local neighborhood (typically &lt; 100 even with 10,000+ total entities).
 */
public class SpatialHash {

	private static final float CELL_SIZE = 4.0f;
	private static final float INV_CELL_SIZE = 1.0f / CELL_SIZE;

	private final Map<Long, List<Entity>> grid = new HashMap<>();

	/**
	 * Clear all entries. Call at the beginning of each tick before re-inserting entities.
	 */
	public void clear() {
		grid.clear();
	}

	/**
	 * Insert an entity into the grid based on its current position.
	 */
	public void insert(Entity entity) {
		long key = cellKey(entity.position());
		grid.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
	}

	/**
	 * Query all entities in cells that overlap with the given AABB.
	 * Calls the consumer for each entity found in the relevant cells.
	 * The consumer is responsible for doing the precise AABB check.
	 *
	 * @param bounds   the query AABB (e.g., inflated target bounding box)
	 * @param consumer called for each candidate entity
	 */
	public void query(AABB bounds, Consumer<Entity> consumer) {
		int minX = cellCoord(bounds.minX);
		int maxX = cellCoord(bounds.maxX);
		int minY = cellCoord(bounds.minY);
		int maxY = cellCoord(bounds.maxY);
		int minZ = cellCoord(bounds.minZ);
		int maxZ = cellCoord(bounds.maxZ);

		for (int cx = minX; cx <= maxX; cx++) {
			for (int cy = minY; cy <= maxY; cy++) {
				for (int cz = minZ; cz <= maxZ; cz++) {
					long key = packKey(cx, cy, cz);
					var list = grid.get(key);
					if (list != null) {
						for (var entity : list) {
							consumer.accept(entity);
						}
					}
				}
			}
		}
	}

	/**
	 * Get the total number of cells currently in use (for diagnostics).
	 */
	public int cellCount() {
		return grid.size();
	}

	private static long cellKey(Vec3 pos) {
		return packKey(cellCoord(pos.x), cellCoord(pos.y), cellCoord(pos.z));
	}

	private static int cellCoord(double v) {
		return (int) Math.floor(v * INV_CELL_SIZE);
	}

	private static long packKey(int x, int y, int z) {
		// Pack 3 ints into a long: x in bits 42-63, y in bits 21-41, z in bits 0-20
		// Each component gets 21 bits, supporting range ±1,048,576 cells
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

}
