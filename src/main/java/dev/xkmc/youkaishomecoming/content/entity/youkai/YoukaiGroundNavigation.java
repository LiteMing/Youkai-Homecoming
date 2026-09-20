package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

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
	}
}
