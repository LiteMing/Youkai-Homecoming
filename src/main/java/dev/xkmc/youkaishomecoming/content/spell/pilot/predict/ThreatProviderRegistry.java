package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered provider chain: first {@link ThreatProvider#supports(Entity)} wins.
 * Core is side-neutral; consumers assemble the list (preview may include T1,
 * server default T2+T3).
 */
public class ThreatProviderRegistry {

	private final List<ThreatProvider> providers = new ArrayList<>();

	public void register(ThreatProvider provider) {
		providers.add(provider);
	}

	@Nullable
	public Threat capture(Entity entity, int horizon) {
		if (entity == null) return null;
		for (ThreatProvider provider : providers) {
			if (provider.supports(entity)) {
				Threat result = provider.capture(entity, horizon);
				if (result != null) return result;
			}
		}
		return null;
	}

	public List<ThreatProvider> getProviders() {
		return Collections.unmodifiableList(providers);
	}

}
