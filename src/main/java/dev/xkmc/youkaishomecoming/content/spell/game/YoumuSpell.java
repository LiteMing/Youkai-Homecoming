package dev.xkmc.youkaishomecoming.content.spell.game;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.mover.CompositeMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.RectMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.RotateMover;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.ActualSpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.Ticker;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * 魂魄妖梦的符卡实现
 * <p>
 * 核心特色：
 * 1. 二刀流斩击 - 双向交叉斩击弹幕
 * 2. 半灵分身 - 半灵发射弹幕
 * 3. 剑气 - 高速直线弹幕
 * 4. 樱花斩 - 美丽的樱花形弹幕
 * <p>
 * 动态行为：
 * - 距离检测：近距离斩击，远距离剑气
 * - 速度检测：高速玩家用拦截弹幕，低速玩家用包围弹幕
 * - 位置检测：地面用上升斩，空中用下压斩
 * - 血量检测：血量越低弹幕越密集越华丽
 */
@SerialClass
public class YoumuSpell extends ActualSpellCard {

    // 状态追踪
    @SerialClass.SerialField
    private int cooldown;
    @SerialClass.SerialField
    private double rotationAngle = 0;
    @SerialClass.SerialField
    private int comboCount = 0; // 连击计数

    // 玩家状态缓存
    private boolean playerOnGround = false;
    private boolean playerHighSpeed = false;
    private double playerDistance = 20;
    private float bossHpRatio = 1.0f;

    @Override
    public void tick(CardHolder holder) {
        super.tick(holder);
        rotationAngle += 4.5;

        if (cooldown > 0) {
            cooldown--;
        }

        // 更新玩家状态
        updatePlayerState(holder);

        // 更新Boss血量比例
        updateBossHpRatio(holder);

        // 根据状态选择攻击模式
        if (cooldown <= 0) {
            executeAttackPattern(holder);
        }

        // 持续的半灵弹幕 (每 8 tick)
        if (tick % 8 == 0) {
            addTicker(new HanreiShot().init(bossHpRatio));
        }

        // 华丽的樱花斩 (低血量时更频繁)
        int sakuraInterval = bossHpRatio < 0.3f ? 40 : (bossHpRatio < 0.6f ? 60 : 100);
        if (tick % sakuraInterval == 0 && bossHpRatio < 0.8f) {
            addTicker(new SakuraSlash().init(bossHpRatio, rotationAngle));
        }
    }

    private void updatePlayerState(CardHolder holder) {
        if (holder.self() instanceof YoukaiEntity youkai) {
            LivingEntity target = youkai.getTarget();
            if (target != null) {
                playerOnGround = target.onGround();
                Vec3 vel = target.getDeltaMovement();
                double speed = vel.horizontalDistance();
                playerHighSpeed = speed > 0.3;
                playerDistance = holder.center().distanceTo(target.position().add(0, target.getBbHeight() / 2, 0));
            }
        } else {
            Vec3 targetPos = holder.target();
            Vec3 vel = holder.targetVelocity();
            if (targetPos != null) {
                playerDistance = holder.center().distanceTo(targetPos);
            }
            if (vel != null) {
                playerHighSpeed = vel.horizontalDistance() > 0.3;
            }
        }
    }

    private void updateBossHpRatio(CardHolder holder) {
        LivingEntity self = holder.self();
        if (self != null) {
            bossHpRatio = self.getHealth() / self.getMaxHealth();
        }
    }

    private void executeAttackPattern(CardHolder holder) {
        // 血量越低，弹幕越猛烈
        int intensityBonus = bossHpRatio < 0.3f ? 3 : (bossHpRatio < 0.6f ? 2 : 1);
        comboCount = (comboCount + 1) % 5;

        if (playerDistance < 12) {
            // 近距离：连续斩击
            executeCloseRangeAttack(holder, intensityBonus);
            cooldown = Math.max(15, 25 - intensityBonus * 3);
        } else if (playerDistance < 25) {
            // 中距离：二刀流交叉斩
            executeMidRangeAttack(holder, intensityBonus);
            cooldown = Math.max(20, 35 - intensityBonus * 4);
        } else {
            // 远距离：剑气 + 预判弹幕
            executeLongRangeAttack(holder, intensityBonus);
            cooldown = Math.max(25, 45 - intensityBonus * 5);
        }

        // 根据玩家位置添加额外攻击
        if (playerOnGround) {
            // 玩家在地面：上升斩 (逼迫起跳)
            addTicker(new RisingSwordWave().init(bossHpRatio));
        } else {
            // 玩家在空中：下压斩 (限制移动)
            addTicker(new FallingSwordWave().init(bossHpRatio));
        }

        // 高速玩家：拦截弹幕
        if (playerHighSpeed) {
            addTicker(new InterceptSlash().init(bossHpRatio));
        }
    }

