package gen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellConditions;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/** Contract checks for the onHit-only player target condition. */
public final class SpellHitEntityPlayerConditionTest {

	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var condition = SpellCondition.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"type\":\"hit_entity_is_player\"}"))
				.getOrThrow(false, message -> {});
		check("condition codec decodes hit_entity_is_player", condition instanceof SpellConditions.HitEntityIsPlayer);
		check("condition codec keeps registered id",
				SpellConditions.getTypeId(condition).equals("hit_entity_is_player"));

		var player = allocate(TestPlayer.class);
		var entityHit = new SpellHitContext(null, SpellHitContext.HitType.ENTITY,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, player);
		var blockHit = new SpellHitContext(null, SpellHitContext.HitType.BLOCK,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null);
		var nonPlayer = allocate(TestEntity.class);
		var otherEntityHit = new SpellHitContext(null, SpellHitContext.HitType.ENTITY,
				Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, nonPlayer);

		check("entity hit on player passes", condition.test(context(entityHit)));
		check("entity hit on non-player fails", !condition.test(context(otherEntityHit)));
		check("block hit fails", !condition.test(context(blockHit)));
		check("no hit context fails", !condition.test(new SpellContext(null, null, null,
				DifficultyModifiers.DEFAULT)));
		System.out.println("SpellHitEntityPlayerConditionTest: 6 checks passed");
	}

	private static SpellContext context(SpellHitContext hit) {
		return new SpellContext(null, null, null, DifficultyModifiers.DEFAULT, hit);
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocate(Class<T> type) {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (T) ((Unsafe) field.get(null)).allocateInstance(type);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static final class TestPlayer extends Player {
		private TestPlayer() {
			super(null, BlockPos.ZERO, 0, null);
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}
	}

	private static final class TestEntity extends Entity {
		private TestEntity() {
			super(EntityType.MARKER, null);
		}

		@Override
		protected void defineSynchedData() {
		}

		@Override
		protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		}

		@Override
		protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		}
	}

	private static void check(String name, boolean condition) {
		if (!condition) throw new AssertionError(name);
		System.out.println("PASS  " + name);
	}
}
