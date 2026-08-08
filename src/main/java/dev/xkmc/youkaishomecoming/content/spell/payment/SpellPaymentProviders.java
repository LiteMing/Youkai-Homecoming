package dev.xkmc.youkaishomecoming.content.spell.payment;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider registry. Default providers are registered eagerly; scripts and
 * compatibility mods may register additional ones via {@link #register}.
 */
public final class SpellPaymentProviders {

	public static final ResourceLocation BOMB = new ResourceLocation("youkaishomecoming", "bomb");
	public static final ResourceLocation EXPERIENCE = new ResourceLocation("youkaishomecoming", "experience");
	public static final ResourceLocation POINTS = new ResourceLocation("youkaishomecoming", "points");
	public static final ResourceLocation LIFE = new ResourceLocation("youkaishomecoming", "life");

	private static final Map<ResourceLocation, SpellPaymentProvider> REGISTRY = new LinkedHashMap<>();

	static {
		register(new BombPaymentProvider());
		register(new ExperiencePaymentProvider());
		register(new PointsPaymentProvider());
		register(new LifePaymentProvider());
	}

	private SpellPaymentProviders() {
	}

	public static void register(SpellPaymentProvider provider) {
		REGISTRY.put(provider.id(), provider);
	}

	public static SpellPaymentProvider get(ResourceLocation id) {
		SpellPaymentProvider provider = REGISTRY.get(id);
		if (provider == null) {
			throw new IllegalArgumentException("Unknown spell payment provider: " + id);
		}
		return provider;
	}

	public static List<SpellPaymentProvider> all() {
		return List.copyOf(REGISTRY.values());
	}
}
