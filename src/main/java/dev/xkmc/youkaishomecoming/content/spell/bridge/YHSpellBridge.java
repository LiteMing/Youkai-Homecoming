package dev.xkmc.youkaishomecoming.content.spell.bridge;

import dev.xkmc.youkaishomecoming.compat.api.API;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class YHSpellBridge {

	@API
	public static boolean cast(Entity caster, @Nullable Entity target, String spellId) {
		ResourceLocation id = ResourceLocation.tryParse(spellId);
		return id != null && cast(caster, target, id);
	}

	@API
	public static boolean cast(Entity caster, @Nullable Entity target, ResourceLocation spellId) {
		if (caster.level().isClientSide()) {
			return false;
		}
		SpellDefinition definition = SpellRegistry.get(spellId);
		if (definition == null) {
			return false;
		}
		LivingEntity livingTarget = target instanceof LivingEntity le ? le : null;

		if (caster instanceof YoukaiEntity youkai) {
			youkai.setSpellRuntime(new SpellRuntime(youkai, definition));
			if (livingTarget != null) {
				youkai.setTarget(livingTarget);
			}
			return true;
		}
		if (caster instanceof DanmakuProxyEntity proxy) {
			proxy.switchSpellDefinition(definition, true);
			return true;
		}
		if (caster instanceof ServerPlayer player) {
			DanmakuProxyEntity proxy = new DanmakuProxyEntity(
					YHEntities.DANMAKU_PROXY.get(), player.serverLevel());
			proxy.init(player, definition, DynamicSpellItem.DURATION_NATURAL, livingTarget);
			player.serverLevel().addFreshEntity(proxy);
			SpellContainer.trackProxy(player, proxy);
			return true;
		}
		return false;
	}
}
