package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

/**
 * Predefined danmaku damage types that can be configured per-action in the data-driven spell system.
 * <p>
 * Usage in {@code fire_danmaku} / {@code fire_laser}:
 * <pre>
 * {
 *   "type": "fire_danmaku",
 *   "damage_type": "abyssal",
 *   ...
 * }
 * </pre>
 * <p>
 * When set, the fired danmaku entity will carry this damage type override,
 * bypassing the normal {@code CardHolder.getDanmakuDamageSource()} resolution chain.
 * This allows data-driven spells to use different damage types per action without
 * needing a legacy SpellCard subclass.
 */
public enum DanmakuDamageType implements StringRepresentable {
	/** Standard danmaku damage. Bypasses armor and cooldown, tagged as magic. */
	DANMAKU("danmaku") {
		@Override
		public DamageSource resolve(IYHDanmaku danmaku) {
			return YHDamageTypes.danmaku(danmaku);
		}
	},
	/** Abyssal danmaku damage. Same as DANMAKU but also bypasses magic protection. */
	ABYSSAL("abyssal") {
		@Override
		public DamageSource resolve(IYHDanmaku danmaku) {
			return YHDamageTypes.abyssal(danmaku);
		}
	};

	public static final Codec<DanmakuDamageType> CODEC = StringRepresentable.fromEnum(DanmakuDamageType::values);

	private final String name;

	DanmakuDamageType(String name) {
		this.name = name;
	}

	@Override
	@NotNull
	public String getSerializedName() {
		return name;
	}

	/**
	 * Resolve this damage type into a concrete {@link DamageSource} for the given danmaku entity.
	 */
	public abstract DamageSource resolve(IYHDanmaku danmaku);
}
