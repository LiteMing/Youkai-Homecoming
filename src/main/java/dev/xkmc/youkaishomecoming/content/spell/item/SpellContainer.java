package dev.xkmc.youkaishomecoming.content.spell.item;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.l2library.capability.conditionals.*;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

@SerialClass
public class SpellContainer extends ConditionalToken {

	private static final TokenKey<SpellContainer> SPELL = TokenKey.of(YoukaisHomecoming.loc("spellcards"));

	private static final Provider PVD = new Provider();

	public static void clear(ServerPlayer sp) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		for (var spell : data.spells) {
			for (var e : spell.cache) {
				e.markErased(true);
			}
		}
		for (var e : data.cache) {
			e.markErased(true);
		}
		for (var proxy : data.proxies) {
			if (!proxy.isRemoved()) proxy.cleanup();
		}
		data.cache.clear();
		data.spells.clear();
		data.proxies.clear();
		DanmakuManager.flushErases();
	}

	public static void track(ServerPlayer sp, SimplifiedProjectile e) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.cache.add(e);
	}

	public static void trackProxy(ServerPlayer sp, DanmakuProxyEntity proxy) {
		var data = ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD);
		data.proxies.add(proxy);
	}

	public static void castSpell(ServerPlayer sp, Supplier<? extends ItemSpell> sup, @Nullable LivingEntity target) {
		ItemSpell spell = sup.get();
		spell.start(sp, target);
		ConditionalData.HOLDER.get(sp).getOrCreateData(PVD, PVD).spells.add(spell);
	}

	@SerialClass.SerialField
	private final List<ItemSpell> spells = new LinkedList<>();

	private final List<SimplifiedProjectile> cache = new LinkedList<>();

	private final List<DanmakuProxyEntity> proxies = new ArrayList<>();

	@Override
	public boolean tick(Player player) {
		var itr = spells.iterator();
		while (itr.hasNext()) {
			var spell = itr.next();
			boolean remove = spell.tick(player);
			if (remove) {
				itr.remove();
				cache.addAll(spell.cache);
			}
		}
		cache.removeIf(e -> !e.isValid());
		proxies.removeIf(DanmakuProxyEntity::isRemoved);
		return spells.isEmpty() && cache.isEmpty() && proxies.isEmpty();
	}

	private record Provider() implements TokenProvider<SpellContainer, Provider>, Context {

		@Override
		public SpellContainer getData(Provider provider) {
			return new SpellContainer();
		}

		@Override
		public TokenKey<SpellContainer> getKey() {
			return SPELL;
		}
	}

}
