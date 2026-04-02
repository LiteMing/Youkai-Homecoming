package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A CardHolder implementation for the preview system.
 * Entities are stored in a local pool and never injected into the real world.
 */
public class PreviewCardHolder implements CardHolder {

	private final Level level;
	private final ArmorStand fakeCaster;
	private final ArmorStand fakeTarget;
	private final List<Entity> localEntities = new ArrayList<>();
	private final RandomSource random = RandomSource.create();

	public PreviewCardHolder(Level level) {
		this.level = level;
		this.fakeCaster = new ArmorStand(EntityType.ARMOR_STAND, level);
		this.fakeCaster.setPos(0, 0, 0);
		this.fakeCaster.setInvisible(true);
		this.fakeTarget = new ArmorStand(EntityType.ARMOR_STAND, level);
		this.fakeTarget.setPos(0, 0, 10);
		this.fakeTarget.setInvisible(true);
	}

	@Override
	public Vec3 center() {
		return fakeCaster.position().add(0, fakeCaster.getBbHeight() / 2, 0);
	}

	@Override
	public Vec3 forward() {
		Vec3 t = target();
		if (t == null) return new Vec3(0, 0, 1);
		return t.subtract(center()).normalize();
	}

	@Nullable
	@Override
	public Vec3 target() {
		return fakeTarget.position().add(0, fakeTarget.getBbHeight() / 2, 0);
	}

	@Override
	public RandomSource random() {
		return random;
	}

	@Override
	public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
		ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), fakeCaster, level);
		danmaku.setPos(center());
		danmaku.setItem(type.get(color).asStack());
		danmaku.setup(getDamage(type), life, true, true, vec);
		return danmaku;
	}

	@Override
	public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
		ItemLaserEntity laser = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), fakeCaster, level);
		laser.setItem(type.get(color).asStack());
		laser.setup(getDamage(type), life, len, true, vec);
		laser.setPos(pos);
		laser.setupLength = type.setupLength();
		return laser;
	}

	@Override
	public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
		// Anonymous subclass: override shoot() to redirect bullets to our local pool
		ShooterEntity shooter = new ShooterEntity(YHEntities.SHOOTER.get(), level) {
			@Override
			public void shoot(Entity danmaku) {
				PreviewCardHolder.this.shoot(danmaku);
			}
		};
		shooter.setup(fakeCaster, fakeTarget, data, spell);
		return shooter;
	}

	@Override
	public void shoot(Entity danmaku) {
		// Add to local pool instead of world
		localEntities.add(danmaku);
	}

	@Override
	public LivingEntity self() {
		return fakeCaster;
	}

	@Override
	public DamageSource getDanmakuDamageSource(IYHDanmaku danmaku) {
		return YHDamageTypes.danmaku(danmaku);
	}

	@Nullable
	@Override
	public Vec3 targetVelocity() {
		return Vec3.ZERO;
	}

	@Nullable
	@Override
	public LivingEntity targetEntity() {
		return fakeTarget;
	}

	@Override
	public float getDamage(YHDanmaku.IDanmakuType type) {
		return type.damage();
	}

	/**
	 * Tick all local entities.
	 * For SimplifiedProjectile (danmaku/laser): replicate ClientDanmakuCache.tick() exactly.
	 * For ShooterEntity: manual movement + lifetime + spell tick.
	 */
	public void tick() {
		var iterator = localEntities.iterator();
		while (iterator.hasNext()) {
			Entity e = iterator.next();

			if (e instanceof SimplifiedProjectile sp) {
				// Replicate ClientDanmakuCache.tick() behavior exactly
				sp.setOldPosAndRot();
				++sp.tickCount;
				sp.tick();
				if (!sp.isValid()) {
					iterator.remove();
				}
			} else if (e instanceof ShooterEntity shooter) {
				tickShooter(shooter);
				if (!shooter.isAlive() || shooter.isRemoved()) {
					iterator.remove();
				}
			} else {
				e.tick();
				if (!e.isAlive() || e.isRemoved()) {
					iterator.remove();
				}
			}
		}
	}

	private void tickShooter(ShooterEntity shooter) {
		// Movement (from ProjectileHealthEntity)
		Vec3 vel = shooter.getDeltaMovement();
		shooter.setPos(
				shooter.getX() + vel.x,
				shooter.getY() + vel.y,
				shooter.getZ() + vel.z
		);

		// Lifetime check
		if (shooter.tickCount >= shooter.lifetime()) {
			shooter.discard();
			return;
		}

		// The spellcard tick (normally in serverAiStep)
		// We use reflection-free access: ShooterEntity implements LivingCardHolder
		// and its serverAiStep calls spellCard.tick(this)
		// We replicate that by accessing the card through the runtime
		try {
			// ShooterEntity.serverAiStep() is protected, call it via tick path workaround
			// Actually, we can just invoke the spell card logic ourselves
			// But spellCard is private in ShooterEntity. Use getLegacyCard pattern instead.
			// For now, call the full LivingEntity.tick() which will execute aiStep() on client too
			shooter.tick();
		} catch (Exception ignored) {
			// Safety net for any server-only code paths
		}
	}

	public List<Entity> getLocalEntities() {
		return localEntities;
	}

	public int getEntityCount() {
		return localEntities.size();
	}

	public void clear() {
		localEntities.clear();
	}

	public void setTargetDistance(float distance) {
		Vec3 dir = forward();
		fakeTarget.setPos(center().add(dir.scale(distance)));
	}

	public void setTargetPos(Vec3 pos) {
		fakeTarget.setPos(pos);
	}

	public Vec3 getTargetPos() {
		return fakeTarget.position();
	}

	public void setCasterHealth(float ratio) {
		float maxHealth = fakeCaster.getMaxHealth();
		fakeCaster.setHealth(maxHealth * Math.max(0.01f, ratio));
	}

	public ArmorStand getFakeCaster() {
		return fakeCaster;
	}

	public ArmorStand getFakeTarget() {
		return fakeTarget;
	}
}
