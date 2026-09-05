package dev.xkmc.youkaishomecoming.init.data;

import dev.xkmc.l2damagetracker.init.data.DamageTypeAndTagsGen;
import dev.xkmc.l2damagetracker.init.data.L2DamageTypes;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import net.minecraft.network.chat.Component;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class YHDamageTypes extends DamageTypeAndTagsGen {

	public static final ResourceKey<DamageType> KOISHI = createDamage("koishi_attack");
	public static final ResourceKey<DamageType> RUMIA = createDamage("rumia_attack");
	public static final ResourceKey<DamageType> DANMAKU = createDamage("danmaku");
	public static final ResourceKey<DamageType> ABYSSAL = createDamage("abyssal_danmaku");

	public static final TagKey<DamageType> DANMAKU_TYPE = TagKey.create(Registries.DAMAGE_TYPE, YoukaisHomecoming.loc("danmaku"));

	private static final DamageTypeTagGroup DANMAKU_GROUP = DamageTypeTagGroup.of(DamageTypeTags.NO_IMPACT,
			L2DamageTypes.MAGIC, DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_COOLDOWN, DANMAKU_TYPE);

	public YHDamageTypes(PackOutput output, CompletableFuture<HolderLookup.Provider> pvd, ExistingFileHelper helper) {
		super(output, pvd, helper, YoukaisHomecoming.MODID);
		new DamageTypeHolder(KOISHI, new DamageType("koishi_attack", 0.1f))
				.add(L2DamageTypes.BYPASS_MAGIC);
		new DamageTypeHolder(RUMIA, new DamageType("rumia_attack", 0.1f))
				.add(L2DamageTypes.BYPASS_MAGIC);
		new DamageTypeHolder(DANMAKU, new DamageType("danmaku", 0.1f))
				.add(DANMAKU_GROUP);
		new DamageTypeHolder(ABYSSAL, new DamageType("abyssal_danmaku", 0.1f))
				.add(DANMAKU_GROUP).add(L2DamageTypes.BYPASS_MAGIC);
	}

	private static ResourceKey<DamageType> createDamage(String id) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, YoukaisHomecoming.loc(id));
	}

	public static DamageSource koishi(LivingEntity target, Vec3 source) {
		return new DamageSource(target.level().registryAccess()
				.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KOISHI),
				source);
	}

	public static DamageSource rumia(LivingEntity self) {
		return new DamageSource(self.level().registryAccess()
				.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RUMIA), self);
	}

	public static DamageSource danmaku(IYHDanmaku self) {
		return new DanmakuDamageSource(self.self().level().registryAccess()
				.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANMAKU), self.self(),
				rootOwner(self.self().getOwner()));
	}

	public static DamageSource abyssal(IYHDanmaku self) {
		return new DanmakuDamageSource(self.self().level().registryAccess()
				.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ABYSSAL), self.self(),
				rootOwner(self.self().getOwner()));
	}

	private static Entity rootOwner(Entity owner) {
		Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		while (owner instanceof TraceableEntity traceable && seen.add(owner)) {
			Entity next = traceable.getOwner();
			if (next == null) break;
			owner = next;
		}
		return owner;
	}

	private static final class DanmakuDamageSource extends DamageSource {
		private final boolean bossOwned;

		private DanmakuDamageSource(Holder<DamageType> type, Entity direct, Entity owner) {
			super(type, direct, owner);
			this.bossOwned = owner instanceof BossYoukaiEntity;
		}

		@Override
		public Component getLocalizedDeathMessage(LivingEntity victim) {
			return Component.translatable(bossOwned ? "death.attack.danmaku.boss" : "death.attack.danmaku.other",
					victim.getDisplayName());
		}
	}


}
