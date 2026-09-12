package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.action.TrailCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.item.PlayerHolder;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server authority and lifetime for spell-authored hints, independent of the caster's renderer. */
@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID)
public final class YsmSpellHints {

	private record Entry(CardHolder source, SpellRuntime runtime, YsmSpellHintState state) {
		boolean active(LivingEntity caster) {
			LivingEntity sourceEntity = source.self();
			return caster.isAlive() && !caster.isRemoved() && sourceEntity.isAlive() && !sourceEntity.isRemoved()
					&& (!(source instanceof net.minecraft.world.entity.Entity entity) || entity.isAlive() && !entity.isRemoved())
					&& sourceEntity.level() == caster.level() && !runtime.isFinished()
					&& (!(source instanceof SpellRuntimeHost host) || host.getSpellRuntime() == runtime)
					&& (!(source instanceof PlayerHolder holder) || SpellContainer.isActiveItemSpell(holder.player(), holder.spell()))
					&& (!(source instanceof DanmakuProxyEntity proxy) || !proxy.isGenerationStopped())
					&& !(caster instanceof YoukaiEntity youkai && youkai.isBeaten())
					&& !caster.hasEffect(YHEffects.BEATEN.get()) && state.active(caster.level().getGameTime());
		}
	}

	// Preview runtimes can be reset on the client thread of an integrated server.
	private static final Map<LivingEntity, Entry> HINTS = new ConcurrentHashMap<>();
	private static long sequence;

	private YsmSpellHints() { }

	public static CardHolder source(CardHolder holder) {
		while (holder instanceof TrailCardHolder trail) holder = trail.delegate();
		return holder;
	}

	public static LivingEntity caster(CardHolder holder) {
		holder = source(holder);
		if (holder instanceof SpellRuntimeHost host) return host.spellCircleDisplayEntity();
		// A visible shooter is its own animation target, even when its damage owner is a player.
		if (holder instanceof LivingEntity entity) return entity;
		if (holder instanceof LivingCardHolder living) return living.shooter();
		return holder.self();
	}

	public static void apply(SpellContext ctx, String hint, int duration) {
		CardHolder source = source(ctx.holder());
		if (source instanceof PreviewCardHolder preview) {
			if (hint.isBlank()) preview.clearYsmRenderOverride("animation");
			else YsmRenderConfig.hint(hint, duration).apply(preview);
			return;
		}
		LivingEntity caster = caster(source);
		if (!(caster.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
		// Retire the old entity-local hint field; model, texture and explicit /yhysm clips remain separate.
		if (caster instanceof YsmRenderOverrideTarget target) target.clearYsmRenderOverride("animation");
		if (hint.isBlank()) {
			clear(caster);
			return;
		}
		SpellRuntime runtime = source instanceof SpellRuntimeHost host && host.getSpellRuntime() != null
				? host.getSpellRuntime() : ctx.runtime();
		var state = YsmSpellHintState.create(hint, level.getGameTime(), duration, ++sequence);
		HINTS.put(caster, new Entry(source, runtime, state));
		sync(caster, state);
	}

	public static void clearRuntime(SpellRuntime runtime) {
		var iterator = HINTS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.getValue().runtime() != runtime) continue;
			iterator.remove();
			sync(entry.getKey(), new YsmSpellHintState("", 0, ++sequence));
		}
	}

	private static void clear(LivingEntity caster) {
		if (HINTS.remove(caster) != null) sync(caster, new YsmSpellHintState("", 0, ++sequence));
	}

	private static void sync(LivingEntity caster, YsmSpellHintState state) {
		var packet = new YsmSpellHintToClient(caster, state);
		YoukaisHomecoming.HANDLER.toTrackingPlayers(packet, caster);
		if (caster instanceof ServerPlayer player) YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
	}

	@SubscribeEvent
	public static void tick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		var iterator = HINTS.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.getValue().active(entry.getKey())) continue;
			iterator.remove();
			sync(entry.getKey(), new YsmSpellHintState("", 0, ++sequence));
		}
	}

	@SubscribeEvent
	public static void track(PlayerEvent.StartTracking event) {
		if (!(event.getEntity() instanceof ServerPlayer viewer)
				|| !(event.getTarget() instanceof LivingEntity caster)) return;
		Entry entry = HINTS.get(caster);
		if (entry != null && entry.active(caster))
			YoukaisHomecoming.HANDLER.toClientPlayer(new YsmSpellHintToClient(caster, entry.state()), viewer);
	}

	@SubscribeEvent
	public static void stopped(ServerStoppedEvent event) {
		HINTS.clear();
	}
}
