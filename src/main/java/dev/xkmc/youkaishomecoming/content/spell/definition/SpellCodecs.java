package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * Shared Codec definitions used across spell action/config classes.
 */
public class SpellCodecs {

	public static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("x").forGetter(Vec3::x),
			Codec.DOUBLE.fieldOf("y").forGetter(Vec3::y),
			Codec.DOUBLE.fieldOf("z").forGetter(Vec3::z)
	).apply(i, Vec3::new));

	public static final Codec<DyeColor> DYE_COLOR_CODEC = Codec.STRING.xmap(
			s -> DyeColor.valueOf(s.toUpperCase()),
			c -> c.name().toLowerCase()
	);

	public static final Codec<YHDanmaku.Bullet> BULLET_CODEC = Codec.STRING.xmap(
			s -> YHDanmaku.Bullet.valueOf(s.toUpperCase()),
			b -> b.name().toLowerCase()
	);

	public static final Codec<YHDanmaku.Laser> LASER_CODEC = Codec.STRING.xmap(
			s -> YHDanmaku.Laser.valueOf(s.toUpperCase()),
			l -> l.name().toLowerCase()
	);

}
