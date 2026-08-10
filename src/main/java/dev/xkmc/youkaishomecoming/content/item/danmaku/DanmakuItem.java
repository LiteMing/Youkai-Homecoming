package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.fastprojectileapi.render.core.ProjTypeHolder;
import dev.xkmc.fastprojectileapi.render.type.ButterflyProjectileType;
import dev.xkmc.fastprojectileapi.render.type.CrossProjectileType;
import dev.xkmc.fastprojectileapi.render.type.GiantSphereProjectileType;
import dev.xkmc.fastprojectileapi.render.type.GiantYinYangSphereProjectileType;
import dev.xkmc.fastprojectileapi.render.type.LayeredCrossProjectileType;
import dev.xkmc.fastprojectileapi.render.type.LayeredRotatingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.LayeredSwingingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.RenderableProjectileType;
import dev.xkmc.fastprojectileapi.render.type.RotatingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.SimpleProjectileType;
import dev.xkmc.fastprojectileapi.render.type.SwingingProjectileType;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.item.curio.hat.TouhouHatItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.events.EffectEventHandlers;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class DanmakuItem extends Item {

	public static final String COLOR_TAG = "DanmakuColor";

	// Track all instances for render cache reset (dev hotswap support)
	private static final List<WeakReference<DanmakuItem>> ALL_INSTANCES = new ArrayList<>();

	public static void resetRenderCache() {
		ProjTypeHolder.reset();
		for (var ref : ALL_INSTANCES) {
			var item = ref.get();
			if (item != null) item.render = null;
		}
		// Re-initialize all render types and re-run setup
		for (var ref : ALL_INSTANCES) {
			var item = ref.get();
			if (item != null) item.getTypeForRender();
		}
		dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper.setup();
	}

	public final YHDanmaku.Bullet type;
	public final DyeColor color;
	public final float size;

	public DanmakuItem(Properties pProperties, YHDanmaku.Bullet type, DyeColor color, float size) {
		super(pProperties);
		this.type = type;
		this.color = color;
		this.size = size;
		ALL_INSTANCES.add(new WeakReference<>(this));
	}

	public static ItemStack withColor(ItemStack stack, DanmakuColor color) {
		stack.getOrCreateTag().putInt(COLOR_TAG, color.argb());
		return stack;
	}

	public static DanmakuColor getColor(ItemStack stack) {
		if (stack.hasTag() && stack.getTag().contains(COLOR_TAG)) {
			return new DanmakuColor(stack.getTag().getInt(COLOR_TAG));
		}
		if (stack.getItem() instanceof DanmakuItem item) {
			if (item.type.usesDyeTextures()) {
				return DanmakuColor.WHITE;
			}
			return DanmakuColor.of(item.color);
		}
		return DanmakuColor.WHITE;
	}

	public boolean isGiant() {
		return type.category == YHDanmaku.BulletCategory.GIANT;
	}

	public ResourceLocation giantOverlayTexture() {
		if (type == YHDanmaku.Bullet.GIANT_YINYANG) {
			return YoukaisHomecoming.loc("textures/entities/bullet/moon/moon.png");
		}
		return YoukaisHomecoming.loc("textures/entities/bullet/" + type.texturePath(color) + ".png");
	}

	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (GrazeHelper.forbidDanmakuWithMessage(player))
			return InteractionResultHolder.fail(stack);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
				SoundSource.PLAYERS,
				0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!level.isClientSide) {
			ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), player, level);
			danmaku.setItem(stack);
			danmaku.setTint(getColor(stack).argb());
			danmaku.setup(type.damage(), 40, false, type.bypass(),
					RayTraceUtil.getRayTerm(Vec3.ZERO, player.getXRot(), player.getYRot(), 2));
			level.addFreshEntity(danmaku);
			if (player instanceof ServerPlayer sp)
				SpellContainer.track(sp, danmaku, GrazeCapability.HOLDER.get(sp).isInDanmakuCombat());
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		int cooldown = YHModConfig.COMMON.playerDanmakuCooldown.get();
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (head.is(YHTagGen.TOUHOU_HAT) && head.getItem() instanceof TouhouHatItem item && item.support(color)) {
			// Hat bonus: no item/buff cost, half cooldown
			player.getCooldowns().addCooldown(this, cooldown / 2);
		} else {
			player.getCooldowns().addCooldown(this, cooldown);
			if (!player.getAbilities().instabuild) {
				// Try consuming buff duration as mana; if no buff, consume item
				if (!EffectEventHandlers.consumeDanmakuBuffCost(player)) {
					stack.shrink(1);
				}
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		var fying = Component.translatable(YHEffects.YOUKAIFYING.get().getDescriptionId());
		var fied = Component.translatable(YHEffects.YOUKAIFIED.get().getDescriptionId());
		list.add(YHLangData.USAGE_DANMAKU.get(fying, fied));
		list.add(YHLangData.DANMAKU_DAMAGE.get(type.damage()));
		if (type.bypass())
			list.add(YHLangData.DANMAKU_BYPASS.get());
	}

	private ProjTypeHolder<? extends RenderableProjectileType<?, ?>, ?> render;

	public ProjTypeHolder<? extends RenderableProjectileType<?, ?>, ?> getTypeForRender() {
		if (render == null) {
			var loc = YoukaisHomecoming
					.loc("textures/entities/bullet/" + type.texturePath(color) + ".png");
			var white = YoukaisHomecoming
					.loc("textures/entities/bullet/" + type.whiteOverlayTexturePath() + ".png");
			RenderableProjectileType<?, ?> r = switch (type) {
				case BUTTERFLY -> new ButterflyProjectileType(loc, type.display(), 20);
				case SPARK -> new RotatingProjectileType(loc, type.display(), 20);
				case STAR -> new RotatingProjectileType(loc, type.display(), 40);
				case YINYANG_2D -> type.usesWhiteOverlayTint() ?
						new LayeredRotatingProjectileType(loc, white, type.display(), 80) :
						new RotatingProjectileType(loc, type.display(), 80);
				// Swinging 3D bullets (rotations per block, tilt angle in degrees, size in blocks)
				case TALISMAN -> type.usesWhiteOverlayTint() ?
						new LayeredSwingingProjectileType(loc, white, type.display(), 0.05f, 0f, 0.7f) :
						new SwingingProjectileType(loc, type.display(), 0.05f, 0f, 0.7f);
				case SCALE -> new SwingingProjectileType(loc, type.display(), 0.02f, 30f, 0.5f);
				// Cross-shaped bullets (like Minecraft saplings)
				case KUNAI -> type.usesWhiteOverlayTint() ?
						new LayeredCrossProjectileType(loc, white, type.display()) :
						new CrossProjectileType(loc, type.display());
				case KNIFE -> type.usesWhiteOverlayTint() ?
						new LayeredCrossProjectileType(loc, white, type.display()) :
						new CrossProjectileType(loc, type.display());
				// Moon uses the textured sphere; giant yinyang uses a no-UV 3D material split.
				case MOON -> new GiantSphereProjectileType(loc, type.display(), 32, 16, 120);
				case GIANT_YINYANG -> new GiantYinYangSphereProjectileType(
						YoukaisHomecoming.loc("textures/entities/bullet/moon/moon.png"), type.display(), 32, 16, 80);
				default -> new SimpleProjectileType(loc, type.display());
			};
			render = ProjTypeHolder.wrap(Wrappers.cast(r));
		}
		return render;
	}

}