    private void executeCloseRangeAttack(CardHolder holder, int intensity) {
        // 快速连续斩击
        addTicker(new RapidSlash().init(intensity, comboCount, rotationAngle));

        // 低血量时追加回旋斩
        if (bossHpRatio < 0.5f) {
            addTicker(new SpinSlash().init(bossHpRatio, rotationAngle));
        }
    }

    private void executeMidRangeAttack(CardHolder holder, int intensity) {
        // 二刀流交叉斩
        addTicker(new CrossSlash().init(1, intensity, rotationAngle));
        addTicker(new CrossSlash().init(-1, intensity, rotationAngle + 90));

        // 低血量追加剑气波
        if (bossHpRatio < 0.6f) {
            addTicker(new SwordWave().init(intensity, rotationAngle));
        }
    }

    private void executeLongRangeAttack(CardHolder holder, int intensity) {
        // 剑气远程攻击
        addTicker(new SwordKi().init(intensity, rotationAngle));

        // 预判弹幕
        if (playerHighSpeed) {
            addTicker(new PredictiveStrike().init(bossHpRatio));
        }

        // 低血量时瞬移斩
        if (bossHpRatio < 0.4f && holder.random().nextDouble() < 0.3) {
            addTicker(new FlashSlash().init(bossHpRatio));
        }
    }

    // ==================== Ticker 实现 ====================

    /**
     * 半灵射击 - 持续发射的半透明弹幕
     */
    @SerialClass
    public static class HanreiShot extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio = 1.0f;

        public HanreiShot init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            var center = holder.center();
            var rand = holder.random();

            // 半灵位置 (在Boss后方环绕)
            double hanreiAngle = card.rotationAngle * 0.5 + 180;
            var o = DanmakuHelper.getOrientation(holder.forward());
            Vec3 hanreiOffset = o.rotateDegrees(hanreiAngle).scale(3);
            Vec3 hanreiPos = center.add(hanreiOffset).add(0, 1.5, 0);

            // 发射半灵弹幕
            int count = hpRatio < 0.3f ? 5 : (hpRatio < 0.6f ? 3 : 2);
            double spread = hpRatio < 0.5f ? 25 : 15;

            Vec3 toTarget = target.subtract(hanreiPos).normalize();
            var ori = DanmakuHelper.getOrientation(toTarget);

            for (int i = 0; i < count; i++) {
                double angle = (i - count / 2.0) * spread;
                Vec3 dir = ori.rotateDegrees(angle, rand.nextGaussian() * 5);
                double speed = 0.8 + rand.nextDouble() * 0.4;
                int life = (int) (60 + playerDistance(holder) * 1.5);

                var e = holder.prepareDanmaku(life, dir.scale(speed),
                        YHDanmaku.Bullet.CIRCLE, DyeColor.LIGHT_BLUE);
                e.setPos(hanreiPos);
                holder.shoot(e);
            }

