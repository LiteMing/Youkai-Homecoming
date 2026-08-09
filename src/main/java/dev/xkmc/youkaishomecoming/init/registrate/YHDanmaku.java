package dev.xkmc.youkaishomecoming.init.registrate;

import com.mojang.serialization.Codec;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuPoofParticleOptions;
import dev.xkmc.youkaishomecoming.content.item.danmaku.CustomSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.custom.data.HomingSpellFormData;
import dev.xkmc.youkaishomecoming.content.spell.custom.data.RingSpellFormData;
import dev.xkmc.youkaishomecoming.content.spell.player.*;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

public class YHDanmaku {

	public interface IDanmakuType {
		int damage();
	}

	public enum Bullet implements IDanmakuType {
		CIRCLE(1, 4, DisplayType.SOLID),
		BALL(1, 4, DisplayType.SOLID),
		MENTOS(2, 6, DisplayType.SOLID),
		BUBBLE(4, 8, DisplayType.ADDITIVE),
		BUTTERFLY(1, 4, DisplayType.TRANSPARENT),
		SPARK(1, 4, DisplayType.SOLID),
		STAR(2, 6, DisplayType.TRANSPARENT),
		YINYANG_2D("yinyang-2d", 2, 6, DisplayType.TRANSPARENT, BulletCategory.NORMAL,
				BulletColorMode.TINTED_WITH_WHITE, "tint", "giant_yinyang"),
		TALISMAN(1.5f, 5, DisplayType.TRANSPARENT, BulletCategory.NORMAL, BulletColorMode.TINTED_WITH_WHITE,
				"tint"),
		KUNAI(1, 4, DisplayType.SOLID, BulletCategory.NORMAL, BulletColorMode.TINTED_WITH_WHITE,
				"tint"),
		SCALE(1, 4, DisplayType.TRANSPARENT, BulletCategory.NORMAL, BulletColorMode.TINTED, "white"),
		KNIFE(1.5f, 5, DisplayType.SOLID, BulletCategory.NORMAL, BulletColorMode.TINTED_WITH_WHITE,
				"tint"),
		MOON(8, 16, DisplayType.ADDITIVE, BulletCategory.GIANT, BulletColorMode.TINTED, "moon"),
		GIANT_YINYANG(8, 14, DisplayType.TRANSPARENT, BulletCategory.GIANT, BulletColorMode.TINTED, "white"),
		;

		public final String name;
		public final TagKey<Item> tag;
		public final float size;
		public final BulletCategory category;
		private final int damage;
		private final DisplayType display;
		private final BulletColorMode colorMode;
		private final String fixedTexture;
		private final String textureFolder;

		Bullet(float size, int damage, DisplayType display) {
			this(null, size, damage, display, BulletCategory.NORMAL, BulletColorMode.DYE_TEXTURES, null, null);
		}

		Bullet(float size, int damage, DisplayType display, BulletCategory category, BulletColorMode colorMode, String fixedTexture) {
			this(null, size, damage, display, category, colorMode, fixedTexture, null);
		}

		Bullet(String name, float size, int damage, DisplayType display, BulletCategory category,
			   BulletColorMode colorMode, String fixedTexture, String textureFolder) {
			this.size = size;
			this.damage = damage;
			this.display = display;
			this.category = category;
			this.colorMode = colorMode;
			this.fixedTexture = fixedTexture;
			this.textureFolder = textureFolder;
			this.name = name == null ? name().toLowerCase(Locale.ROOT) : name;
			tag = YHTagGen.item("danmaku/" + this.name);
		}

		public ItemEntry<DanmakuItem> get(DyeColor color) {
			return YHDanmaku.DANMAKU[ordinal()][color.ordinal()];
		}

		public ItemEntry<DanmakuItem> item() {
			return YHDanmaku.BASE_DANMAKU[ordinal()];
		}

		public ItemStack stack(DanmakuColor color) {
			return DanmakuItem.withColor(item().asStack(), color);
		}

		public int damage() {
			return damage;
		}

		public boolean bypass() {
			return size > 1;
		}

		public String getName() {
			return name;
		}

