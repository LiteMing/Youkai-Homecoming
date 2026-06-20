package dev.xkmc.youkaishomecoming.content.spell;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for creating {@link EntitySpellProxyEntity} instances.
 * <p>
 * This is the public API for assigning a spell card proxy to any entity, or for creating
 * block-based spell traps. No block registration is needed: just call these methods
 * from your block's tick logic.
 * <p>
 * Example: Ender Dragon with spell cards
 * <pre>{@code
 *   // In your dragon's tick or AI goal:
 *   EntitySpellProxyEntity proxy = SpellCardBlockHelper.createProxy(
 *       dragon, spellDef, 200, dragon.getTarget());
 *   dragon.level().addFreshEntity(proxy);
 * }</pre>
 * <p>
 * Example: Spell trap block
 * <pre>{@code
 *   // In your block entity's tick:
 *   if (redstoneSignal > 0 && proxy == null) {
 *       proxy = SpellCardBlockHelper.createFixedProxy(
 *           serverLevel, blockPos, spellDef, 100, null);
 *   }
 * }</pre>
 */
public final class SpellCardBlockHelper {

	private SpellCardBlockHelper() {
	}

	/**
	 * Create a spell proxy attached to an entity.
	 *
	 * @param host       the entity to attach to (e.g., Ender Dragon, custom mob)
	 * @param definition the spell definition to drive
	 * @param duration   total duration in ticks (-1 for natural end)
	 * @param target     optional target entity for aimed shots
	 * @return the proxy entity (not yet added to the world)
	 */
	public static EntitySpellProxyEntity createProxy(Entity host, SpellDefinition definition,
													  int duration, @Nullable LivingEntity target) {
		EntitySpellProxyEntity proxy = new EntitySpellProxyEntity(
				YHEntities.ENTITY_SPELL_PROXY.get(), host.level());
		proxy.attachTo(host, definition, duration, target);
		return proxy;
	}

	/**
	 * Create a spell proxy at a fixed position for block traps.
	 *
	 * @param level      the server level
	 * @param pos        the position to spawn at
	 * @param definition the spell definition to drive
	 * @param duration   total duration in ticks (-1 for natural end)
	 * @param target     optional target entity for aimed shots (null = auto-select nearest player)
	 * @return the proxy entity (not yet added to the world)
	 */
	public static EntitySpellProxyEntity createFixedProxy(ServerLevel level, Vec3 pos,
														  SpellDefinition definition, int duration,
														  @Nullable LivingEntity target) {
		return createFixedProxy(level, pos, 0, 0, definition, duration, target);
	}

	public static EntitySpellProxyEntity createFixedProxy(ServerLevel level, Vec3 pos, float yRot, float xRot,
														  SpellDefinition definition, int duration,
														  @Nullable LivingEntity target) {
		EntitySpellProxyEntity proxy = new EntitySpellProxyEntity(
				YHEntities.ENTITY_SPELL_PROXY.get(), level);
		proxy.spawnAtPosition(pos, yRot, xRot, definition, duration, target);
		return proxy;
	}

	/**
	 * Convenience: create a proxy, add it to the world, and return it.
	 *
	 * @return the added proxy, or null if the level is client-side
	 */
	@Nullable
	public static EntitySpellProxyEntity spawnProxy(Entity host, SpellDefinition definition,
													int duration, @Nullable LivingEntity target) {
		if (!(host.level() instanceof ServerLevel)) return null;
		EntitySpellProxyEntity proxy = createProxy(host, definition, duration, target);
		host.level().addFreshEntity(proxy);
		return proxy;
	}

	/**
	 * Convenience: create a fixed-position proxy, add it to the world, and return it.
	 *
	 * @return the added proxy, or null if the level is client-side
	 */
	@Nullable
	public static EntitySpellProxyEntity spawnFixedProxy(ServerLevel level, Vec3 pos,
														SpellDefinition definition, int duration,
														@Nullable LivingEntity target) {
		return spawnFixedProxy(level, pos, 0, 0, definition, duration, target);
	}

	@Nullable
	public static EntitySpellProxyEntity spawnFixedProxy(ServerLevel level, Vec3 pos, float yRot, float xRot,
														SpellDefinition definition, int duration,
														@Nullable LivingEntity target) {
		EntitySpellProxyEntity proxy = createFixedProxy(level, pos, yRot, xRot, definition, duration, target);
		level.addFreshEntity(proxy);
		return proxy;
	}
}
