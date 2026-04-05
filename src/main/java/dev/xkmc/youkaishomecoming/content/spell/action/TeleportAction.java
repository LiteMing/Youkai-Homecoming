package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports the caster to the position specified by an OriginConfig.
 * Includes collision check (reverts if blocked), broadcast events, and optional sound.
 * <p>
 * JSON: {"type": "teleport", "destination": {...}, "play_sound": true}
 */
public record TeleportAction(OriginConfig destination, boolean playSound) implements SpellAction {

	public static final Codec<TeleportAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			OriginConfig.CODEC.fieldOf("destination").forGetter(TeleportAction::destination),
			Codec.BOOL.optionalFieldOf("play_sound", true).forGetter(TeleportAction::playSound)
	).apply(i, TeleportAction::new));

	@Override
	public void execute(SpellContext ctx) {
		LivingEntity mob = ctx.self();
		Vec3 target = destination.resolve(ctx);
		if (ctx.holder() instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder) {
			// In preview mode: directly set position, skip all world interactions
			mob.setPos(target);
			return;
		}
		teleport(mob, target, playSound, false);
	}

	/**
	 * Teleport with collision check. Returns true if successful.
	 * In preview mode, collision check and world events are skipped.
	 */
	public static boolean teleport(LivingEntity mob, Vec3 target, boolean sound, boolean skipCollision) {
		Vec3 old = mob.position();
		mob.teleportTo(target.x(), target.y(), target.z());
		if (!skipCollision && !mob.level().noCollision(mob)) {
			mob.teleportTo(old.x(), old.y(), old.z());
			return false;
		}
		if (!skipCollision) {
			mob.level().broadcastEntityEvent(mob, EntityEvent.TELEPORT);
			mob.level().gameEvent(GameEvent.TELEPORT, mob.position(), GameEvent.Context.of(mob));
		}
		if (sound && !mob.isSilent()) {
			mob.level().playSound(null, mob.xo, mob.yo, mob.zo,
					SoundEvents.ENDERMAN_TELEPORT, mob.getSoundSource(), 1.0F, 1.0F);
			mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
		}
		return true;
	}

	/** Overload for non-preview callers. */
	public static boolean teleport(LivingEntity mob, Vec3 target, boolean sound) {
		return teleport(mob, target, sound, false);
	}
}