		public DisplayType display() {
			return display;
		}

		public boolean usesDyeTextures() {
			return colorMode == BulletColorMode.DYE_TEXTURES;
		}

		public boolean usesTint() {
			return colorMode == BulletColorMode.TINTED || colorMode == BulletColorMode.TINTED_WITH_WHITE;
		}

		public boolean usesWhiteOverlayTint() {
			return colorMode == BulletColorMode.TINTED_WITH_WHITE;
		}

		public String textureName(DyeColor color) {
			return colorMode == BulletColorMode.DYE_TEXTURES ? color.getName() : fixedTexture;
		}

		public String textureFolder() {
			return textureFolder == null ? name : textureFolder;
		}

		public String texturePath(DyeColor color) {
			return textureFolder() + "/" + textureName(color);
		}

		public String whiteOverlayTexturePath() {
			return textureFolder() + "/white_overlay";
		}

		public static Bullet byName(String id) {
			String key = normalize(id);
			for (var e : values()) {
				if (normalize(e.name).equals(key) || normalize(e.name()).equals(key)) return e;
			}
			throw new IllegalArgumentException("Unknown danmaku bullet: " + id);
		}

		private static String normalize(String id) {
			return id.toLowerCase(Locale.ROOT).replace('-', '_');
		}

	}

	public enum BulletCategory {
		NORMAL, GIANT
	}

	public enum BulletColorMode {
		DYE_TEXTURES, TINTED, TINTED_WITH_WHITE, FIXED
	}

	public enum Laser implements IDanmakuType {
		LASER(1, 1, 4), PENCIL(1, 1.75f, 4);

		public final String name;
		public final TagKey<Item> tag;
		public final float size, visualLength;
		private final int damage;

		Laser(float size, float visualLength, int damage) {
			this.size = size;
			this.visualLength = visualLength;
			this.damage = damage;
			name = name().toLowerCase(Locale.ROOT);
			tag = YHTagGen.item("laser/" + name);
		}

		public ItemEntry<LaserItem> get(DyeColor color) {
			return YHDanmaku.LASER[ordinal()][color.ordinal()];
		}

		public int damage() {
			return damage;
		}

		public boolean setupLength() {
			return this != LASER;
		}

		public float visualLength() {
			return visualLength;
		}
	}

	public static final RegistryEntry<CreativeModeTab> TAB = YoukaisHomecoming.REGISTRATE
			.buildModCreativeTab("danmaku", "Youkai's Danmaku",
					e -> e.icon(YHDanmaku.DANMAKU[0][DyeColor.RED.ordinal()]::asStack));

	private static final ItemEntry<DanmakuItem>[][] DANMAKU;
	private static final ItemEntry<DanmakuItem>[] BASE_DANMAKU;

	private static final ItemEntry<LaserItem>[][] LASER;

	public static final RegistryEntry<ParticleType<DanmakuPoofParticleOptions>> POOF;

	public static final ItemEntry<SpellItem> REIMU_SPELL;
	public static final ItemEntry<SpellItem> MARISA_SPELL;
	public static final ItemEntry<SpellItem> SANAE_SPELL;
	public static final ItemEntry<SpellItem> KOISHI_SPELL;
	public static final ItemEntry<SpellItem> MYSTIA_SPELL;
	public static final ItemEntry<SpellItem> REMILIA_SPELL;
	public static final ItemEntry<SpellItem> YUKARI_SPELL_LASER;
	public static final ItemEntry<SpellItem> YUKARI_SPELL_BUTTERFLY;
	public static final ItemEntry<SpellItem> CLOWNPIECE_SPELL;
	public static final ItemEntry<CustomSpellItem> CUSTOM_SPELL_RING;
	public static final ItemEntry<CustomSpellItem> CUSTOM_SPELL_HOMING;
	public static final ItemEntry<DynamicSpellItem> DYNAMIC_SPELL;
	public static final RegistryEntry<RecipeType<dev.xkmc.youkaishomecoming.content.spell.recipe.SpellDraftConversionRecipe>> SPELL_DRAFT_CONVERSION_RT;
	public static final RegistryEntry<RecipeSerializer<dev.xkmc.youkaishomecoming.content.spell.recipe.SpellDraftConversionRecipe>> SPELL_DRAFT_CONVERSION_RS;

