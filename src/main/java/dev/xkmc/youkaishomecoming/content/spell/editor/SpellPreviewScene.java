package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderHelper;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.fairy.FairyEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellPreviewHost;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCardWrapper;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SpellPreviewScene {

	private static final Vec3 CASTER_POS = new Vec3(0, 0, 0);
	private static final int FULL_BRIGHT = 0xF000F0;

	private final Minecraft minecraft = Minecraft.getInstance();
	private final List<SimplifiedProjectile> projectiles = new ArrayList<>();
	private final List<PreviewShooterEntity> shooters = new ArrayList<>();
	private final List<Entity> pendingShots = new ArrayList<>();

	@Nullable
	private SpellDefinition definition;
	@Nullable
	private SpellRuntime runtime;
	@Nullable
	private PreviewCasterEntity caster;
	@Nullable
	private LivingEntity casterAvatar;
	@Nullable
	private BossYoukaiEntity target;
	@Nullable
	private String error;

	private boolean stepping;
	private boolean playing = true;
	private ViewMode viewMode = ViewMode.FRONT;
	private SpeedPreset speed = SpeedPreset.X1;
	private ZoomPreset zoom = ZoomPreset.NORMAL;
	private HealthPreset health = HealthPreset.FULL;
	private TargetPreset targetPreset = TargetPreset.MID;

	public void rebuild(SpellDefinition definition) {
		clearAll();
		this.definition = definition;
		ClientLevel level = minecraft.level;
		if (level == null) {
			error = "Client level unavailable";
			return;
		}
		try {
			casterAvatar = createCasterAvatar(level, definition.display.modelId());
			target = createTarget(level);
			caster = new PreviewCasterEntity(level, casterAvatar);
			runtime = new SpellRuntime(definition);
			applyHealthPreset();
			applyTargetPreset();
			error = null;
		} catch (Exception e) {
			clearAll();
			error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
		}
	}

	public void invalidate(String error) {
		clearAll();
		this.error = error;
	}

	public void tick() {
		if (!playing || runtime == null || caster == null || target == null || error != null) {
			return;
		}
		for (int i = 0; i < speed.steps; i++) {
			stepOnce();
		}
	}

	public void stepOnce() {
		if (runtime == null || caster == null || target == null || error != null) {
			return;
		}
		stepping = true;
		try {
			runtime.tick(caster);
			flushPendingShots();
			tickShooters();
			flushPendingShots();
			tickProjectiles();
			flushPendingShots();
		} catch (Exception e) {
			error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
			playing = false;
		} finally {
			stepping = false;
		}
	}

	public void reset() {
		if (definition != null) {
			rebuild(definition);
		}
	}

	public void jumpToPhase(@Nullable ResourceLocation phaseId) {
		if (phaseId == null || runtime == null || caster == null || definition == null) {
			return;
		}
		if (definition.getPhase(phaseId) == null) {
			return;
		}
		try {
			runtime.forceTransition(caster.createContext(runtime), phaseId);
			clearDanmaku();
			error = null;
		} catch (Exception e) {
			error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
		}
	}

	public void render(PoseStack pose, int x, int y, int width, int height, float partialTick) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (error != null || caster == null || target == null) {
			return;
		}
		Vec3 anchor = caster.center();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		Quaternionf orientation = viewMode.orientation();
		float scale = Math.max(4.0f, Math.min((width - 24) / (zoom.halfRange * 2.0f), (height - 24) / (zoom.halfRange * 2.0f)));
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		try (SpellPreviewRenderState.Token ignored = SpellPreviewRenderState.push(orientation)) {
			pose.pushPose();
			pose.translate(x + width / 2.0f, y + height / 2.0f + 10.0f, 200.0f);
			pose.scale(scale, -scale, scale);
			pose.mulPose(orientation);
			pose.translate(-anchor.x, -anchor.y, -anchor.z);
			renderGuide(pose, buffer.getBuffer(RenderType.lines()), anchor);
			renderShooters(pose, buffer.getBuffer(RenderType.lines()), anchor);
			renderProjectiles(pose, partialTick, anchor);
			ProjectileRenderHelper.flush(buffer);
			buffer.endBatch();
			pose.popPose();
		}
		RenderSystem.disableDepthTest();
	}

	public boolean isPlaying() {
		return playing;
	}

	public void togglePlaying() {
		playing = !playing;
	}

	public ViewMode viewMode() {
		return viewMode;
	}

	public void cycleViewMode() {
		viewMode = viewMode.next();
	}

	public SpeedPreset speed() {
		return speed;
	}

	public void cycleSpeed() {
		speed = speed.next();
	}

	public ZoomPreset zoom() {
		return zoom;
	}

	public void cycleZoom() {
		zoom = zoom.next();
	}

	public HealthPreset health() {
		return health;
	}

	public void cycleHealth() {
		health = health.next();
		applyHealthPreset();
	}

	public TargetPreset targetPreset() {
		return targetPreset;
	}

	public void cycleTargetPreset() {
		targetPreset = targetPreset.next();
		applyTargetPreset();
	}

	@Nullable
	public String error() {
		return error;
	}

	public int projectileCount() {
		return projectiles.size();
	}

	public int shooterCount() {
		return shooters.size();
	}

	public int totalTick() {
		return runtime == null ? 0 : runtime.getTotalTick();
	}

	public int phaseTick() {
		return runtime == null ? 0 : runtime.getPhaseTick();
	}

	@Nullable
	public ResourceLocation currentPhase() {
		return runtime == null ? null : runtime.getCurrentPhaseId();
	}

	public Map<String, Double> variables() {
		return runtime == null ? Map.of() : runtime.getVariables();
	}

	public String healthSummary() {
		return health.label;
	}

	public String targetSummary() {
		return targetPreset.label;
	}

	private void clearAll() {
		projectiles.clear();
		shooters.clear();
		pendingShots.clear();
		runtime = null;
		caster = null;
		casterAvatar = null;
		target = null;
		error = null;
	}

	private void clearDanmaku() {
		for (SimplifiedProjectile projectile : projectiles) {
			projectile.markErased(false);
		}
		projectiles.clear();
		shooters.clear();
		pendingShots.clear();
	}

	private void flushPendingShots() {
		if (pendingShots.isEmpty()) {
			return;
		}
		for (Entity entity : pendingShots) {
			addShot(entity);
		}
		pendingShots.clear();
	}

	private void addShot(Entity entity) {
		if (entity instanceof SimplifiedProjectile projectile) {
			projectiles.add(projectile);
		} else if (entity instanceof PreviewShooterEntity shooter) {
			shooters.add(shooter);
		}
	}

	private void tickProjectiles() {
		Iterator<SimplifiedProjectile> iterator = projectiles.iterator();
		while (iterator.hasNext()) {
			SimplifiedProjectile projectile = iterator.next();
			projectile.setOldPosAndRot();
			projectile.tickCount++;
			projectile.tick();
			if (!projectile.isValid()) {
				iterator.remove();
			}
		}
	}

	private void tickShooters() {
		Iterator<PreviewShooterEntity> iterator = shooters.iterator();
		while (iterator.hasNext()) {
			PreviewShooterEntity shooter = iterator.next();
			shooter.setOldPosAndRot();
			shooter.tick();
			if (!shooter.isAlive() || shooter.tickCount >= shooter.lifetime()) {
				iterator.remove();
			}
		}
	}

	private void renderProjectiles(PoseStack pose, float partialTick, Vec3 anchor) {
		for (SimplifiedProjectile projectile : projectiles) {
			renderProjectile(projectile, pose, partialTick, anchor);
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends SimplifiedProjectile> void renderProjectile(T projectile, PoseStack pose, float partialTick, Vec3 anchor) {
		EntityRenderer<T> renderer = (EntityRenderer<T>) minecraft.getEntityRenderDispatcher().getRenderer(projectile);
		if (!(renderer instanceof ProjectileRenderer<?> projectileRenderer)) {
			return;
		}
		ProjectileRenderer<T> typed = (ProjectileRenderer<T>) projectileRenderer;
		Vec3 offset = renderer.getRenderOffset(projectile, partialTick);
		double dx = Mth.lerp(partialTick, projectile.xOld, projectile.getX()) - anchor.x + offset.x();
		double dy = Mth.lerp(partialTick, projectile.yOld, projectile.getY()) - anchor.y + offset.y();
		double dz = Mth.lerp(partialTick, projectile.zOld, projectile.getZ()) - anchor.z + offset.z();
		pose.pushPose();
		pose.translate(dx, dy, dz);
		typed.render(projectile, partialTick, pose);
		pose.popPose();
	}

	private void renderGuide(PoseStack pose, VertexConsumer line, Vec3 anchor) {
		renderLineBox(pose, line, new AABB(anchor.add(-0.2, -0.2, -0.2), anchor.add(0.2, 0.2, 0.2)), 1.0f, 0.6f, 0.2f);
		renderLineBox(pose, line, new AABB(anchor.add(-zoom.halfRange, -0.02, -0.02), anchor.add(zoom.halfRange, 0.02, 0.02)), 0.9f, 0.35f, 0.35f);
		renderLineBox(pose, line, new AABB(anchor.add(-0.02, -zoom.halfRange * 0.75f, -0.02), anchor.add(0.02, zoom.halfRange * 0.75f, 0.02)), 0.35f, 0.9f, 0.35f);
		renderLineBox(pose, line, new AABB(anchor.add(-0.02, -0.02, -zoom.halfRange), anchor.add(0.02, 0.02, zoom.halfRange)), 0.35f, 0.55f, 1.0f);
		if (target != null) {
			Vec3 pos = target.position().add(0, target.getBbHeight() / 2.0f, 0);
			renderLineBox(pose, line, new AABB(pos.add(-0.3, -0.3, -0.3), pos.add(0.3, 0.3, 0.3)), 0.5f, 1.0f, 0.5f);
		}
	}

	private void renderShooters(PoseStack pose, VertexConsumer line, Vec3 anchor) {
		for (PreviewShooterEntity shooter : shooters) {
			Vec3 pos = shooter.position().add(0, shooter.getBbHeight() / 2.0f, 0);
			renderLineBox(pose, line, new AABB(pos.add(-0.25, -0.25, -0.25), pos.add(0.25, 0.25, 0.25)), 1.0f, 0.9f, 0.35f);
			drawLine(pose, line,
					(float) (pos.x - anchor.x), (float) (pos.y - anchor.y), (float) (pos.z - anchor.z),
					(float) (pos.x - anchor.x + shooter.getLookAngle().x * 0.9f),
					(float) (pos.y - anchor.y + shooter.getLookAngle().y * 0.9f),
					(float) (pos.z - anchor.z + shooter.getLookAngle().z * 0.9f),
					1.0f, 0.9f, 0.35f, 1.0f);
		}
	}

	private void renderLineBox(PoseStack pose, VertexConsumer line, AABB box, float r, float g, float b) {
		Vec3 anchor = caster == null ? Vec3.ZERO : caster.center();
		LevelRenderer.renderLineBox(
				pose,
				line,
				box.move(-anchor.x, -anchor.y, -anchor.z),
				r, g, b, 1.0f
		);
	}

	private void drawLine(PoseStack pose, VertexConsumer consumer,
						  float x1, float y1, float z1,
						  float x2, float y2, float z2,
						  float r, float g, float b, float a) {
		Matrix4f poseMat = pose.last().pose();
		Matrix3f normalMat = pose.last().normal();
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = len <= 1.0E-4f ? 0 : dx / len;
		float ny = len <= 1.0E-4f ? 1 : dy / len;
		float nz = len <= 1.0E-4f ? 0 : dz / len;
		consumer.vertex(poseMat, x1, y1, z1).color(r, g, b, a).normal(normalMat, nx, ny, nz).endVertex();
		consumer.vertex(poseMat, x2, y2, z2).color(r, g, b, a).normal(normalMat, nx, ny, nz).endVertex();
	}

	private void applyHealthPreset() {
		if (casterAvatar == null) {
			return;
		}
		casterAvatar.setHealth(Math.max(1.0f, casterAvatar.getMaxHealth() * health.ratio));
	}

	private void applyTargetPreset() {
		if (target == null || caster == null || casterAvatar == null) {
			return;
		}
		Vec3 targetPos = CASTER_POS.add(0, 0, targetPreset.distance);
		caster.setPos(CASTER_POS);
		casterAvatar.setPos(CASTER_POS);
		caster.setDeltaMovement(Vec3.ZERO);
		casterAvatar.setDeltaMovement(Vec3.ZERO);
		target.setPos(targetPos);
		target.setDeltaMovement(Vec3.ZERO);
		target.setHealth(target.getMaxHealth());
		caster.setTarget(target);
		if (casterAvatar instanceof Mob mob) {
			mob.setTarget(target);
		}
	}

	private LivingEntity createCasterAvatar(ClientLevel level, @Nullable ResourceLocation modelId) {
		EntityType<? extends LivingEntity> type = switchAvatar(modelId);
		LivingEntity entity = type.create(level);
		if (entity == null) {
			throw new IllegalStateException("Failed to create preview caster");
		}
		entity.setPos(CASTER_POS);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.setHealth(entity.getMaxHealth());
		if (entity instanceof GeneralYoukaiEntity general) {
			general.spellCard = new SpellCardWrapper();
			general.spellCard.modelId = modelId == null ? null : modelId.toString();
			general.syncModel();
		}
		return entity;
	}

	private BossYoukaiEntity createTarget(ClientLevel level) {
		BossYoukaiEntity entity = YHEntities.GENERAL_YOUKAI.get().create(level);
		if (entity == null) {
			throw new IllegalStateException("Failed to create preview target");
		}
		entity.setPos(CASTER_POS.add(0, 0, targetPreset.distance));
		entity.setDeltaMovement(Vec3.ZERO);
		entity.setHealth(entity.getMaxHealth());
		return entity;
	}

	private EntityType<? extends LivingEntity> switchAvatar(@Nullable ResourceLocation modelId) {
		if (modelId == null) {
			return YHEntities.GENERAL_YOUKAI.get();
		}
		if ("fairy".equals(modelId.getNamespace())) {
			return YHEntities.FAIRY.get();
		}
		String id = modelId.toString();
		return switch (id) {
			case "touhou_little_maid:hakurei_reimu" -> YHEntities.REIMU.get();
			case "touhou_little_maid:yukari_yakumo" -> YHEntities.YUKARI.get();
			case "touhou_little_maid:cirno" -> YHEntities.CIRNO.get();
			case "touhou_little_maid:kochiya_sanae" -> YHEntities.SANAE.get();
			case "touhou_little_maid:kirisame_marisa" -> YHEntities.MARISA.get();
			case "touhou_little_maid:komeiji_koishi" -> YHEntities.KOISHI.get();
			case "touhou_little_maid:remilia_scarlet" -> YHEntities.REMILIA.get();
			case "touhou_little_maid:mystia_lorelei" -> YHEntities.MYSTIA.get();
			case "touhou_little_maid:sunny_milk" -> YHEntities.SUNNY.get();
			case "touhou_little_maid:luna_child" -> YHEntities.LUNA.get();
			case "touhou_little_maid:star_sapphire" -> YHEntities.STAR.get();
			case "touhou_little_maid:eternity_larva" -> YHEntities.LARVA.get();
			case "touhou_little_maid:clownpiece" -> YHEntities.CLOWN.get();
			default -> YHEntities.GENERAL_YOUKAI.get();
		};
	}

	public enum ViewMode {
		FRONT("Front", new Quaternionf()),
		SIDE("Side", Axis.YP.rotationDegrees(90.0f)),
		TOP("Top", Axis.XP.rotationDegrees(90.0f));

		public final String label;
		private final Quaternionf orientation;

		ViewMode(String label, Quaternionf orientation) {
			this.label = label;
			this.orientation = orientation;
		}

		public Quaternionf orientation() {
			return new Quaternionf(orientation);
		}

		public ViewMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum SpeedPreset {
		X1("1x", 1),
		X2("2x", 2),
		X4("4x", 4),
		X8("8x", 8);

		public final String label;
		public final int steps;

		SpeedPreset(String label, int steps) {
			this.label = label;
			this.steps = steps;
		}

		public SpeedPreset next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum ZoomPreset {
		CLOSE("Zoom 12", 12),
		NORMAL("Zoom 18", 18),
		WIDE("Zoom 28", 28);

		public final String label;
		public final float halfRange;

		ZoomPreset(String label, float halfRange) {
			this.label = label;
			this.halfRange = halfRange;
		}

		public ZoomPreset next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum HealthPreset {
		FULL("HP 100%", 1.0f),
		HALF("HP 50%", 0.5f),
		LOW("HP 20%", 0.2f);

		public final String label;
		public final float ratio;

		HealthPreset(String label, float ratio) {
			this.label = label;
			this.ratio = ratio;
		}

		public HealthPreset next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum TargetPreset {
		CLOSE("Target 8", 8.0),
		MID("Target 14", 14.0),
		FAR("Target 24", 24.0);

		public final String label;
		public final double distance;

		TargetPreset(String label, double distance) {
			this.label = label;
			this.distance = distance;
		}

		public TargetPreset next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private final class PreviewCasterEntity extends BossYoukaiEntity implements SpellPreviewHost {

		private final LivingEntity avatar;

		private PreviewCasterEntity(ClientLevel level, LivingEntity avatar) {
			super(YHEntities.GENERAL_YOUKAI.get(), level);
			this.avatar = avatar;
			setPos(avatar.position());
			setHealth(getMaxHealth());
			var attack = getAttribute(Attributes.ATTACK_DAMAGE);
			if (attack != null && avatar.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				attack.setBaseValue(avatar.getAttributeValue(Attributes.ATTACK_DAMAGE));
			}
		}

		@Override
		public LivingEntity self() {
			return avatar;
		}

		@Override
		public void clearSpellPreviewDanmaku() {
			clearDanmaku();
		}

		@Override
		public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
			PreviewDanmakuEntity danmaku = new PreviewDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), shooter(), (ClientLevel) level());
			danmaku.setPos(center());
			danmaku.setItem(type.get(color).asStack());
			danmaku.setup(getDamage(type), life, true, true, vec);
			return danmaku;
		}

		@Override
		public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
			PreviewLaserEntity danmaku = new PreviewLaserEntity(YHEntities.ITEM_LASER.get(), shooter(), (ClientLevel) level());
			danmaku.setItem(type.get(color).asStack());
			danmaku.setup(getDamage(type), life, len, true, vec);
			danmaku.setPos(pos);
			danmaku.setupLength = type.setupLength();
			return danmaku;
		}

		@Override
		public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
			PreviewShooterEntity shooter = new PreviewShooterEntity((ClientLevel) level());
			shooter.setup(this, target, data, spell);
			shooter.setPos(center());
			return shooter;
		}

		@Override
		public void shoot(Entity danmaku) {
			if (stepping) {
				pendingShots.add(danmaku);
			} else {
				addShot(danmaku);
			}
		}

		public SpellContext createContext(SpellRuntime runtime) {
			return new SpellContext(this, definition, runtime, definition.difficulty.resolve(health.ratio));
		}
	}

	private final class PreviewShooterEntity extends ShooterEntity implements SpellPreviewHost {

		@Nullable
		private SpellCard previewCard;

		private PreviewShooterEntity(ClientLevel level) {
			super(YHEntities.SHOOTER.get(), level);
		}

		@Override
		public void setup(@Nullable LivingEntity owner, @Nullable LivingEntity target, ShooterData data, SpellCard card) {
			super.setup(owner, target, data, card);
			this.previewCard = card;
			setHealth(getMaxHealth());
		}

		@Override
		public void tick() {
			super.tick();
			if (previewCard != null && tickCount < lifetime()) {
				previewCard.tick(this);
			}
		}

		@Override
		public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
			PreviewDanmakuEntity danmaku = new PreviewDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), shooter(), (ClientLevel) level());
			danmaku.setPos(center());
			danmaku.setItem(type.get(color).asStack());
			danmaku.setup(getDamage(type), life, true, true, vec);
			return danmaku;
		}

		@Override
		public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
			PreviewLaserEntity danmaku = new PreviewLaserEntity(YHEntities.ITEM_LASER.get(), shooter(), (ClientLevel) level());
			danmaku.setItem(type.get(color).asStack());
			danmaku.setup(getDamage(type), life, len, true, vec);
			danmaku.setPos(pos);
			danmaku.setupLength = type.setupLength();
			return danmaku;
		}

		@Override
		public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
			PreviewShooterEntity shooter = new PreviewShooterEntity((ClientLevel) level());
			shooter.setup(this, target, data, spell);
			shooter.setPos(center());
			return shooter;
		}

		@Override
		public void shoot(Entity danmaku) {
			if (stepping) {
				pendingShots.add(danmaku);
			} else {
				addShot(danmaku);
			}
		}

		@Override
		public void clearSpellPreviewDanmaku() {
			clearDanmaku();
		}
	}

	private static final class PreviewDanmakuEntity extends ItemDanmakuEntity {

		private PreviewDanmakuEntity(EntityType<? extends ItemDanmakuEntity> type, LivingEntity shooter, ClientLevel level) {
			super(type, shooter, level);
		}

		@Override
		public boolean checkBlockHit() {
			return false;
		}

		@Override
		public boolean canHitEntity(Entity target) {
			return false;
		}
	}

	private static final class PreviewLaserEntity extends ItemLaserEntity {

		private PreviewLaserEntity(EntityType<? extends ItemLaserEntity> type, LivingEntity shooter, ClientLevel level) {
			super(type, shooter, level);
		}

		@Override
		public boolean checkBlockHit() {
			return false;
		}

		@Override
		public boolean checkEntityHit() {
			return false;
		}
	}
}
