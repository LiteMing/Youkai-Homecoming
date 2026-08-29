package dev.xkmc.fastprojectileapi.spellcircle;

import dev.xkmc.l2library.serial.config.BaseConfig;
import dev.xkmc.l2library.serial.config.CollectType;
import dev.xkmc.l2library.serial.config.ConfigCollect;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@SerialClass
public class SpellCircleConfig extends BaseConfig {

	private static Set<ResourceLocation> cachedBuiltinIds = null;

	/**
	 * Returns the IDs supplied by resource-pack config entries.  The merged map is
	 * also used for server/world overrides, so looking at it cannot tell a built-in
	 * circle from one loaded from {@link CustomSpellCircleStorage}.  The individual
	 * entries exposed by {@link YoukaisHomecoming#SPELL} are the immutable source
	 * definitions and therefore provide the same distinction as SpellRegistry's
	 * built-in snapshot.
	 */
	public static Set<ResourceLocation> builtinIds() {
		if (cachedBuiltinIds != null) {
			return cachedBuiltinIds;
		}
		Set<ResourceLocation> ids = new HashSet<>();
		for (SpellCircleConfig config : YoukaisHomecoming.SPELL.getAll()) {
			if (config == null || config.map == null) {
				continue;
			}
			for (String key : config.map.keySet()) {
				ResourceLocation id = ResourceLocation.tryParse(key);
				if (id != null) {
					ids.add(id);
				}
			}
		}
		cachedBuiltinIds = Set.copyOf(ids);
		return cachedBuiltinIds;
	}

	public static void invalidateBuiltinCache() {
		cachedBuiltinIds = null;
	}

	public static boolean isBuiltin(@Nullable ResourceLocation id) {
		return id != null && builtinIds().contains(id);
	}

	/** Return the packaged source component for a built-in ID, if available. */
	@Nullable
	public static SpellComponent builtinComponent(ResourceLocation id) {
		for (SpellCircleConfig config : YoukaisHomecoming.SPELL.getAll()) {
			if (config == null || config.map == null) {
				continue;
			}
			SpellComponent component = config.map.get(id.toString());
			if (component != null) {
				return component;
			}
		}
		return null;
	}

	@Nullable
	public static SpellComponent getFromConfig(ResourceLocation s) {
		return YoukaisHomecoming.SPELL.getMerged().map.get(s.toString());
	}

	@ConfigCollect(CollectType.MAP_OVERWRITE)
	@SerialClass.SerialField
	public HashMap<String, SpellComponent> map = new HashMap<>();

}