	static {

		YoukaisHomecoming.REGISTRATE.defaultCreativeTab(YHDanmaku.TAB.getKey());

		// spell
		{

			CUSTOM_SPELL_RING = YoukaisHomecoming.REGISTRATE
					.item("custom_spell_ring", p -> new CustomSpellItem(p.stacksTo(1), false, RingSpellFormData.FLOWER))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/custom_spell")))
					.tag(YHTagGen.CUSTOM_SPELL)
					.register();

			CUSTOM_SPELL_HOMING = YoukaisHomecoming.REGISTRATE
					.item("custom_spell_homing",
							p -> new CustomSpellItem(p.stacksTo(1), true, HomingSpellFormData.RING))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/custom_spell")))
					.tag(YHTagGen.CUSTOM_SPELL)
					.register();

			DYNAMIC_SPELL = YoukaisHomecoming.REGISTRATE
					.item("dynamic_spell", p -> new DynamicSpellItem(p.stacksTo(1)))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/custom_spell")))
					.color(() -> () -> (stack, i) -> i == 0 ? DynamicSpellItem.getColor(stack).argb() : 0xffffffff)
					.register();

			// boss-drop spell card -> dynamic spell draft conversion
			SPELL_DRAFT_CONVERSION_RT = YoukaisHomecoming.REGISTRATE.recipe("spell_draft_conversion");
			SPELL_DRAFT_CONVERSION_RS = YoukaisHomecoming.REGISTRATE.simple("spell_draft_conversion",
					ForgeRegistries.Keys.RECIPE_SERIALIZERS,
					() -> dev.xkmc.youkaishomecoming.content.spell.recipe.SpellDraftConversionRecipe
							.SpellDraftConversionSerializer.INSTANCE);

			REIMU_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_reimu", p -> new SpellItem(
							p.stacksTo(1), ReimuItemSpell::new, true,
							() -> Bullet.CIRCLE.get(DyeColor.RED).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Reimu's Spellcard \"Innate Dream\"")
					.register();

			MARISA_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_marisa", p -> new SpellItem(
							p.stacksTo(1), MarisaItemSpell::new, false,
							() -> Laser.LASER.get(DyeColor.WHITE).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Marisa's Spellcard \"Master Spark\"")
					.register();

			SANAE_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_sanae", p -> new SpellItem(
							p.stacksTo(1), SanaeItemSpell::new, false,
							() -> Bullet.SPARK.get(DyeColor.GREEN).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Sanae's Spellcard \"Inherited Ritual\"")
					.register();

			MYSTIA_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_mystia", p -> new SpellItem(
							p.stacksTo(1), MystiaItemSpell::new, false,
							() -> Bullet.MENTOS.get(DyeColor.GREEN).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Night Sparrow \"Midnight Chorus Master\"")
					.register();

			KOISHI_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_koishi", p -> new SpellItem(
							p.stacksTo(1), KoishiItemSpell::new, false,
							() -> Laser.LASER.get(DyeColor.BLUE).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Response \"Youkai Polygraph\"")
					.register();

