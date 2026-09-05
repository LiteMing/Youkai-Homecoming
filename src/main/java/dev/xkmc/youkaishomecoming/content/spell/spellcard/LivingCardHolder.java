package dev.xkmc.youkaishomecoming.content.spell.spellcard;

import dev.xkmc.fastprojectileapi.collision.EntityStorageHelper;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface LivingCardHolder extends CardHolder {

	LivingEntity self();

	LivingEntity shooter();

	@Override
	default Vec3 center() {
		return self().position().add(0, self().getBbHeight() / 2, 0);
	}

	@Override
	default Vec3 forward() {
		var target = target();
		if (target != null) {
			Vec3 delta = target.subtract(center());
			if (delta.lengthSqr() >= 1e-6) return delta.normalize();
		}
		return self().getForward();
	}

	@Override
	default @Nullable Vec3 target() {
		var le = targetEntity();
		if (le == null) return null;
		return le.position().add(0, le.getBbHeight() / 2, 0);
	}

	@Override
	default @Nullable Vec3 targetVelocity() {
		var le = targetEntity();
		if (le == null) return null;
		return le.getDeltaMovement();
	}

	@Override
	default RandomSource random() {
		return self().getRandom();
	}

	@Override
	default ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DanmakuColor color) {
		ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), shooter(), self().level());
		LivingEntity target = targetEntity();
		danmaku.setRetargetTarget(target);
		configureHarmfulPlayerSnapshot(danmaku);
		if (shooter() instanceof net.minecraft.world.entity.player.Player) {
			danmaku.restrictPlayerSpellDamage(target);
		}
		danmaku.setPos(center());
		// For DYE_TEXTURES mode: use the specific colored item (has correct texture baked in)
		// For TINTED/FIXED modes: use BASE_DANMAKU with NBT color and runtime tint
		if (type.usesDyeTextures()) {
			DyeColor dyeColor = color.toDyeColor();
			danmaku.setItem(type.get(dyeColor).asStack());
		} else {
			danmaku.setItem(type.stack(color));
			danmaku.setTint(color.argb());
		}
		danmaku.setup(getDamage(type),
				life, true, true, vec);
		return danmaku;
	}

	@Override
	default ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
		ItemLaserEntity danmaku = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), shooter(), self().level());
		configureHarmfulPlayerSnapshot(danmaku);
		if (shooter() instanceof net.minecraft.world.entity.player.Player) {
			danmaku.restrictPlayerSpellDamage(targetEntity());
		}
		danmaku.setItem(type.get(color).asStack());
		danmaku.setup(getDamage(type),
				life, len, true, vec);
		danmaku.setBeamStart(pos);
		danmaku.setupLength = type.setupLength();
		return danmaku;
	}

	default TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 dir, float size, String text, int textColor) {
		TextDanmakuEntity danmaku = new TextDanmakuEntity(YHEntities.TEXT_DANMAKU.get(), shooter(), self().level());
		configureHarmfulPlayerSnapshot(danmaku);
		if (shooter() instanceof net.minecraft.world.entity.player.Player) {
			danmaku.restrictPlayerSpellDamage(targetEntity());
		}
		danmaku.setPos(pos);
		danmaku.configureText(text, size, textColor);
		// Use PENCIL laser damage type as default for text danmaku
		danmaku.setup(getDamage(YHDanmaku.Laser.PENCIL), life, danmaku.length, true, dir);
		danmaku.setupLength = YHDanmaku.Laser.PENCIL.setupLength();
		return danmaku;
	}

	private void configureHarmfulPlayerSnapshot(IYHDanmaku danmaku) {
		LivingEntity source = shooter();
		if (source instanceof dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity youkai) {
			danmaku.setHarmfulPlayerSnapshot(youkai.targets.snapshotIds());
			return;
		}
		LivingEntity target = targetEntity();
		if (source instanceof net.minecraft.world.entity.player.Player caster) {
			java.util.Set<java.util.UUID> harmfulPlayers = new java.util.LinkedHashSet<>();
			// An explicitly selected player remains a valid target even when neither
			// player has joined a team.
			if (target instanceof net.minecraft.world.entity.player.Player selected
					&& selected != caster && !caster.isAlliedTo(selected)) {
				harmfulPlayers.add(selected.getUUID());
			}
			// A player assigned to any team is a default combat target. Vanilla
			// alliance checks above still protect members of the caster's own team.
			for (net.minecraft.world.entity.player.Player candidate : caster.level().players()) {
				if (candidate != caster && GrazeHelper.isUntargetedPlayerSpellTarget(caster, candidate)) {
					harmfulPlayers.add(candidate.getUUID());
				}
			}
			danmaku.setHarmfulPlayerSnapshot(harmfulPlayers);
		} else if (target instanceof net.minecraft.world.entity.player.Player player
				&& !source.isAlliedTo(player)) {
			danmaku.setHarmfulPlayerSnapshot(java.util.List.of(player.getUUID()));
		}
	}


	@Override
	default ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
		ShooterEntity ans = new ShooterEntity(YHEntities.SHOOTER.get(), self().level());
		ans.setup(shooter(), targetEntity(), data, spell);
		return ans;
	}

	@Override
	default void shoot(Entity danmaku) {
		if (self().level() instanceof ServerLevel sl)
			EntityStorageHelper.fastAdd(sl, danmaku);
	}


}
