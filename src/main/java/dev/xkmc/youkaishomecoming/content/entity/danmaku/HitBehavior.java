package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Controls what happens to a danmaku after it hits an entity or block.
 */
public enum HitBehavior implements StringRepresentable {
	/** Remove the danmaku on hit (default vanilla behavior). */
	DISCARD("discard"),
	/**
	 * Keep flying after hit.
	 * For entity hits this means "pierce and still deal damage";
	 * for block hits it means "do not discard on contact".
	 */
	CONTINUE("continue");

	public static final Codec<HitBehavior> CODEC = StringRepresentable.fromEnum(HitBehavior::values);

	private final String name;

	HitBehavior(String name) {
		this.name = name;
	}

	@Override
	@NotNull
	public String getSerializedName() {
		return name;
	}
}
