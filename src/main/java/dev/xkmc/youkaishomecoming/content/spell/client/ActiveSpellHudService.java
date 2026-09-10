package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.EntitySpellProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the per-viewer active spell snapshot. */
public final class ActiveSpellHudService {

	private static final double RANGE = 128.0;

	private ActiveSpellHudService() {
	}

	public static void sync(ServerPlayer viewer) {
		if (!(viewer.level() instanceof ServerLevel level)) return;
		GrazeCapability capability = GrazeCapability.HOLDER.get(viewer);
		List<ActiveSpellHudToClient.Entry> entries = new ArrayList<>();
		Set<Integer> hosts = new HashSet<>();

		addPlayerSpell(entries, hosts, viewer, false, true);
		for (LivingEntity opponent : capability.snapshotOpponents().entities()) {
			if (opponent instanceof ServerPlayer player) {
				addPlayerSpell(entries, hosts, player, true, false);
			} else if (opponent instanceof YoukaiEntity youkai) {
				addYoukaiSpell(entries, hosts, youkai, true);
			}
		}

		AABB area = AABB.ofSize(viewer.position(), RANGE * 2, RANGE * 2, RANGE * 2);
		for (YoukaiEntity youkai : level.getEntitiesOfClass(YoukaiEntity.class, area,
				e -> e.targets.contains(viewer))) {
			addYoukaiSpell(entries, hosts, youkai, true);
		}
		for (EntitySpellProxyEntity proxy : level.getEntitiesOfClass(EntitySpellProxyEntity.class, area)) {
			if (proxy.isFinished()) continue;
			if (proxy.getSpellRuntime() == null || !proxy.getSpellRuntime().isSpellHudVisible()) continue;
			ResourceLocation id = proxy.getSpellDefinitionId();
			SpellDefinition definition = id == null ? null : SpellRegistry.get(id);
			if (definition == null || definition.itemForm.cardType() == SpellCardType.NON_SPELL) continue;
			LivingEntity owner = proxy.owner();
			boolean own = owner == viewer;
			boolean hostile = !own && isHostile(viewer, capability, owner, proxy);
			if (!own && !hostile) continue;
			add(entries, hosts, proxy.getId(), proxy.getSpellRuntime(), hostile, own);
		}

		YoukaisHomecoming.HANDLER.toClientPlayer(new ActiveSpellHudToClient(entries), viewer);
	}

	private static void addPlayerSpell(List<ActiveSpellHudToClient.Entry> entries, Set<Integer> hosts,
			ServerPlayer player, boolean hostile, boolean own) {
		SpellContainer.ActiveSpellInfo info = SpellContainer.activeSpellInfo(player);
		if (info == null) return;
		if (hosts.add(player.getId())) {
			entries.add(new ActiveSpellHudToClient.Entry(player.getId(), info.id().toString(),
					info.displayName(), hostile, own, info.titleId(), info.presentation()));
		}
	}

	private static void addYoukaiSpell(List<ActiveSpellHudToClient.Entry> entries, Set<Integer> hosts,
			YoukaiEntity youkai, boolean hostile) {
		var runtime = youkai.getSpellRuntime();
		if (runtime == null || !runtime.isSpellHudVisible()
				|| runtime.getDefinition().itemForm.cardType() == SpellCardType.NON_SPELL) return;
		add(entries, hosts, youkai.getId(), runtime, hostile, false);
	}

	private static boolean isHostile(ServerPlayer viewer, GrazeCapability capability,
			LivingEntity owner, EntitySpellProxyEntity proxy) {
		if (owner instanceof ServerPlayer player) {
			return capability.hasPlayerOpponent(player.getUUID()) || capability.isForcedDanmakuCombat();
		}
		if (owner instanceof YoukaiEntity youkai) {
			return capability.isInSession(youkai.getUUID()) || youkai.targets.contains(viewer);
		}
		if (proxy.targetEntity() == viewer) return true;
		return owner instanceof Mob mob && mob.getTarget() == viewer;
	}

	private static void add(List<ActiveSpellHudToClient.Entry> entries, Set<Integer> hosts,
			int hostId, SpellRuntime runtime, boolean hostile, boolean own) {
		if (runtime == null || !hosts.add(hostId)) return;
		var definition = runtime.getDefinition();
		entries.add(new ActiveSpellHudToClient.Entry(hostId, definition.id.toString(), definition.display.name(),
				hostile, own, runtime.getSpellTitleId(), runtime.getSpellTitlePresentation()));
	}
}
