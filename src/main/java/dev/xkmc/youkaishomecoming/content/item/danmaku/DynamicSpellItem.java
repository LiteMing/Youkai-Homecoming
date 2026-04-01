package dev.xkmc.youkaishomecoming.content.item.danmaku;

import dev.xkmc.l2library.util.raytrace.IGlowingTarget;
import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.item.DefinitionItemSpell;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DynamicSpellItem extends Item implements IGlowingTarget, ISpellItem {

	public static final String SPELL_ID_TAG = "spell_id";

	@Nullable
	private final ResourceLocation defaultSpellId;

	public DynamicSpellItem(Properties properties, @Nullable ResourceLocation defaultSpellId) {
		super(properties);
		this.defaultSpellId = defaultSpellId;
	}

	public static ItemStack bind(ItemStack stack, ResourceLocation spellId) {
		stack.getOrCreateTag().putString(SPELL_ID_TAG, spellId.toString());
		return stack;
	}

	@Nullable
	public ResourceLocation getSpellId(ItemStack stack) {
		var tag = stack.getTag();
		if (tag != null && tag.contains(SPELL_ID_TAG)) {
			String id = tag.getString(SPELL_ID_TAG);
			if (ResourceLocation.isValidResourceLocation(id)) {
				return new ResourceLocation(id);
			}
		}
		return defaultSpellId;
	}

	@Nullable
	public SpellDefinition getDefinition(ItemStack stack) {
		ResourceLocation spellId = getSpellId(stack);
		return spellId == null ? null : SpellRegistry.get(spellId);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (GrazeHelper.forbidDanmaku(player))
			return InteractionResultHolder.fail(stack);
		boolean consume = !player.getAbilities().instabuild && !(player instanceof FakePlayer);
		if (!castSpell(stack, player, consume, true)) {
			return InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean castSpell(ItemStack stack, Player player, boolean consume, boolean cooldown) {
		SpellDefinition definition = getDefinition(stack);
		if (definition == null) {
			return false;
		}
		var form = definition.itemForm;
		LivingEntity target = RayTraceUtil.serverGetTarget(player);
		if (target != null) GrazeHelper.addSession(player, target);
		if (form.requiresTarget() && target == null) {
			target = GrazeHelper.getTarget(player);
			if (target == null) return false;
		}
		if (consume && !consumeAmmo(definition, player, false)) {
			return false;
		}
		if (player instanceof ServerPlayer sp) {
			if (consume) {
				consumeAmmo(definition, player, true);
			}
			int duration = Math.max(1, form.cooldown());
			SpellContainer.castSpell(sp, () -> new DefinitionItemSpell(definition, duration), target);
			if (cooldown) {
				player.getCooldowns().addCooldown(this, duration);
			}
		}
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		ResourceLocation spellId = getSpellId(stack);
		SpellDefinition definition = getDefinition(stack);
		if (definition != null) {
			list.add(definition.display.displayName());
			if (!definition.display.description().isEmpty()) {
				list.add(definition.display.displayDesc());
			}
			Item ammo = getAmmoItem(definition);
			if (ammo != null) {
				list.add(YHLangData.SPELL_COST.get(1, ammo.getName(ammo.getDefaultInstance())));
			}
			if (definition.itemForm.requiresTarget()) {
				list.add(YHLangData.SPELL_TARGET.get());
			}
		} else if (spellId != null) {
			list.add(Component.literal("Unknown spell: " + spellId));
		} else {
			list.add(Component.literal("Bind NBT spell_id to a registered spell definition"));
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity user, int slot, boolean selected) {
		if (selected && user instanceof Player player && level.isClientSide) {
			RayTraceUtil.clientUpdateTarget(player, 64);
		}
	}

	@Override
	public int getDistance(ItemStack stack) {
		return 64;
	}

	private static boolean consumeAmmo(SpellDefinition definition, Player player, boolean execute) {
		Item ammo = getAmmoItem(definition);
		if (ammo == null) {
			return true;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack inv = player.getInventory().getItem(i);
			if (inv.is(ammo)) {
				if (execute) {
					inv.shrink(1);
				}
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static Item getAmmoItem(SpellDefinition definition) {
		ResourceLocation iconItem = definition.itemForm.iconItem();
		if (iconItem == null) {
			return null;
		}
		Item ammo = ForgeRegistries.ITEMS.getValue(iconItem);
		return ammo == null || ammo == net.minecraft.world.item.Items.AIR ? null : ammo;
	}
}
