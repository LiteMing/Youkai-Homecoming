package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Exposure-independent storage item for danmaku photograph replication progress. */
public class SpellReplicaFilmItem extends Item {
	public SpellReplicaFilmItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
		if (!level.isClientSide && player instanceof ServerPlayer server) {
			if (SpellReplicaService.isComplete(stack)) {
				SpellReplicaService.completeIntoDraft(server, stack);
			} else {
				SpellReplicaService.clearProgress(stack);
				SpellReplicaService.markInventoryChanged(server);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("youkaishomecoming.replica.progress",
				SpellReplicaService.progress(stack)));
	}
}
