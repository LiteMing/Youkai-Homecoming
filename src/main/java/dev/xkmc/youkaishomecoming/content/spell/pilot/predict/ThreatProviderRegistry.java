package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered provider chain: first {@link ThreatProvider#supports(Entity)} wins.
 * A null {@code capture} means "no threat", not "try the next provider"
 * (otherwise Ballistic rejecting a stuck arrow would fall through to T3
 * and still produce a stationary threat that dominates APF weight).
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
		// First matching provider wins. A null result means "this provider
		// supports the type but has no threat" — do NOT fall through to a
		// looser provider (e.g. Ballistic null for stuck arrow must not hit T3).
		for (ThreatProvider provider : providers) {
			if (provider.supports(entity)) {
				return provider.capture(entity, horizon);
			}
		}
		return null;
	}

	public List<ThreatProvider> getProviders() {
		return Collections.unmodifiableList(providers);
	}

}
