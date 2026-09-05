package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a CardHolder to override center() and forward() with the position/direction
 * of an expired danmaku. Used by DataDrivenTrailAction to execute onExpiry actions
 * from the danmaku's final position rather than the boss's position.
 */
public class TrailCardHolder implements CardHolder {

	private final CardHolder delegate;
	private final Vec3 position;
	private final Vec3 direction;
	private final HitType hitType;
	@Nullable
	private final Entity hitEntity;

	public TrailCardHolder(CardHolder delegate, Vec3 position, Vec3 direction) {
		this(delegate, position, direction, HitType.NONE, null);
	}

	public TrailCardHolder(CardHolder delegate, Vec3 position, Vec3 direction,
			HitType hitType, @Nullable Entity hitEntity) {
		this.delegate = delegate;
		this.position = position;
		this.direction = direction.lengthSqr() > 1e-8 ? direction.normalize() : new Vec3(0, 0, 1);
		this.hitType = hitType;
		this.hitEntity = hitEntity;
	}

	public CardHolder delegate() {
		return delegate;
	}

	public HitType hitType() {
		return hitType;
	}

	@Nullable
	public Entity hitEntity() {
		return hitEntity;
	}

	@Override
	public Vec3 center() {
		return position;
	}

	@Override
	public Vec3 forward() {
		return direction;
	}

	@Override
	@Nullable
	public Vec3 target() {
		return delegate.target();
	}

	@Override
	public RandomSource random() {
		return delegate.random();
	}

	@Override
	public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DanmakuColor color) {
		return delegate.prepareDanmaku(life, vec, type, color);
	}

	@Override
	public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
		return delegate.prepareLaser(life, pos, vec, len, type, color);
	}

	@Override
	public TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 dir, float size, String text, int textColor) {
		return delegate.prepareTextDanmaku(life, pos, dir, size, text, textColor);
	}

	@Override
	public void shoot(Entity danmaku) {
		delegate.shoot(danmaku);
	}

	@Override
	public LivingEntity self() {
		return delegate.self();
	}

	@Override
	public double casterPower() {
		return delegate.casterPower();
	}

	@Override
	public DamageSource getDanmakuDamageSource(IYHDanmaku danmaku) {
		return delegate.getDanmakuDamageSource(danmaku);
	}

	@Override
	@Nullable
	public Vec3 targetVelocity() {
		return delegate.targetVelocity();
	}

	@Override
	public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
		return delegate.prepareShooter(data, spell);
	}

	@Override
	@Nullable
	public LivingEntity targetEntity() {
		return delegate.targetEntity();
	}

	@Override
	public float getDamage(YHDanmaku.IDanmakuType type) {
		return delegate.getDamage(type);
	}

	public enum HitType {
		NONE,
		ENTITY,
		BLOCK
	}

}
