package dev.xkmc.youkaishomecoming.init.registrate;

import com.mojang.serialization.Codec;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuPoofParticleOptions;
import dev.xkmc.youkaishomecoming.content.item.danmaku.CustomSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItem;
import dev.xkmc.youkaishomecoming.content.spell.custom.data.HomingSpellFormData;
import dev.xkmc.youkaishomecoming.content.spell.custom.data.RingSpellFormData;
import dev.xkmc.youkaishomecoming.content.spell.player.*;
import dev.xkmc.youkaishomecoming.content.spell.registry.SpellItemAutoRegister;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHTagGen;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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
		// Animated sequence frame bullets
		ROSE(1, 4, DisplayType.TRANSPARENT),
		// Swinging 3D bullets
		TALISMAN(1.5f, 5, DisplayType.TRANSPARENT),
		KUNAI(1, 4, DisplayType.SOLID),
		SCALE(1, 4, DisplayType.TRANSPARENT),
		KNIFE(1.5f, 5, DisplayType.SOLID),
		// Large bullets (separate registration, not 16 colors)
		MOON(8, 16, DisplayType.ADDITIVE),
		GIANT_YINYANG(8, 14, DisplayType.TRANSPARENT),
		;

		public final String name;
		public final TagKey<Item> tag;
		public final float size;
		private final int damage;
		private final DisplayType display;

		Bullet(float size, int damage, DisplayType display) {
			this.size = size;
			this.damage = damage;
			this.display = display;
			name = name().toLowerCase(Locale.ROOT);
			tag = YHTagGen.item("danmaku/" + name);
		}

		public ItemEntry<DanmakuItem> get(DyeColor color) {
			return YHDanmaku.DANMAKU[ordinal()][color.ordinal()];
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

		/**
		 * Returns true for bullets that should be registered separately
		 * based on specific texture files, not all 16 dye colors.
		 */
		public boolean isSpecial() {
			return this == MOON || this == GIANT_YINYANG || this == ROSE;
		}

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
	public static final ItemEntry<DynamicSpellItem> DYNAMIC_SPELL;
	public static final ItemEntry<CustomSpellItem> CUSTOM_SPELL_RING;
	public static final ItemEntry<CustomSpellItem> CUSTOM_SPELL_HOMING;

	// Special bullets (not 16 colors)
	public static final ItemEntry<DanmakuItem> ROSE_DANMAKU;
	public static final ItemEntry<DanmakuItem> MOON_DANMAKU;
	public static final ItemEntry<DanmakuItem> GIANT_YINYANG_RED;
	public static final ItemEntry<DanmakuItem> GIANT_YINYANG_BLUE;

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

			DYNAMIC_SPELL = SpellItemAutoRegister.registerDynamic(
					"spell_dynamic",
					null,
					"item/spell/custom_spell",
					"Dynamic Spellcard");

			REIMU_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_reimu",
					ReimuItemSpell::new,
					true,
					() -> Bullet.CIRCLE.get(DyeColor.RED).get(),
					"item/spell/spell_reimu",
					"Reimu's Spellcard \"Innate Dream\""));

			MARISA_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_marisa",
					MarisaItemSpell::new,
					false,
					() -> Laser.LASER.get(DyeColor.WHITE).get(),
					"item/spell/spell_marisa",
					"Marisa's Spellcard \"Master Spark\""));

			SANAE_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_sanae",
					SanaeItemSpell::new,
					false,
					() -> Bullet.SPARK.get(DyeColor.GREEN).get(),
					"item/spell/spell_sanae",
					"Sanae's Spellcard \"Inherited Ritual\""));

			MYSTIA_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_mystia",
					MystiaItemSpell::new,
					false,
					() -> Bullet.MENTOS.get(DyeColor.GREEN).get(),
					"item/spell/spell_mystia",
					"Night Sparrow \"Midnight Chorus Master\""));

			KOISHI_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_koishi",
					KoishiItemSpell::new,
					false,
					() -> Laser.LASER.get(DyeColor.BLUE).get(),
					"item/spell/spell_koishi",
					"Response \"Youkai Polygraph\""));

			REMILIA_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_remilia",
					RemiliaItemSpell::new,
					false,
					() -> Bullet.BUBBLE.get(DyeColor.RED).get(),
					"item/spell/spell_remilia",
					"Scarlet Sign \"Scarlet Meister\""));

			YUKARI_SPELL_LASER = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_yukari_laser",
					YukariItemSpellLaser::new,
					false,
					() -> Laser.LASER.get(DyeColor.RED).get(),
					"item/spell/spell_yukari",
					"Barrier \"Mesh of Light & Darkness\""));

			YUKARI_SPELL_BUTTERFLY = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_yukari_butterfly",
					YukariItemSpellButterfly::new,
					false,
					() -> Bullet.BUTTERFLY.get(DyeColor.MAGENTA).get(),
					"item/spell/spell_yukari",
					"Barrier \"Double Black Death Butterfly\""));

			CLOWNPIECE_SPELL = SpellItemAutoRegister.registerLegacy(new SpellItemAutoRegister.LegacySpellItemSpec(
					"spell_clownpiece",
					ClownItemSpell::new,
					true,
					() -> Laser.LASER.get(DyeColor.RED).get(),
					"item/spell/spell_clownpiece",
					"Hell Sign \"Star and Stripe\""));
		}

		DANMAKU = new ItemEntry[Bullet.values().length][DyeColor.values().length];
		for (var t : Bullet.values()) {
			// Skip special bullets - they are registered separately
			if (t.isSpecial())
				continue;
			for (var e : DyeColor.values()) {
				var ent = YoukaisHomecoming.REGISTRATE
						.item(e.getName() + "_" + t.name + "_danmaku",
								p -> new DanmakuItem(p.rarity(Rarity.RARE), t, e, t.size))
						.model((ctx, pvd) -> pvd.generated(ctx,
								pvd.modLoc("item/bullet/" + t.name + "/" + e.getName())))
						.tag(t.tag)
						.register();
				DANMAKU[t.ordinal()][e.ordinal()] = ent;
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

		// Register special bullets with specific textures
		ROSE_DANMAKU = YoukaisHomecoming.REGISTRATE
				.item("rose_danmaku",
						p -> new DanmakuItem(p.rarity(Rarity.EPIC), Bullet.ROSE, DyeColor.PINK, Bullet.ROSE.size))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bullet/rose/rose")))
				.tag(Bullet.ROSE.tag)
				.register();

		MOON_DANMAKU = YoukaisHomecoming.REGISTRATE
				.item("moon_danmaku",
						p -> new DanmakuItem(p.rarity(Rarity.EPIC), Bullet.MOON, DyeColor.YELLOW, Bullet.MOON.size))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bullet/moon/moon")))
				.tag(Bullet.MOON.tag)
				.register();

		GIANT_YINYANG_RED = YoukaisHomecoming.REGISTRATE
				.item("red_giant_yinyang_danmaku",
						p -> new DanmakuItem(p.rarity(Rarity.EPIC), Bullet.GIANT_YINYANG, DyeColor.RED,
								Bullet.GIANT_YINYANG.size))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bullet/giant_yinyang/red")))
				.tag(Bullet.GIANT_YINYANG.tag)
				.register();

		GIANT_YINYANG_BLUE = YoukaisHomecoming.REGISTRATE
				.item("blue_giant_yinyang_danmaku",
						p -> new DanmakuItem(p.rarity(Rarity.EPIC), Bullet.GIANT_YINYANG, DyeColor.BLUE,
								Bullet.GIANT_YINYANG.size))
				.model((ctx, pvd) -> pvd.generated(ctx, pvd.modLoc("item/bullet/giant_yinyang/blue")))
				.tag(Bullet.GIANT_YINYANG.tag)
				.register();

		// Populate DANMAKU array for special bullets to avoid NullPointerException in
		// Bullet.get()
		// ROSE: single texture for all colors
		for (var e : DyeColor.values()) {
			DANMAKU[Bullet.ROSE.ordinal()][e.ordinal()] = ROSE_DANMAKU;
		}
		// MOON: single texture for all colors
		for (var e : DyeColor.values()) {
			DANMAKU[Bullet.MOON.ordinal()][e.ordinal()] = MOON_DANMAKU;
		}
		// GIANT_YINYANG: red and blue variants
		for (var e : DyeColor.values()) {
			DANMAKU[Bullet.GIANT_YINYANG.ordinal()][e
					.ordinal()] = (e == DyeColor.BLUE || e == DyeColor.LIGHT_BLUE || e == DyeColor.CYAN)
							? GIANT_YINYANG_BLUE
							: GIANT_YINYANG_RED;
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
