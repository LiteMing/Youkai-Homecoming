package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.fastprojectileapi.entity.GrazingEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuProxyEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;
import org.jetbrains.annotations.Nullable;

@Cancelable
public class DanmakuGrazeEvent extends PlayerEvent {

	private final GrazingEntity e;
	@Nullable
	private final Entity danmakuEntity;
	@Nullable
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation phaseId;

	public DanmakuGrazeEvent(Player player, GrazingEntity e) {
		super(player);
		this.e = e;
		this.danmakuEntity = e instanceof Entity entity ? entity : null;
		SpellRuntime runtime = resolveRuntime(danmakuEntity);
		this.spellId = runtime == null ? null : runtime.getDefinition().id;
		this.phaseId = runtime == null ? null : runtime.getCurrentPhaseId();
	}

	public GrazingEntity getDanmaku() {
		return e;
	}

	@Nullable
	public Entity getDanmakuEntity() {
		return danmakuEntity;
	}

	@Nullable
	public ResourceLocation getSpellId() {
		return spellId;
	}

	@Nullable
	public ResourceLocation getPhaseId() {
		return phaseId;
	}

	@Nullable
	private static SpellRuntime resolveRuntime(@Nullable Entity danmakuEntity) {
		if (!(danmakuEntity instanceof YHBaseDanmakuEntity danmaku)) {
			return null;
		}
		Entity owner = danmaku.getOwner();
		if (owner instanceof DanmakuProxyEntity proxy) {
			return proxy.getSpellRuntime();
		}
		if (owner instanceof YoukaiEntity youkai) {
			return youkai.spellRuntime;
		}
		return null;
	}

}