			REMILIA_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_remilia", p -> new SpellItem(
							p.stacksTo(1), RemiliaItemSpell::new, false,
							() -> Bullet.BUBBLE.get(DyeColor.RED).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/" + ctx.getName())))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Scarlet Sign \"Scarlet Meister\"")
					.register();

			YUKARI_SPELL_LASER = YoukaisHomecoming.REGISTRATE
					.item("spell_yukari_laser", p -> new SpellItem(
							p.stacksTo(1), YukariItemSpellLaser::new, false,
							() -> Laser.LASER.get(DyeColor.RED).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/spell_yukari")))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Barrier \"Mesh of Light & Darkness\"")
					.register();

			YUKARI_SPELL_BUTTERFLY = YoukaisHomecoming.REGISTRATE
					.item("spell_yukari_butterfly", p -> new SpellItem(
							p.stacksTo(1), YukariItemSpellButterfly::new, false,
							() -> Bullet.BUTTERFLY.get(DyeColor.MAGENTA).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/spell_yukari")))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Barrier \"Double Black Death Butterfly\"")
					.register();

			CLOWNPIECE_SPELL = YoukaisHomecoming.REGISTRATE
					.item("spell_clownpiece", p -> new SpellItem(
							p.stacksTo(1), ClownItemSpell::new, true,
							() -> Laser.LASER.get(DyeColor.RED).get()))
					.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/spell/spell_clownpiece")))
					.tag(YHTagGen.PRESET_SPELL)
					.lang("Hell Sign \"Star and Stripe\"")
					.register();
		}

		DANMAKU = new ItemEntry[Bullet.values().length][DyeColor.values().length];
		BASE_DANMAKU = new ItemEntry[Bullet.values().length];
		for (var t : Bullet.values()) {
			if (t.usesDyeTextures()) {
				for (var e : DyeColor.values()) {
					var ent = YoukaisHomecoming.REGISTRATE
							.item(e.getName() + "_" + t.name + "_danmaku",
									p -> new DanmakuItem(p.rarity(Rarity.RARE), t, e, t.size))
							.model((ctx, pvd) -> pvd.generated(ctx,
									pvd.modLoc("item/bullet/" + t.texturePath(e))))
							.tag(t.tag)
							.register();
					DANMAKU[t.ordinal()][e.ordinal()] = ent;
				}
				BASE_DANMAKU[t.ordinal()] = DANMAKU[t.ordinal()][DyeColor.WHITE.ordinal()];
			} else {
				var builder = YoukaisHomecoming.REGISTRATE
						.item(t.name + "_danmaku",
								p -> new DanmakuItem(p.rarity(t.category == BulletCategory.GIANT ? Rarity.EPIC : Rarity.RARE),
										t, DyeColor.WHITE, t.size))
						.model((ctx, pvd) -> {
							if (t.usesWhiteOverlayTint()) {
								pvd.generated(ctx,
										pvd.modLoc("item/bullet/" + t.texturePath(DyeColor.WHITE)),
										pvd.modLoc("item/bullet/" + t.whiteOverlayTexturePath()));
							} else {
								pvd.generated(ctx,
										pvd.modLoc("item/bullet/" + t.texturePath(DyeColor.WHITE)));
							}
						})
						.tag(t.tag)
						.lang(RegistrateLangProvider.toEnglishName(t.name) + " Danmaku");
				if (t.usesTint()) {
					builder.color(() -> () -> (stack, i) -> i == 0 ? DanmakuItem.getColor(stack).argb() : 0xffffffff);
				}
				var ent = builder.register();
				BASE_DANMAKU[t.ordinal()] = ent;
				for (var e : DyeColor.values()) {
					DANMAKU[t.ordinal()][e.ordinal()] = ent;
				}
			}
		}

		LASER = new ItemEntry[Laser.values().length][DyeColor.values().length];
		for (var t : Laser.values()) {
			for (var e : DyeColor.values()) {
				var ent = YoukaisHomecoming.REGISTRATE
						.item(e.getName() + "_" + t.name, p -> new LaserItem(p.rarity(Rarity.RARE), t, e, 1))
						.model((ctx, pvd) -> pvd.generated(ctx,
								pvd.modLoc("item/danmaku/" + t.name),
								pvd.modLoc("item/danmaku/" + t.name + "_overlay")))
						.color(() -> () -> (stack, i) -> ((LaserItem) stack.getItem()).getDanmakuColor(stack, i))
						.tag(t.tag)
						.register();
				LASER[t.ordinal()][e.ordinal()] = ent;
			}
		}

		POOF = YoukaisHomecoming.REGISTRATE.simple("danmaku_poof",
				ForgeRegistries.Keys.PARTICLE_TYPES,
				() -> new ParticleType<>(false, DanmakuPoofParticleOptions.DESERIALIZER) {
					@Override
					public Codec<DanmakuPoofParticleOptions> codec() {
						return DanmakuPoofParticleOptions.CODEC;
					}
				});

	}

	public static void register() {
	}

}
