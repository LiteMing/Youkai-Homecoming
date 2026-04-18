package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.item.curio.hat.TouhouHatItem;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.events.EffectEventHandlers;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.network.chat.Component;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TextDanmakuItem extends Item {

	private static final Component DEFAULT_NAME = Component.literal("言弾");
	private static final float MONO_CHAR_LENGTH = 1.0f;
	private static final DyeColor SUPPORT_COLOR = DyeColor.WHITE;

	public TextDanmakuItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (GrazeHelper.forbidDanmaku(player))
			return InteractionResultHolder.fail(stack);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
				SoundSource.PLAYERS,
				0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

		int cooldown = YHModConfig.COMMON.playerDanmakuCooldown.get();
		if (!level.isClientSide) {
			String text = getDanmakuText(stack);
			TextDanmakuEntity danmaku = new TextDanmakuEntity(YHEntities.TEXT_DANMAKU.get(), player, level);
			danmaku.text = text;
			danmaku.backText = text;
			danmaku.textColor = getTextColor(stack);
			danmaku.setup(YHDanmaku.Laser.PENCIL.damage(),
					YHModConfig.COMMON.playerLaserDuration.get(),
					getDanmakuLength(text),
					true,
					player.getYRot(),
					player.getXRot());
			danmaku.setupLength = YHDanmaku.Laser.PENCIL.setupLength();
			level.addFreshEntity(danmaku);
			if (player instanceof ServerPlayer sp)
				SpellContainer.track(sp, danmaku);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (head.is(YHTagGen.TOUHOU_HAT) && head.getItem() instanceof TouhouHatItem item && item.support(SUPPORT_COLOR)) {
			player.getCooldowns().addCooldown(this, cooldown / 2);
		} else {
			player.getCooldowns().addCooldown(this, cooldown);
			if (!player.getAbilities().instabuild && !EffectEventHandlers.consumeDanmakuBuffCost(player)) {
				stack.shrink(1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public Component getName(ItemStack stack) {
		return DEFAULT_NAME.copy();
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		var fying = Component.translatable(YHEffects.YOUKAIFYING.get().getDescriptionId());
		var fied = Component.translatable(YHEffects.YOUKAIFIED.get().getDescriptionId());
		list.add(YHLangData.USAGE_DANMAKU.get(fying, fied));
		list.add(YHLangData.DANMAKU_DAMAGE.get(YHDanmaku.Laser.PENCIL.damage()));
	}

	public int getDanmakuColor(ItemStack stack, int layer) {
		return 0xFFFFFFFF;
	}

	private static String getDanmakuText(ItemStack stack) {
		String text = stack.getHoverName().getString();
		return text.isBlank() ? DEFAULT_NAME.getString() : text;
	}

	private static float getDanmakuLength(String text) {
		int codePoints = text.codePointCount(0, text.length());
		return Math.max(1, codePoints) * MONO_CHAR_LENGTH;
	}

	private static int getTextColor(ItemStack stack) {
		if (stack.hasCustomHoverName()) {
			var color = stack.getHoverName().getStyle().getColor();
			if (color != null) {
				return 0xFF000000 | color.getValue();
			}
		}
		return 0xFFFFFFFF;
	}

}
