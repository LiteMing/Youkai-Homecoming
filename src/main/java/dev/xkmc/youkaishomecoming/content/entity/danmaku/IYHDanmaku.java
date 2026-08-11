package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.EntityInfo;
import dev.xkmc.fastprojectileapi.entity.GrazingEntity;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationContactGateway;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.events.GeneralEventHandlers;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

public interface IYHDanmaku extends GrazingEntity {

	float GRAZE_RANGE = 1.5f;

	float damage(Entity target);

	SimplifiedProjectile self();

	default boolean canHitDanmakuTarget(EntityInfo target) {
		return true;
	}

	default void restrictPlayerSpellDamage(@Nullable LivingEntity target) {
	}

	default void setHarmfulPlayerSnapshot(java.util.Collection<java.util.UUID> playerIds) {
	}

	default boolean hasHarmfulPlayerSnapshot() {
		return false;
	}

	default boolean isHarmfulToPlayer(java.util.UUID playerId) {
		return true;
	}

	static boolean canPlayerSpellHit(EntityInfo candidate, boolean restricted, @Nullable java.util.UUID targetId) {
		if (!restricted) return true;
		// The selected target steers the spell; collision always uses the full hostile whitelist.
		return candidate.hostileForPlayerSpell();
	}

	@Override
	default float grazeRange() {
		return GRAZE_RANGE;
	}

	@Override
	default AABB alterHitBox(EntityInfo x, float radius, float graze) {
		if (x.ownerTrackedByYoukai() && x.entity() instanceof YoukaiEntity) {
			return x.boundingBox().inflate(GRAZE_RANGE);
		}
		return alterEntityHitBox(x, radius, graze);
	}

	default boolean shouldHurt(@Nullable Entity owner, Entity e) {
		if (owner == null || e instanceof DanmakuHostProxy) return false;
		if (owner instanceof YoukaiEntity youkai) {
			if (e instanceof LivingEntity le) {
				return youkai.shouldHurt(le);
			}
			return false;
		}
		return true;
	}

	/**
	 * Returns the per-danmaku damage type override, or null if none is set.
	 * Override this in subclasses that support data-driven damage type configuration.
	 */
	@Nullable
	default DanmakuDamageType getDamageTypeOverride() {
		return null;
	}

	default DamageSource source() {
		// Per-danmaku damage type override (set by data-driven fire_danmaku/fire_laser actions)
		DanmakuDamageType override = getDamageTypeOverride();
		if (override != null) {
			DamageSource dmgType = override.resolve(this);
			if (self().getOwner() instanceof LivingEntity le)
				dmgType = GeneralEventHandlers.modifyDamageType(le, dmgType, this);
			return dmgType;
		}
		DamageSource dmgType = YHDamageTypes.danmaku(this);
		if (self().getOwner() instanceof CardHolder youkai) {
			dmgType = youkai.getDanmakuDamageSource(this);
		}
		if (self().getOwner() instanceof LivingEntity le)
			dmgType = GeneralEventHandlers.modifyDamageType(le, dmgType, this);
		return dmgType;
	}

	default void hurtTarget(EntityHitResult result) {
		if (self().level().isClientSide) return;
		var e = result.getEntity();
		if (e instanceof DanmakuHostProxy) return;
		// Certification No-Hit contact must precede hurt-time invul checks, auto-bomb
		// and damage events (design doc §8, D8): a hit is a hit even under i-frames.
		if (e instanceof ServerPlayer sp && self().getOwner() instanceof SpellCertificationEntity cert) {
			CertificationContactGateway.onCertificationContact(cert, sp);
			return;
		}
		var source = source();
		if (e instanceof LivingEntity le) {
			if (le.hurtTime > 0 && (YHModConfig.COMMON.invulFrameForDanmaku.get() || e instanceof Player || e instanceof YoukaiEntity)) {
				DamageSource last = le.getLastDamageSource();
				if (last != null && last.getDirectEntity() instanceof IYHDanmaku) {
					return;
				}
			}
		}
		LivingEntity target = null;
		while (e instanceof PartEntity<?> pe) {
			e = pe.getParent();
		}
		if (e instanceof LivingEntity le) target = le;
		var owner = self().getOwner();
		if (target != null && owner instanceof YoukaiEntity youkai) {
			youkai.danmakuHitTarget(this, source, target);
			return;
		}
		e.hurt(source, damage(e));
	}

	static AABB alterEntityHitBox(EntityInfo x, float radius, float graze) {
		var box = x.boundingBox();
		if (graze > 0) return box.inflate(radius + graze);
		float shrink = -x.hitBoxDelta();
		return new AABB(
				box.minX + shrink - radius, box.minY + shrink * 2 - radius, box.minZ + shrink - radius,
				box.maxX - shrink + radius, box.maxY + radius, box.maxZ - shrink + radius
		);
	}

	static AABB alterEntityHitBox(Entity x, float radius, float graze) {
		var box = x.getBoundingBox();
		if (graze > 0) return box.inflate(radius + graze);
		float shrink = x instanceof Player player ? -GrazeHelper.getHitBoxDelta(player) : 0;
		return new AABB(
				box.minX + shrink - radius, box.minY + shrink * 2 - radius, box.minZ + shrink - radius,
				box.maxX - shrink + radius, box.maxY + radius, box.maxZ - shrink + radius
		);
	}

}
