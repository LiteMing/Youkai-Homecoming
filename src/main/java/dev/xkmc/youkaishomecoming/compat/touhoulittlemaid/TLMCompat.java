package dev.xkmc.youkaishomecoming.compat.touhoulittlemaid;

import com.github.tartaricacid.touhoulittlemaid.entity.monster.EntityFairy;
import com.github.tartaricacid.touhoulittlemaid.item.ItemGarageKit;
import com.github.tartaricacid.touhoulittlemaid.item.ItemCamera;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.MaidRayTraceHelper;
import dev.xkmc.youkaishomecoming.compat.exposure.DanmakuCaptureService;
import dev.xkmc.youkaishomecoming.compat.touhoulittlemaid.fairy.SmallFairy;
import dev.xkmc.youkaishomecoming.content.entity.boss.MystiaEntity;
import dev.xkmc.youkaishomecoming.content.entity.boss.RemiliaEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.game.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.food.YHFood;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TLMCompat {

	/**
	 * TLM's camera only handles maid entity conversion itself. When no maid was
	 * targeted, treat a view containing danmaku as the camera's optional spell
	 * replication shot without changing TLM's item implementation.
	 */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onCameraUse(PlayerInteractEvent.RightClickItem event) {
		if (event.getHand() != InteractionHand.MAIN_HAND
				|| !(event.getItemStack().getItem() instanceof ItemCamera)
				|| !(event.getEntity() instanceof ServerPlayer player)) return;
		if (player.getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
		if (MaidRayTraceHelper.rayTraceMaid(player, 8.0).isPresent()) return;
		var outcome = DanmakuCaptureService.commit(player,
				DanmakuCaptureService.collect(player, 70.0f));
		if (!outcome.success()) return;

		player.getCooldowns().addCooldown(event.getItemStack().getItem(), 20);
		event.getItemStack().hurtAndBreak(1, player,
				p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
		player.playSound(InitSounds.CAMERA_USE.get(), 1.0f, 1.0f);
		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
		if (event.getEntity() instanceof EntityFairy old && YHModConfig.COMMON.smallFairyReplacement.get()) {
			event.setCanceled(true);
			var replacement = new SmallFairy(TLMRegistries.SMALL_FAIRY.get(), old.level());
			TouhouSpellCards.setSpell(replacement, "fairy:" + old.getFairyTypeOrdinal());
			if (old.hasCustomName() && old.isCustomNameVisible()) {
				replacement.setCustomName(old.getCustomName());
				replacement.setCustomNameVisible(true);
			}
			replacement.copyPosition(old);
			old.level().addFreshEntity(replacement);
		}
	}

	@SubscribeEvent
	public static void onInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof GeneralYoukaiEntity e && event.getEntity().isCreative()) {
			if (event.getItemStack().getItem() instanceof ItemGarageKit) {
				if (!event.getTarget().level().isClientSide()) {
					String id = ItemGarageKit.getMaidData(event.getItemStack()).getString("ModelId");
					TouhouSpellCards.setSpell(e, id);
				}
				event.setCancellationResult(InteractionResult.SUCCESS);
				event.setCanceled(true);
			}
		}
		if (event.getTarget() instanceof Bat bat && event.getItemStack().is(YHFood.SCARLET_DEVIL_CAKE.item.get())) {
			if (!event.getTarget().level().isClientSide()) {
				var remilia = new RemiliaEntity(YHEntities.REMILIA.get(), bat.level());
				remilia.moveTo(bat.position());
				remilia.initSpellCard();
				bat.level().addFreshEntity(remilia);
				bat.discard();
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
		if (event.getTarget() instanceof Parrot parrot && event.getItemStack().is(YHFood.RAW_LAMPREY.item.get())) {
			if (!event.getTarget().level().isClientSide()) {
				var mystia = new MystiaEntity(YHEntities.MYSTIA.get(), parrot.level());
				mystia.moveTo(parrot.position());
				mystia.initSpellCard();
				parrot.level().addFreshEntity(mystia);
				parrot.discard();
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

}
