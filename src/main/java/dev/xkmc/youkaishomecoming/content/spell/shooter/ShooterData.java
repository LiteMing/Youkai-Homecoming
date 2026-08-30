package dev.xkmc.youkaishomecoming.content.spell.shooter;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ShooterData(
		int health, float damage, int life,
		@Nullable ResourceLocation circle,
		boolean targetable
) {

	public static final ResourceLocation DEFAULT_CIRCLE = YoukaisHomecoming.loc("test_spell_0");

	public static final ShooterData EMPTY = new ShooterData(20, 4, 100, DEFAULT_CIRCLE, true);

	public ShooterData(int health, float damage, int life) {
		this(health, damage, life, DEFAULT_CIRCLE, true);
	}

	public ShooterData(int health, float damage, int life, @Nullable ResourceLocation circle) {
		this(health, damage, life, circle, true);
	}
}
