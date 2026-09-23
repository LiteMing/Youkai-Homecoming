package dev.xkmc.youkaishomecoming.content.entity.youkai.burst;

import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One active defeat burst. Position is frozen at the moment of defeat so the
 * dying youkai visually vanishes into the explosion, matching the original games.
 */
@OnlyIn(Dist.CLIENT)
public class DefeatBurst {

	public final Level level;
	public final double x, y, z;
	/** Base radius in blocks; all layer sizes scale from this. */
	public final float scale;
	/** Random base rotation so consecutive bursts don't look identical. */
	public final float seed;
	public final int start;

	public DefeatBurst(Level level, double x, double y, double z, float scale, float seed, int start) {
		this.level = level;
		this.x = x;
		this.y = y;
		this.z = z;
		this.scale = scale;
		this.seed = seed;
		this.start = start;
	}

}
