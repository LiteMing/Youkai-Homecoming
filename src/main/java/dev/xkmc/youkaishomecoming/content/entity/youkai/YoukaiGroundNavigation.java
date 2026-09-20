package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

/** Shared by native goals and externally requested ground paths; flying is unchanged. */
public class YoukaiGroundNavigation extends GroundPathNavigation {

	public YoukaiGroundNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected PathFinder createPathFinder(int maxVisitedNodes) {
		nodeEvaluator = new YoukaiWalkNodeEvaluator();
		nodeEvaluator.setCanPassDoors(true);
		return new PathFinder(nodeEvaluator, maxVisitedNodes);
	}

	static class YoukaiWalkNodeEvaluator extends WalkNodeEvaluator {

		/**
		 * Vanilla accepts a neighbour based on the path type of the destination
		 * node.  An upright open trapdoor can sit in the side of that one-block
		 * corridor, however, so neither endpoint is necessarily the trapdoor's
		 * block position.  Reject the edge when the mob-sized swept corridor
		 * intersects one of our structural obstacles.
		 */
		@Override
		protected Node findAcceptedNode(int x, int y, int z, int jumpSize, double nodeHeight,
				Direction travelDirection, BlockPathTypes currentType) {
			if (edgeHitsGroundObstacle(x, y, z, travelDirection)) {
				return null;
			}
			return super.findAcceptedNode(x, y, z, jumpSize, nodeHeight, travelDirection, currentType);
		}

		@Override
		protected boolean isDiagonalValid(Node current, Node eastWest, Node northSouth, Node diagonal) {
			return super.isDiagonalValid(current, eastWest, northSouth, diagonal)
					&& (diagonal == null || !edgeHitsGroundObstacle(current.x, current.y, current.z,
						diagonal.x, diagonal.y, diagonal.z));
		}

		@Override
		public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			// BLOCKED alone still permits vanilla's one-block step-up search.
			// Reject the node above as well: neither box lids nor upright panels
			// are intended footholds. This changes routes, not block collision.
			if (isGroundObstacle(level.getBlockState(pos))
					|| isGroundObstacle(level.getBlockState(pos.below()))) {
				return BlockPathTypes.BLOCKED;
			}
			return super.getBlockPathType(level, x, y, z);
		}

		private boolean isGroundObstacle(BlockState state) {
			return state.is(YHBlocks.DONATION_BOX.get())
					|| state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
		}

		private boolean edgeHitsGroundObstacle(int targetX, int targetY, int targetZ, Direction direction) {
			int sourceX = targetX - direction.getStepX();
			int sourceZ = targetZ - direction.getStepZ();
			return edgeHitsGroundObstacle(sourceX, targetY, sourceZ, targetX, targetY, targetZ);
		}

		private boolean edgeHitsGroundObstacle(int sourceX, int sourceY, int sourceZ,
				int targetX, int targetY, int targetZ) {
			float halfWidth = mob.getBbWidth() * 0.5F + 0.04F;
			AABB corridor = new AABB(
				Math.min(sourceX, targetX) + 0.5D - halfWidth,
				Math.min(sourceY, targetY),
				Math.min(sourceZ, targetZ) + 0.5D - halfWidth,
				Math.max(sourceX, targetX) + 0.5D + halfWidth,
				Math.max(sourceY, targetY) + mob.getBbHeight(),
				Math.max(sourceZ, targetZ) + 0.5D + halfWidth);
			int minX = (int) Math.floor(corridor.minX) - 1;
			int maxX = (int) Math.floor(corridor.maxX) + 1;
			int minY = (int) Math.floor(corridor.minY) - 1;
			int maxY = (int) Math.floor(corridor.maxY) + 1;
			int minZ = (int) Math.floor(corridor.minZ) - 1;
			int maxZ = (int) Math.floor(corridor.maxZ) + 1;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int blockX = minX; blockX <= maxX; blockX++) {
				for (int blockY = minY; blockY <= maxY; blockY++) {
					for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
						pos.set(blockX, blockY, blockZ);
						BlockState state = level.getBlockState(pos);
						if (!isGroundObstacle(state)) continue;
						var shape = state.getCollisionShape(level, pos);
						for (AABB box : shape.toAabbs()) {
							if (box.move(blockX, blockY, blockZ).intersects(corridor)) return true;
						}
					}
				}
			}
			return false;
		}
	}
}
