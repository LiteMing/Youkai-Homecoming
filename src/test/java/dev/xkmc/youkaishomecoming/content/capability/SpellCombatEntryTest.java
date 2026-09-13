package dev.xkmc.youkaishomecoming.content.capability;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.xkmc.l2library.capability.conditionals.ConditionalData;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItemCost;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Headless selection/payment regressions; does not start a Forge server or test packet delivery. */
public final class SpellCombatEntryTest {

	private static int checks;
	private static DynamicSpellItem item;

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			runWithoutModRegistration();
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		installEmptyModList();
		CommentedConfig config = CommentedConfig.inMemory();
		YHModConfig.COMMON_SPEC.correct(config);
		YHModConfig.COMMON_SPEC.setConfig(config);
		ForgeRegistry<Item> items = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
		items.unfreeze();
		((MappedRegistry<Item>) BuiltInRegistries.ITEM).unfreeze();
		item = new DynamicSpellItem(new Item.Properties());
		items.register(new ResourceLocation("yh_test", "spell"), item);
		items.freeze();
		BuiltInRegistries.ITEM.freeze();
		testInventoryEligibility();
		testPaymentAndBrokenCards();
		testLastSpellEntry();
		System.out.println("SpellCombatEntryTest: " + checks + " checks passed");
	}

	private static void testInventoryEligibility() throws Exception {
		TestPlayer player = player();
		ItemStack nonSpell = card(SpellCardType.NON_SPELL, false);
		ItemStack normal = card(SpellCardType.NORMAL, true);
		ItemStack draft = card(SpellCardType.NORMAL, false);
		check("empty inventory cannot qualify", !GrazeHelper.hasSpellCard(player));
		player.inventory.items.set(0, new ItemStack(Items.STONE));
		check("ordinary items cannot qualify", !GrazeHelper.hasSpellCard(player));
		player.inventory.items.set(0, nonSpell);
		check("non-spell fixture really is cast-ready", item.isCastReady(nonSpell));
		check("cast-ready non-spell alone cannot qualify", !GrazeHelper.hasSpellCard(player));
		check("non-spell still works as an ordinary attack", GrazeHelper.findNonSpell(player) == nonSpell);
		player.inventory.items.set(9, normal);
		check("non-spell plus normal card qualifies", GrazeHelper.hasSpellCard(player));
		check("Bomb skips the earlier non-spell", GrazeHelper.findSpellCard(player) == normal);
		player.inventory.items.set(9, ItemStack.EMPTY);
		player.inventory.items.set(0, draft);
		check("unfinished normal card cannot qualify", !GrazeHelper.hasSpellCard(player));
		player.inventory.offhand.set(0, normal);
		check("usable offhand card qualifies past a draft", GrazeHelper.hasSpellCard(player));
		check("offhand and inventory selection share the same gate", GrazeHelper.findSpellCard(player) == normal);
		player.inventory.items.set(0, normal);
		ItemStack timeout = card(SpellCardType.TIMEOUT_SPELL, true);
		player.inventory.offhand.set(0, timeout);
		check("mainhand precedes offhand", GrazeHelper.findSpellCard(player) == normal);
		player.inventory.items.set(0, ItemStack.EMPTY);
		check("timeout spell also qualifies", GrazeHelper.hasSpellCard(player) && GrazeHelper.findSpellCard(player) == timeout);
	}

	private static void testPaymentAndBrokenCards() throws Exception {
		TestPlayer player = player();
		ItemStack normal = card(SpellCardType.NORMAL, true);
		player.inventory.items.set(0, normal);
		player.experienceLevel = 0;
		check("unaffordable card cannot enter", !GrazeHelper.hasSpellCard(player));
		check("Bomb also skips the unaffordable card", GrazeHelper.findSpellCard(player).isEmpty());
		player.experienceLevel = 1000;
		check("sufficient XP restores entry eligibility", GrazeHelper.hasSpellCard(player));
		check("selection only quotes and does not spend XP", player.experienceLevel == 1000);
		set(player.graze, "forcedDanmakuCombat", true);
		player.graze.setBomb(0);
		check("combat qualification uses Bomb rather than XP", !GrazeHelper.hasSpellCard(player));
		player.graze.setBomb(100);
		check("affordable combat card remains available", GrazeHelper.hasSpellCard(player));
		player.graze.disableSpellCardForCombat(GrazeHelper.spellCardKey(normal));
		check("broken combat card no longer qualifies", !GrazeHelper.hasSpellCard(player));
		check("Bomb shares the broken-card exclusion", GrazeHelper.findSpellCard(player).isEmpty());
		player.inventory.offhand.set(0, card(SpellCardType.NON_SPELL, false));
		check("non-spell cannot replace a broken card's qualification", !GrazeHelper.hasSpellCard(player));
		player.inventory.items.set(9, card(SpellCardType.TIMEOUT_SPELL, true));
		check("another usable real card restores qualification", GrazeHelper.hasSpellCard(player));
	}

	private static void testLastSpellEntry() throws Exception {
		TestPlayer player = player();
		ItemStack last = card(SpellCardType.LAST_SPELL, true);
		player.inventory.items.set(0, last);
		player.experienceLevel = 0;
		check("last spell can qualify for a new combat without XP", GrazeHelper.hasSpellCard(player));
		check("entry eligibility does not allow out-of-combat casting", !SpellItemCost.canAfford(player, last));
		check("Bomb still cannot select a last spell outside combat", GrazeHelper.findSpellCard(player).isEmpty());
		set(player.graze, "lastSpellCooldownTicks", 20);
		check("last-spell cooldown also blocks entry", !GrazeHelper.hasSpellCard(player));
		set(player.graze, "lastSpellCooldownTicks", 0);
		set(player.graze, "forcedDanmakuCombat", true);
		check("ready last spell is usable inside combat", GrazeHelper.hasSpellCard(player) && GrazeHelper.findSpellCard(player) == last);
		set(player.graze, "lastSpellUsedThisCombat", true);
		check("used last spell cannot keep combat qualified", !GrazeHelper.hasSpellCard(player));
		set(player.graze, "forcedDanmakuCombat", false);
		check("previous combat use does not block the next combat", GrazeHelper.hasSpellCard(player));
	}

	private static ItemStack card(SpellCardType type, boolean complete) {
		ResourceLocation id = new ResourceLocation("yh_test", type.getSerializedName() + (complete ? "_complete" : "_draft"));
		SpellDefinition definition = new SpellDefinition(id,
				new SpellDisplay("Fixture", "", Optional.empty(), Optional.empty()),
				SpellItemForm.NONE.withCardType(type), id, Map.of(), DifficultyProfile.DEFAULT);
		SpellRegistry.register(definition);
		ItemStack stack = DynamicSpellItem.createStack(item, id, false);
		DynamicSpellItem.setComplete(stack, complete);
		return stack;
	}

	private static TestPlayer player() throws Exception {
		TestPlayer player = allocate(TestPlayer.class);
		player.inventory = new Inventory(player);
		player.abilities = new Abilities();
		player.testLevel = allocate(ServerLevel.class);
		player.graze = new TestGraze();
		player.graze.player = player;
		player.conditional = new ConditionalData();
		player.conditional.player = player;
		player.experienceLevel = 1000;
		return player;
	}

	private static final class TestPlayer extends ServerPlayer {
		Inventory inventory;
		Abilities abilities;
		ServerLevel testLevel;
		TestGraze graze;
		ConditionalData conditional;
		private TestPlayer() { super(null, null, null); }
		@Override public Inventory getInventory() { return inventory; }
		@Override public Abilities getAbilities() { return abilities; }
		@Override public Level level() { return testLevel; }
		@Override public ItemStack getItemInHand(InteractionHand hand) {
			return hand == InteractionHand.MAIN_HAND ? inventory.items.get(0) : inventory.offhand.get(0);
		}
		@Override public void displayClientMessage(Component text, boolean actionBar) {}
		@Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
			if (capability == GrazeCapability.CAPABILITY) return LazyOptional.of(() -> graze).cast();
			if (capability == ConditionalData.CAPABILITY) return LazyOptional.of(() -> conditional).cast();
			return LazyOptional.empty();
		}
	}

	private static final class TestGraze extends GrazeCapability {
		@Override public void sync() {}
	}

	private static void installEmptyModList() throws Exception {
		ModList empty = allocate(ModList.class);
		for (Field field : ModList.class.getDeclaredFields()) {
			field.setAccessible(true);
			if (Modifier.isStatic(field.getModifiers())) {
				if (field.getType() == ModList.class) field.set(null, empty);
			} else if (field.getType() == Map.class) field.set(empty, Map.of());
			else if (field.getType() == List.class) field.set(empty, List.of());
		}
	}

	private static void set(GrazeCapability target, String name, Object value) throws Exception {
		Field field = GrazeCapability.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocate(Class<T> type) throws Exception {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (T) ((Unsafe) field.get(null)).allocateInstance(type);
	}

	private static void check(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
		checks++;
	}

	/** Forge normally rewrites token lookups to the same named capability lookup at load time. */
	@SuppressWarnings("unchecked")
	public static <T> Capability<T> capability(CapabilityToken<T> token) {
		try {
			String type = ((ParameterizedType) token.getClass().getGenericSuperclass())
					.getActualTypeArguments()[0].getTypeName().replace('.', '/');
			var get = CapabilityManager.class.getDeclaredMethod("get", String.class, boolean.class);
			get.setAccessible(true);
			return (Capability<T>) get.invoke(CapabilityManager.INSTANCE, type, false);
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
	}

	/** Supply Forge's capability-token type names and omit mod entrypoint registration. */
	private static void runWithoutModRegistration() throws Exception {
		URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(path -> {
			try { return Path.of(path).toUri().toURL(); }
			catch (IOException error) { throw new IllegalArgumentException(error); }
		}).toArray(URL[]::new);
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(urls, previous) {
			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (!name.startsWith("dev.xkmc.")) return super.loadClass(name, resolve);
				synchronized (getClassLoadingLock(name)) {
					Class<?> loaded = findLoadedClass(name);
					if (loaded == null) loaded = findClass(name);
					if (resolve) resolveClass(loaded);
					return loaded;
				}
			}
			@Override protected Class<?> findClass(String name) throws ClassNotFoundException {
				try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
					if (input == null) throw new ClassNotFoundException(name);
					byte[] original = input.readAllBytes();
					ClassReader reader = new ClassReader(original);
					boolean entrypoint = name.equals("dev.xkmc.youkaishomecoming.init.YoukaisHomecoming")
							|| name.equals("dev.xkmc.l2library.init.L2Library");
					ClassWriter writer = new ClassWriter(0);
					reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
						@Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
							if (entrypoint && method.equals("<clinit>")) return null;
							return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
								@Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
									if (opcode == Opcodes.INVOKESTATIC && owner.equals("net/minecraftforge/common/capabilities/CapabilityManager")
											&& name.equals("get")) {
										owner = "dev/xkmc/youkaishomecoming/content/capability/SpellCombatEntryTest";
										name = "capability";
									}
									super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
								}
							};
						}
					}, 0);
					byte[] bytes = writer.toByteArray();
					return defineClass(name, bytes, 0, bytes.length);
				} catch (IOException error) { throw new ClassNotFoundException(name, error); }
			}
		}) {
			Thread.currentThread().setContextClassLoader(loader);
			loader.loadClass(SpellCombatEntryTest.class.getName()).getMethod("main", String[].class)
					.invoke(null, (Object) new String[]{"loaded"});
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}
}
