package dev.xkmc.youkaishomecoming.init;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.platform.InputConstants;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.youkaishomecoming.compat.touhoulittlemaid.TLMRenderHandler;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMClientCompat;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig;
import dev.xkmc.youkaishomecoming.content.capability.PvpDanmakuStatusOverlay;
import dev.xkmc.youkaishomecoming.content.capability.PowerInfoOverlay;
import dev.xkmc.youkaishomecoming.content.client.*;
import dev.xkmc.youkaishomecoming.content.entity.animal.boar.BoarModel;
import dev.xkmc.youkaishomecoming.content.entity.animal.boar.BoarModelData;
import dev.xkmc.youkaishomecoming.content.entity.animal.crab.CrabModel;
import dev.xkmc.youkaishomecoming.content.entity.animal.crab.CrabModelData;
import dev.xkmc.youkaishomecoming.content.entity.animal.deer.DeerModel;
import dev.xkmc.youkaishomecoming.content.entity.animal.deer.DeerModelData;
import dev.xkmc.youkaishomecoming.content.entity.animal.lampery.LampreyModel;
import dev.xkmc.youkaishomecoming.content.entity.animal.tuna.TunaModel;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuPoofParticle;
import dev.xkmc.youkaishomecoming.content.entity.fairy.CirnoModel;
import dev.xkmc.youkaishomecoming.content.entity.reimu.ReimuModel;
import dev.xkmc.youkaishomecoming.content.entity.rumia.BlackBallModel;
import dev.xkmc.youkaishomecoming.content.entity.rumia.RumiaModel;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.BottleTexture;
import dev.xkmc.youkaishomecoming.content.item.fluid.BottledDrinkSet;
import dev.xkmc.youkaishomecoming.content.item.fluid.SlipBottleItem;
import dev.xkmc.youkaishomecoming.content.spell.client.SpellTitleOverlay;
import dev.xkmc.youkaishomecoming.content.pot.overlay.HintOverlay;
import dev.xkmc.youkaishomecoming.content.pot.overlay.TileClientTooltip;
import dev.xkmc.youkaishomecoming.content.pot.overlay.TileInfoDisplay;
import dev.xkmc.youkaishomecoming.content.pot.overlay.TileTooltip;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class YHClient {

	private static final KeyMapping OPEN_SPELL_EDITOR = new KeyMapping(
			"key.youkaishomecoming.open_spell_editor",
			KeyConflictContext.IN_GAME,
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN,
			"key.categories.youkaishomecoming"
	);

	@SubscribeEvent
	public static void clientSetup(FMLClientSetupEvent event) {
		// 直接在这里注册醉酒效果渲染器
		MinecraftForge.EVENT_BUS.register(DrunkEffectRenderer.class);
		MinecraftForge.EVENT_BUS.register(ClientForgeEvents.class);

		if (YoukaisHomecoming.ENABLE_TLM && ModList.get().isLoaded(TouhouLittleMaid.MOD_ID)) {
			MinecraftForge.EVENT_BUS.register(TLMRenderHandler.class);
		}
		if (ModList.get().isLoaded("exposure")) {
			MinecraftForge.EVENT_BUS.register(dev.xkmc.youkaishomecoming.compat.exposure.DanmakuPhotoOverlay.class);
		}
		event.enqueueWork(() -> {
			ItemProperties.register(YHItems.SAKE_BOTTLE.get(), YoukaisHomecoming.loc("slip"),
					(stack, level, user, index) -> SlipBottleItem.texture(stack));
			ItemProperties.register(YHItems.SAKE_BOTTLE.get(), YoukaisHomecoming.loc("bottle"),
					(stack, level, user, index) -> BottleTexture.texture(stack));
			for (var e : YHDanmaku.Bullet.values()) {
				if (e.usesDyeTextures()) {
					for (var d : DyeColor.values())
						e.get(d).get().getTypeForRender();
				} else {
					e.item().get().getTypeForRender();
				}
			}
			for (var e : YHDanmaku.Laser.values())
				for (var d : DyeColor.values())
					e.get(d).get().getTypeForRender();
			ProjectileRenderHelper.setup();
		});

	}

	@SubscribeEvent
	public static void registerItemDeco(RegisterItemDecorationsEvent event) {
		var deco = new DanmakuItemDeco();
		for (var e : YHDanmaku.Bullet.values()) {
			if (e.usesDyeTextures()) {
				for (var col : DyeColor.values()) {
					event.register(e.get(col), deco);
				}
			} else {
				event.register(e.item(), deco);
			}
		}
		for (var col : DyeColor.values()) {
			for (var e : YHDanmaku.Laser.values()) {
				event.register(e.get(col), deco);
			}
		}
		event.register(YHDanmaku.DYNAMIC_SPELL.get(), deco);
		for (var e : SpellItem.LIST) {
			event.register(e, deco);
		}
	}

	@SubscribeEvent
	public static void registerParticle(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(YHDanmaku.POOF.get(), DanmakuPoofParticle.Provider::new);
	}

	@SubscribeEvent
	public static void registerOverlay(RegisterGuiOverlaysEvent event) {
		event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "info_tile", new TileInfoDisplay());
		event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "cuisine_hint", new HintOverlay());
		event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "power_info", new PowerInfoOverlay());
		event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "pvp_danmaku_status", new PvpDanmakuStatusOverlay());
		event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "spell_title", new SpellTitleOverlay());
		event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "ysm_debug", YSMClientCompat::renderDebugOverlay);
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(OPEN_SPELL_EDITOR);
	}

	@SubscribeEvent
	public static void registerClientTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(TileTooltip.class, TileClientTooltip::new);
	}

	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(CamelliaHeadDeco.LAYER_LOCATION, CamelliaHeadDeco::createBodyLayer);
		event.registerLayerDefinition(LampreyModel.LAYER_LOCATION, LampreyModel::createBodyLayer);
		event.registerLayerDefinition(TunaModel.LAYER_LOCATION, TunaModel::createBodyLayer);
		event.registerLayerDefinition(DeerModel.LAYER_LOCATION, DeerModelData::createBodyLayer);
		event.registerLayerDefinition(BoarModel.LAYER_LOCATION, BoarModelData::createBodyLayer);
		event.registerLayerDefinition(CrabModel.LAYER_LOCATION, CrabModelData::createBodyLayer);
		event.registerLayerDefinition(SuwakoHatModel.SUWAKO, SuwakoHatModel::createSuwakoHat);
		event.registerLayerDefinition(SuwakoHatModel.STRAW, SuwakoHatModel::createStrawHat);
		event.registerLayerDefinition(FrogStrawHatModel.STRAW, FrogStrawHatModel::createHat);
		event.registerLayerDefinition(KoishiHatModel.HAT, KoishiHatModel::createHat);
		event.registerLayerDefinition(RumiaModel.LAYER_LOCATION, RumiaModel::createBodyLayer);
		event.registerLayerDefinition(RumiaModel.HAIRBAND, RumiaModel::createHairbandLayer);
		event.registerLayerDefinition(BlackBallModel.LAYER_LOCATION, BlackBallModel::createBodyLayer);
		event.registerLayerDefinition(ReimuModel.LAYER_LOCATION, ReimuModel::createBodyLayer);
		event.registerLayerDefinition(ReimuModel.HAT_LOCATION, ReimuModel::createHatLayer);
		event.registerLayerDefinition(CirnoModel.LAYER_LOCATION, CirnoModel::createBodyLayer);
		event.registerLayerDefinition(CirnoModel.HAT_LOCATION, CirnoModel::createHatLayer);
		event.registerLayerDefinition(CirnoModel.WINGS_LOCATION, CirnoModel::createWingsLayer);
	}

	@SubscribeEvent
	public static void registerRecipeTab(RegisterRecipeBookCategoriesEvent event) {
		event.registerBookCategories(YoukaisHomecoming.MOKA, List.of(YHRecipeCategories.MOKA.get()));
		event.registerRecipeCategoryFinder(YHBlocks.MOKA_RT.get(), e -> YHRecipeCategories.MOKA.get());
		event.registerBookCategories(YoukaisHomecoming.KETTLE, List.of(YHRecipeCategories.KETTLE.get()));
		event.registerRecipeCategoryFinder(YHBlocks.KETTLE_RT.get(), e -> YHRecipeCategories.KETTLE.get());
	}

	@SubscribeEvent
	public static void addLayer(EntityRenderersEvent.AddLayers event) {
		if (event.getRenderer(EntityType.FROG) instanceof FrogRenderer r) {
			r.addLayer(new FrogHatLayer<>(r, event.getEntityModels()));
		}
		if (YoukaisHomecoming.ENABLE_TLM && ModList.get().isLoaded(TouhouLittleMaid.MOD_ID)) {
			TLMRenderHandler.addLayers(event);
		}
	}

	@SubscribeEvent
	public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener((ResourceManagerReloadListener) YSMCompatConfig::reload);
		event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> registerWingsLayer());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void registerWingsLayer() {
		EntityRenderDispatcher renderManager = Minecraft.getInstance().getEntityRenderDispatcher();
		Map<String, EntityRenderer<? extends Player>> skinMap = renderManager.getSkinMap();
		for (EntityRenderer<? extends Player> renderer : skinMap.values()) {
			if (renderer instanceof LivingEntityRenderer ler) {
				if (ler.getModel() instanceof HumanoidModel<?>) {
					addHumanoidLayers(ler);
				}
			}
		}
		renderManager.renderers.forEach((e, r) -> {
			if (r instanceof LivingEntityRenderer ler) {
				if (ler.getModel() instanceof HumanoidModel<?>) {
					addHumanoidLayers(ler);
				}
			}
		});
	}

	private static <T extends LivingEntity, M extends HumanoidModel<T>> void addHumanoidLayers(LivingEntityRenderer<T, M> ler) {
		var mc = Minecraft.getInstance();
		ler.addLayer(new CirnoWingsLayer<>(ler, mc.getEntityModels()));
		ler.addLayer(new CamelliaHeadLayer<>(ler, mc.getEntityModels()));
	}

	public static class ClientForgeEvents {

		@SubscribeEvent
		public static void onKeyInput(InputEvent.Key event) {
			Minecraft mc = Minecraft.getInstance();
			while (OPEN_SPELL_EDITOR.consumeClick()) {
				if (mc.player != null && mc.player.connection != null && mc.screen == null) {
					mc.player.connection.sendCommand("yhspell editor");
				}
			}
		}

	}

}