            super.tick(holder, card);
            return tick > 1;
        }

        private double playerDistance(CardHolder holder) {
            var target = holder.target();
            return target != null ? holder.center().distanceTo(target) : 20;
        }
    }

    /**
     * 快速连续斩击 - 近距离高速弹幕
     */
    @SerialClass
    public static class RapidSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private int intensity;
        @SerialClass.SerialField
        private int combo;
        @SerialClass.SerialField
        private double baseAngle;

        public RapidSlash init(int intensity, int combo, double baseAngle) {
            this.intensity = intensity;
            this.combo = combo;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            if (tick % 2 == 0 && tick < 12) {
                var center = holder.center();
                var toTarget = target.subtract(center).normalize();
                var o = DanmakuHelper.getOrientation(toTarget);
                var rand = holder.random();

                int slashCount = 8 + intensity * 4;
                double arcAngle = 60 + combo * 10;

                for (int i = 0; i < slashCount; i++) {
                    double angle = (i - slashCount / 2.0) * (arcAngle / slashCount);
                    double speed = 1.8 + rand.nextDouble() * 0.5;
                    Vec3 dir = o.rotateDegrees(angle + tick * 5, rand.nextGaussian() * 3);

                    int life = 25 + rand.nextInt(10);
                    var e = holder.prepareDanmaku(life, dir.scale(speed),
                            YHDanmaku.Bullet.MENTOS, DyeColor.WHITE);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > 12;
        }
    }

    /**
     * 二刀流交叉斩 - 双向交错弹幕
     */
    @SerialClass
    public static class CrossSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private int direction;
        @SerialClass.SerialField
        private int intensity;
        @SerialClass.SerialField
        private double baseAngle;

        public CrossSlash init(int direction, int intensity, double baseAngle) {
            this.direction = direction;
            this.intensity = intensity;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            int duration = 20 + intensity * 5;

            if (tick < duration && tick % 2 == 0) {
                var center = holder.center();
                var toTarget = target.subtract(center).normalize();
                var o = DanmakuHelper.getOrientation(toTarget);
                var rand = holder.random();

                // 斜向斩击
                double sweepAngle = -45 + (90.0 * tick / duration) * direction;
                int bladeCount = 6 + intensity * 2;

                for (int i = 0; i < bladeCount; i++) {
                    double offset = (i - bladeCount / 2.0) * 3;
                    double speed = 1.2 + i * 0.1;
                    Vec3 dir = o.rotateDegrees(sweepAngle + offset, rand.nextGaussian() * 2);

                    var e = holder.prepareDanmaku(50, dir.scale(speed),
                            YHDanmaku.Bullet.MENTOS,
                            direction > 0 ? DyeColor.PINK : DyeColor.LIGHT_BLUE);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 回旋斩 - 360度旋转弹幕
     */
    @SerialClass
    public static class SpinSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;
        @SerialClass.SerialField
        private double baseAngle;

        public SpinSlash init(float hpRatio, double baseAngle) {
            this.hpRatio = hpRatio;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            int duration = 30;
            int bulletPerTick = hpRatio < 0.3f ? 6 : (hpRatio < 0.5f ? 4 : 3);

            if (tick < duration) {
                var center = holder.center();
                var rand = holder.random();
                double currentAngle = baseAngle + tick * 20;

                for (int i = 0; i < bulletPerTick; i++) {
                    double angle = currentAngle + i * (360.0 / bulletPerTick);
                    Vec3 dir = new Vec3(Math.cos(Math.toRadians(angle)), 0,
                            Math.sin(Math.toRadians(angle)));
                    double speed = 0.8 + rand.nextDouble() * 0.3;

                    var e = holder.prepareDanmaku(60, dir.scale(speed),
                            YHDanmaku.Bullet.BALL, DyeColor.MAGENTA);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 剑气波 - 扩散型弹幕
     */
    @SerialClass
    public static class SwordWave extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private int intensity;
        @SerialClass.SerialField
        private double baseAngle;

        public SwordWave init(int intensity, double baseAngle) {
            this.intensity = intensity;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            if (tick == 0) {
                var center = holder.center();
                var toTarget = target.subtract(center).normalize();
                var o = DanmakuHelper.getOrientation(toTarget);
                var rand = holder.random();

                int waveCount = 12 + intensity * 6;

                for (int i = 0; i < waveCount; i++) {
                    double angle = 360.0 / waveCount * i + baseAngle;
                    double tilt = Math.sin(Math.toRadians(angle * 3)) * 15;
                    Vec3 dir = o.rotateDegrees(angle, tilt);

                    // 三层波
                    for (int layer = 0; layer < 3; layer++) {
                        double speed = 0.6 + layer * 0.3;
                        int life = 80 - layer * 10;
                        DyeColor color = layer == 0 ? DyeColor.WHITE
                                : (layer == 1 ? DyeColor.LIGHT_GRAY : DyeColor.GRAY);

                        var e = holder.prepareDanmaku(life, dir.scale(speed),
                                YHDanmaku.Bullet.MENTOS, color);
                        holder.shoot(e);
                    }
                }
            }

            super.tick(holder, card);
            return tick > 5;
        }
    }

    /**
     * 剑气远程攻击
     */
    @SerialClass
    public static class SwordKi extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private int intensity;
        @SerialClass.SerialField
        private double baseAngle;

        public SwordKi init(int intensity, double baseAngle) {
            this.intensity = intensity;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            int duration = 15 + intensity * 3;

            if (tick < duration && tick % 3 == 0) {
                var center = holder.center();
                var toTarget = target.subtract(center).normalize();
                var o = DanmakuHelper.getOrientation(toTarget);
                var rand = holder.random();

                int kiCount = 4 + intensity * 2;

                for (int i = 0; i < kiCount; i++) {
                    double angle = (i - kiCount / 2.0) * 8;
                    double speed = 2.0 + rand.nextDouble() * 0.5;
                    Vec3 dir = o.rotateDegrees(angle, rand.nextGaussian() * 3);

                    int life = 100;
                    var e = holder.prepareDanmaku(life, dir.scale(speed),
                            YHDanmaku.Bullet.MENTOS, DyeColor.CYAN);

                    // 加速运动
                    e.mover = new RectMover(center, dir.scale(speed * 0.5), dir.scale(0.05));
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 上升斩 - 地面玩家专用
     */
    @SerialClass
    public static class RisingSwordWave extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;

        public RisingSwordWave init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            if (tick == 0) {
                var center = holder.center();
                var toTarget = target.subtract(center);
                var horizontal = toTarget.multiply(1, 0, 1).normalize();
                var rand = holder.random();

                int waveCount = hpRatio < 0.4f ? 24 : (hpRatio < 0.7f ? 16 : 10);

                // 从地面向上的弧形弹幕
                for (int i = 0; i < waveCount; i++) {
                    double angle = (i - waveCount / 2.0) * (180.0 / waveCount);

                    // 上升方向
                    Vec3 upDir = new Vec3(
                            horizontal.x * Math.cos(Math.toRadians(angle)),
                            Math.sin(Math.toRadians(Math.abs(angle))) * 0.8 + 0.3,
                            horizontal.z * Math.cos(Math.toRadians(angle))).normalize();

                    double speed = 1.0 + rand.nextDouble() * 0.3;
                    var e = holder.prepareDanmaku(70, upDir.scale(speed),
                            YHDanmaku.Bullet.BALL, DyeColor.LIME);
                    e.setPos(center.add(0, -1, 0));
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > 5;
        }
    }

    /**
     * 下压斩 - 空中玩家专用
     */
    @SerialClass
    public static class FallingSwordWave extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;

        public FallingSwordWave init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            int duration = 20;

            if (tick < duration && tick % 4 == 0) {
                var toTarget = target.subtract(holder.center());
                var rand = holder.random();

                int waveCount = hpRatio < 0.4f ? 12 : (hpRatio < 0.7f ? 8 : 5);

                // 从上方压下的弹幕
                Vec3 aboveTarget = target.add(0, 15, 0);
                var o = DanmakuHelper.getOrientation(new Vec3(0, -1, 0));

                for (int i = 0; i < waveCount; i++) {
                    double angle = rand.nextDouble() * 360;
                    double radius = rand.nextDouble() * 5;
                    Vec3 offset = o.rotateDegrees(angle).scale(radius);
                    Vec3 pos = aboveTarget.add(offset.x, 0, offset.z);

                    Vec3 downDir = new Vec3(offset.x * 0.1, -1, offset.z * 0.1).normalize();
                    double speed = 1.5 + rand.nextDouble() * 0.5;

                    var e = holder.prepareDanmaku(50, downDir.scale(speed),
                            YHDanmaku.Bullet.MENTOS, DyeColor.PURPLE);
                    e.setPos(pos);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 拦截斩 - 高速玩家专用
     */
    @SerialClass
    public static class InterceptSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;

        public InterceptSlash init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            var vel = holder.targetVelocity();
            if (target == null || vel == null)
                return true;

            if (tick == 0) {
                var rand = holder.random();

                // 预测玩家位置
                double predictionTime = 15;
                Vec3 predictedPos = target.add(vel.scale(predictionTime));

                int interceptCount = hpRatio < 0.4f ? 20 : (hpRatio < 0.7f ? 12 : 8);

                // 在预测位置周围生成拦截弹幕
                var o = DanmakuHelper.getOrientation(vel.normalize());

                for (int i = 0; i < interceptCount; i++) {
                    double angle = 360.0 / interceptCount * i;
                    double radius = 3 + rand.nextDouble() * 5;
                    Vec3 offset = o.rotateDegrees(angle).scale(radius);
                    Vec3 pos = predictedPos.add(offset);

                    // 向内收缩
                    Vec3 inwardDir = predictedPos.subtract(pos).normalize();
                    double speed = 0.4 + rand.nextDouble() * 0.2;

                    int life = 60;
                    var e = holder.prepareDanmaku(life, inwardDir.scale(speed),
                            YHDanmaku.Bullet.BALL, DyeColor.RED);
                    e.setPos(pos);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > 5;
        }
    }

    /**
     * 预判斩击 - 远距离预判弹幕
     */
    @SerialClass
    public static class PredictiveStrike extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;

        public PredictiveStrike init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            var vel = holder.targetVelocity();
            if (target == null)
                return true;

            if (tick < 20 && tick % 4 == 0) {
                var center = holder.center();
                var rand = holder.random();

                // 多时间点预测
                double[] times = { 10, 20, 30 };

                for (double time : times) {
                    Vec3 predicted = vel != null ? target.add(vel.scale(time)) : target;
                    Vec3 toPredict = predicted.subtract(center).normalize();

                    double speed = 1.5 + time * 0.02;
                    var e = holder.prepareDanmaku(80, toPredict.scale(speed),
                            YHDanmaku.Bullet.MENTOS, DyeColor.ORANGE);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > 20;
        }
    }

    /**
     * 瞬移斩 - 低血量专属华丽技能
     */
    @SerialClass
    public static class FlashSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;
        @SerialClass.SerialField
        private Vec3 flashPos;

        public FlashSlash init(float hpRatio) {
            this.hpRatio = hpRatio;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            if (tick == 0) {
                // 记录瞬移位置
                var rand = holder.random();
                double angle = rand.nextDouble() * 360;
                double dist = 8 + rand.nextDouble() * 4;
                flashPos = target.add(
                        Math.cos(Math.toRadians(angle)) * dist,
                        rand.nextDouble() * 3 - 1,
                        Math.sin(Math.toRadians(angle)) * dist);
            }

            if (tick == 5 && flashPos != null) {
                var rand = holder.random();
                int slashCount = hpRatio < 0.3f ? 36 : 24;

                // 从瞬移点爆发弹幕
                var o = DanmakuHelper.getOrientation(target.subtract(flashPos).normalize());

                for (int i = 0; i < slashCount; i++) {
                    double angle = 360.0 / slashCount * i;
                    for (int layer = 0; layer < 2; layer++) {
                        double tilt = (layer - 0.5) * 30;
                        Vec3 dir = o.rotateDegrees(angle, tilt);
                        double speed = 1.2 + layer * 0.3 + rand.nextDouble() * 0.2;

                        DyeColor color = layer == 0 ? DyeColor.WHITE : DyeColor.PINK;
                        var e = holder.prepareDanmaku(60, dir.scale(speed),
                                YHDanmaku.Bullet.MENTOS, color);
                        e.setPos(flashPos);
                        holder.shoot(e);
                    }
                }
            }

            super.tick(holder, card);
            return tick > 10;
        }
    }

    /**
     * 樱花斩 - 华丽的樱花形弹幕
     */
    @SerialClass
    public static class SakuraSlash extends Ticker<YoumuSpell> {
        @SerialClass.SerialField
        private float hpRatio;
        @SerialClass.SerialField
        private double baseAngle;

        public SakuraSlash init(float hpRatio, double baseAngle) {
            this.hpRatio = hpRatio;
            this.baseAngle = baseAngle;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, YoumuSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            int duration = 60;
            int petalsPerTick = hpRatio < 0.3f ? 8 : (hpRatio < 0.5f ? 5 : 3);

            if (tick < duration) {
                var center = holder.center();
                var rand = holder.random();

                // 樱花花瓣形状 (5瓣花)
                for (int i = 0; i < petalsPerTick; i++) {
                    double t = baseAngle + tick * 6 + i * (360.0 / petalsPerTick);

                    // 樱花曲线参数方程
                    double r = 1.0 + 0.3 * Math.cos(5 * Math.toRadians(t));
                    double x = r * Math.cos(Math.toRadians(t));
                    double z = r * Math.sin(Math.toRadians(t));
                    double y = Math.sin(tick * 0.1) * 0.3;

                    Vec3 dir = new Vec3(x, y, z).normalize();
                    double speed = 0.6 + rand.nextDouble() * 0.3;

                    // 随机樱花颜色
                    DyeColor[] sakuraColors = { DyeColor.PINK, DyeColor.WHITE, DyeColor.MAGENTA };
                    DyeColor color = sakuraColors[rand.nextInt(sakuraColors.length)];

                    var e = holder.prepareDanmaku(80, dir.scale(speed),
                            YHDanmaku.Bullet.CIRCLE, color);
                    holder.shoot(e);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }
}
