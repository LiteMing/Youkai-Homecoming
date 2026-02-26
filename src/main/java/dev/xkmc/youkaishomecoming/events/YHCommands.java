package dev.xkmc.youkaishomecoming.events;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YHCommands {

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(literal("danmaku")
				.requires(e -> e.hasPermission(2))
				.then(literal("resetRender")
						.requires(e -> e.hasPermission(2))
						.executes(ctx -> {
							if (FMLEnvironment.dist.isClient()) {
								net.minecraft.client.Minecraft.getInstance().execute(() -> {
									DanmakuItem.resetRenderCache();
									var player = net.minecraft.client.Minecraft.getInstance().player;
									if (player != null) {
										player.displayClientMessage(
												Component.literal("[YH] Danmaku render cache reset."), true);
									}
								});
							}
							ctx.getSource().sendSystemMessage(Component.literal("[YH] Danmaku render cache reset scheduled."));
							return 1;
						}))
				.then(argument("player", EntityArgument.players())
						.then(literal("setLife")
								.requires(e -> e.hasPermission(2))
								.then(argument("life", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int life = ctx.getArgument("life", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setLife(life * 5);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setBomb")
								.requires(e -> e.hasPermission(2))
								.then(argument("bomb", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int bomb = ctx.getArgument("bomb", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setBomb(bomb * 5);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))
						.then(literal("setPower")
								.requires(e -> e.hasPermission(2))
								.then(argument("power", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											EntitySelector sel = ctx.getArgument("player", EntitySelector.class);
											var player = sel.findSinglePlayer(ctx.getSource());
											int power = ctx.getArgument("power", Integer.class);
											var cap = GrazeCapability.HOLDER.get(player);
											cap.setPower(power * 100);
											ctx.getSource().sendSystemMessage(Component.literal("Completed"));
											return 0;
										})))

				));
	}

	protected static LiteralArgumentBuilder<CommandSourceStack> literal(String str) {
		return LiteralArgumentBuilder.literal(str);
	}

	protected static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

}
