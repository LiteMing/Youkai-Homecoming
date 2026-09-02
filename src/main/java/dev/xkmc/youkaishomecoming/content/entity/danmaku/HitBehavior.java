package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Controls what happens to a danmaku after its on-hit callback leaves the
 * collision unresolved. This fallback does not decide whether on_hit runs.
 */
public enum HitBehavior implements StringRepresentable {
	/** Remove the danmaku on hit (default vanilla behavior). */
	DISCARD("discard"),
	/** Trigger expiry immediately on hit, including afterExpiry behavior. */
	EXPIRE("expire"),
	/** Keep flying after hit — danmaku lives until lifetime expires and then triggers afterExpiry. */
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
