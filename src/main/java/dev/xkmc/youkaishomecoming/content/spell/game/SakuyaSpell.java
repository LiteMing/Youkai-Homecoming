package dev.xkmc.youkaishomecoming.content.spell.game;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.mover.RectMover;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.StagedSpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.Ticker;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/**
 * 十六夜咲夜的符卡实现 (增强版)
 * <p>
 * 核心特色：
 * 1. 时间停止 - 弹幕急停后突然追踪
 * 2. 飞刀弹幕 - 高密度MENTOS型弹幕
 * 3. 螺旋展开 - 旋转扩散弹幕
 * 4. 飞刀风暴 - 大范围密集弹幕
 * <p>
 * 阶段设计：
 * - Phase 0: 幻想「杀人玩偶」- 密集飞刀 + 多层环形弹
 * - Phase 1: 奇术「エターナルミーク」- 时停飞刀 + 追踪弹 + 扫射
 * - Phase 2: 「咲夜的世界」- 全方位时停 + 飞刀风暴 + 激光
 */
@SerialClass
public class SakuyaSpell extends StagedSpellCard {

    @SerialClass.SerialField
    private int cooldown;

    @SerialClass.SerialField
    private double rotationOffset = 0;

    @Override
    protected int getStageCount() {
        return 3;
    }

    @Override
    protected void tickStaged(CardHolder holder, int stage) {
        rotationOffset += 3.0; // 螺旋旋转速度
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        var target = holder.target();
        if (target == null)
            return;
        double dist = holder.center().distanceTo(target);

        switch (stage) {
            case 0 -> {
                // 幻想「杀人玩偶」- 密集飞刀 + 多层环形弹
                addTicker(new KnifeRing().setDensity(24, 5, 4));
                if (tick % 80 == 0) {
                    addTicker(new SpiralKnife().init(1).setDensity(4, 60));
                }
                cooldown = 30;
            }
            case 1 -> {
                // 奇术「エターナルミーク」- 时停飞刀 + 追踪弹 + 扫射
                addTicker(new TimeStopKnife().setDensity(24).setFreezeTime(30));
                if (tick % 40 == 0) {
                    addTicker(new KnifeSweep().init(dist));
                }
                addTicker(new SpiralKnife().init(holder.random().nextBoolean() ? 1 : -1).setDensity(3, 50));
                cooldown = 35;
            }
            case 2 -> {
                // 「咲夜的世界」- 全方位时停 + 飞刀风暴 + 激光
                addTicker(new TimeStopKnife().setIntense(true).setDensity(36).setFreezeTime(25));
                addTicker(new SpiralKnife().init(-1).setDensity(5, 50));
                addTicker(new SpiralKnife().init(1).setDensity(5, 50));
                if (tick % 60 == 0) {
                    addTicker(new KnifeStorm().init(dist));
                }
                if (tick % 100 == 0) {
                    addTicker(new CrossLaser());
                }
                cooldown = 25;
            }
        }
    }

    @Override
    protected void tickTraditional(CardHolder holder) {
        rotationOffset += 3.0;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        var target = holder.target();
        if (target == null)
            return;
        var center = holder.center();
        double dist = center.distanceTo(target);

        if (dist < 15) {
            // 近距离：密集环形飞刀
            addTicker(new KnifeRing().setDensity(28, 4, 3));
            cooldown = 25;
        } else if (dist < 35) {
            // 中距离：时停飞刀 + 扫射
            addTicker(new TimeStopKnife().setDensity(20));
            addTicker(new KnifeSweep().init(dist));
            cooldown = 40;
        } else {
            // 远距离：螺旋飞刀 + 时停 + 风暴
            addTicker(new SpiralKnife().init(holder.random().nextBoolean() ? 1 : -1).setDensity(4, 50));
            addTicker(new TimeStopKnife().setIntense(true).setDensity(28));
            addTicker(new KnifeStorm().init(dist));
            cooldown = 50;
        }
    }

    /**
     * 环形飞刀 - 从中心向外扩散的多层环形弹幕
     * 参考 RemiliaSpell.Sweep 的密度设计
     */
    @SerialClass
    public static class KnifeRing extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private Vec3 dir;
        @SerialClass.SerialField
        private int knifeCount = 24;
        @SerialClass.SerialField
        private int waves = 5;
        @SerialClass.SerialField
        private int waveInterval = 4;
        @SerialClass.SerialField
        private int layers = 3; // 每波的层数

        public KnifeRing setDensity(int knifeCount, int waves, int waveInterval) {
            this.knifeCount = knifeCount;
            this.waves = waves;
            this.waveInterval = waveInterval;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            if (dir == null) {
                dir = holder.forward().normalize();
            }

            var target = holder.target();
            if (target == null)
                return true;

            if (tick % waveInterval == 0 && tick / waveInterval < waves) {
                var center = holder.center();
                double dist = center.distanceTo(target);
                double baseSpeed = Mth.clamp(dist / 25, 0.8, 2.5);
                int baseLife = (int) (dist * 1.5 + 25);

                var o = DanmakuHelper.getOrientation(dir);
                double angleOffset = card.rotationOffset + tick * 4;
                var rand = holder.random();

                for (int layer = 0; layer < layers; layer++) {
                    double speedMod = 1.0 - layer * 0.25;
                    double layerOffset = layer * (360.0 / knifeCount / layers);
                    DyeColor color = layer == 0 ? DyeColor.GRAY : (layer == 1 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE);
                    YHDanmaku.Bullet type = layer == 0 ? YHDanmaku.Bullet.KNIFE
                            : (layer == 1 ? YHDanmaku.Bullet.KUNAI : YHDanmaku.Bullet.KNIFE);

                    for (int i = 0; i < knifeCount; i++) {
                        double angle = 360.0 / knifeCount * i + angleOffset + layerOffset;
                        double verSpread = rand.nextGaussian() * 5;
                        var knifeDir = o.rotateDegrees(angle, verSpread);

                        double speed = baseSpeed * speedMod * (0.9 + rand.nextDouble() * 0.2);
                        int life = (int) (baseLife / speedMod);

                        var e = holder.prepareDanmaku(life, knifeDir.scale(speed), type, color);
                        holder.shoot(e);
                    }
                }
            }

            super.tick(holder, card);
            return tick > waves * waveInterval;
        }
    }

    /**
     * 时间停止飞刀 - 急停后追踪目标 (高密度版)
     */
    @SerialClass
    public static class TimeStopKnife extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private Vec3 pos;
        @SerialClass.SerialField
        private int knifeCount = 24;
        @SerialClass.SerialField
        private int freezeTime = 30;
        @SerialClass.SerialField
        private boolean intense = false;

        public TimeStopKnife setDensity(int count) {
            this.knifeCount = count;
            return this;
        }

        public TimeStopKnife setFreezeTime(int time) {
            this.freezeTime = time;
            return this;
        }

        public TimeStopKnife setIntense(boolean intense) {
            this.intense = intense;
            if (intense) {
                knifeCount = Math.max(knifeCount, 32);
                freezeTime = Math.min(freezeTime, 25);
            }
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            if (pos == null) {
                pos = holder.center();
            }

            var target = holder.target();
            if (target == null)
                return true;

            if (tick == 0) {
                generateFrozenKnives(holder, card);
            }

            super.tick(holder, card);
            return tick > freezeTime + 5;
        }

        private void generateFrozenKnives(CardHolder holder, SakuyaSpell card) {
            var target = holder.target();
            if (target == null)
                return;

            var rand = holder.random();
            var center = holder.center();
            var targetDir = target.subtract(center).normalize();
            var o = DanmakuHelper.getOrientation(targetDir);

            double baseAngle = card.rotationOffset;
            int expandTime = intense ? 12 : 15;
            double expandDist = intense ? 10 : 6;

            // 生成多层飞刀
            int layerCount = intense ? 3 : 2;
            for (int layer = 0; layer < layerCount; layer++) {
                double layerOffset = layer * (360.0 / knifeCount / layerCount);
                double layerDist = expandDist * (1.0 + layer * 0.3);
                DyeColor color = layer == 0 ? DyeColor.RED : (layer == 1 ? DyeColor.GRAY : DyeColor.LIGHT_GRAY);

                for (int i = 0; i < knifeCount; i++) {
                    double angle = 360.0 / knifeCount * i + baseAngle + layerOffset;
                    double tilt = intense ? (rand.nextDouble() * 40 - 20) : (rand.nextDouble() * 20 - 10);

                    var expandDir = o.rotateDegrees(angle, tilt);
                    double expandSpeed = layerDist / expandTime;

                    var e = holder.prepareDanmaku(expandTime, expandDir.scale(expandSpeed),
                            YHDanmaku.Bullet.KNIFE, color);

                    // 急停减速
                    var acc = expandDir.scale(-expandSpeed / expandTime * 0.9);
                    e.mover = new RectMover(center, expandDir.scale(expandSpeed), acc);

                    // 时停结束后追踪
                    double homingSpeed = intense ? 2.2 : 1.6;
                    int homingLife = intense ? 50 : 60;
                    e.afterExpiry = new DelayedHomingKnife()
                            .setup(freezeTime - expandTime, homingSpeed, homingLife);

                    holder.shoot(e);
                }
            }
        }
    }

    /**
     * 螺旋飞刀 - 持续旋转发射的螺旋弹幕 (高密度版)
     */
    @SerialClass
    public static class SpiralKnife extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private Vec3 dir;
        @SerialClass.SerialField
        private int rotationDir = 1;
        @SerialClass.SerialField
        private int duration = 50;
        @SerialClass.SerialField
        private int knifePerTick = 4;
        @SerialClass.SerialField
        private double rotationSpeed = 18;

        public SpiralKnife init(int rotationDir) {
            this.rotationDir = rotationDir;
            return this;
        }

        public SpiralKnife setDensity(int knifePerTick, int duration) {
            this.knifePerTick = knifePerTick;
            this.duration = duration;
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            if (dir == null) {
                var target = holder.target();
                if (target == null)
                    return true;
                dir = target.subtract(holder.center()).normalize();
            }

            var target = holder.target();
            if (target == null)
                return true;

            var center = holder.center();
            double dist = center.distanceTo(target);
            double speed = Mth.clamp(dist / 20, 0.7, 2.2);
            int life = (int) (dist * 2 + 35);

            var o = DanmakuHelper.getOrientation(dir);
            var rand = holder.random();

            for (int i = 0; i < knifePerTick; i++) {
                double angle = rotationDir * tick * rotationSpeed + i * (360.0 / knifePerTick);
                double tilt = Math.sin(tick * 0.12) * 25;

                var knifeDir = o.rotateDegrees(angle, tilt);

                // 主螺旋刀
                var e = holder.prepareDanmaku(life, knifeDir.scale(speed),
                        YHDanmaku.Bullet.MENTOS, DyeColor.GRAY);
                holder.shoot(e);

                // 中速刀
                double midSpeed = speed * (0.5 + rand.nextDouble() * 0.2);
                var midDir = o.rotateDegrees(angle + 15, -tilt * 0.5);
                var mid = holder.prepareDanmaku(life + 15, midDir.scale(midSpeed),
                        YHDanmaku.Bullet.BALL, DyeColor.LIGHT_GRAY);
                holder.shoot(mid);

                // 内层慢速刀 (交错)
                if (tick % 2 == 0) {
                    var innerDir = o.rotateDegrees(angle + 180, -tilt);
                    var inner = holder.prepareDanmaku(life + 25, innerDir.scale(speed * 0.35),
                            YHDanmaku.Bullet.CIRCLE, DyeColor.WHITE);
                    holder.shoot(inner);
                }
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 飞刀扫射 - 模仿 RemiliaSpell.Sweep 的扫射效果
     */
    @SerialClass
    public static class KnifeSweep extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private Vec3 dir, nor;
        @SerialClass.SerialField
        private double initialAngle = 0;
        @SerialClass.SerialField
        private int duration = 20;
        @SerialClass.SerialField
        private int countPerTick = 8;

        public KnifeSweep init(double dist) {
            this.countPerTick = (int) Mth.clamp(dist / 5, 6, 15);
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            if (dir == null) {
                dir = holder.forward();
                var rand = holder.random();
                initialAngle = rand.nextDouble() * 360;
                nor = DanmakuHelper.getOrientation(dir).asNormal()
                        .rotateDegrees((rand.nextDouble() * 2 - 1) * 60);
            }

            var target = holder.target();
            if (target == null)
                return true;

            var rand = holder.random();
            var o = DanmakuHelper.getOrientation(dir, nor);
            double dist = holder.center().distanceTo(target);
            double baseSpeed = Mth.clamp(dist / 20, 0.8, 2.0);

            double sweepAngle = initialAngle + 360.0 / duration * tick;
            int life = (int) Math.max(60, dist * 2);

            for (int i = 0; i < countPerTick; i++) {
                double angle = sweepAngle + (rand.nextDouble() * 2 - 1) * 15;
                double verSpread = rand.nextGaussian() * 8;
                double speed = baseSpeed * (0.8 + rand.nextDouble() * 0.4);

                var knifeDir = o.rotateDegrees(angle, verSpread);

                // 快速刀
                var e = holder.prepareDanmaku(life, knifeDir.scale(speed),
                        YHDanmaku.Bullet.MENTOS, DyeColor.GRAY);
                holder.shoot(e);

                // 中速刀
                var mid = holder.prepareDanmaku((int) (life * 1.2), knifeDir.scale(speed * 0.6),
                        YHDanmaku.Bullet.BALL, DyeColor.LIGHT_GRAY);
                holder.shoot(mid);
            }

            super.tick(holder, card);
            return tick > duration;
        }
    }

    /**
     * 飞刀风暴 - 大范围密集弹幕攻击
     * 参考 RemiliaSpell.shootSpear 的密度
     */
    @SerialClass
    public static class KnifeStorm extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private int totalKnives = 100;
        @SerialClass.SerialField
        private int burstDuration = 10;

        public KnifeStorm init(double dist) {
            this.totalKnives = (int) Mth.clamp(dist * 4, 80, 200);
            return this;
        }

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            var target = holder.target();
            if (target == null)
                return true;

            var rand = holder.random();
            var center = holder.center();
            double dist = center.distanceTo(target);
            var targetDir = target.subtract(center).normalize();
            var o = DanmakuHelper.getOrientation(targetDir);

            int knivesThisTick = totalKnives / burstDuration;

            for (int i = 0; i < knivesThisTick; i++) {
                double angle = rand.nextDouble() * 360;
                double tilt = rand.nextGaussian() * 30;
                double spread = rand.nextDouble() * 45 - 22.5; // 前方锥形

                var knifeDir = o.rotateDegrees(angle * 0.1 + spread, tilt);
                double speed = 1.5 + rand.nextDouble() * 1.0;
                int life = (int) (dist * 1.5 + 30 + rand.nextInt(20));

                DyeColor color = rand.nextDouble() < 0.3 ? DyeColor.RED
                        : (rand.nextDouble() < 0.5 ? DyeColor.GRAY : DyeColor.LIGHT_GRAY);
                YHDanmaku.Bullet type = rand.nextDouble() < 0.4 ? YHDanmaku.Bullet.KNIFE
                        : (rand.nextDouble() < 0.5 ? YHDanmaku.Bullet.BALL : YHDanmaku.Bullet.KNIFE);

                var e = holder.prepareDanmaku(life, knifeDir.scale(speed), type, color);

                // 随机位置偏移 (营造风暴感)
                double posOffset = rand.nextDouble() * 3;
                var offsetDir = o.rotateDegrees(rand.nextDouble() * 360);
                e.setPos(center.add(offsetDir.scale(posOffset)));

                holder.shoot(e);
            }

            super.tick(holder, card);
            return tick > burstDuration;
        }
    }

    /**
     * 十字激光 - Phase 2 专属
     */
    @SerialClass
    public static class CrossLaser extends Ticker<SakuyaSpell> {

        @SerialClass.SerialField
        private int laserCount = 8;
        @SerialClass.SerialField
        private int life = 100;

        @Override
        public boolean tick(CardHolder holder, SakuyaSpell card) {
            if (tick == 0) {
                var center = holder.center();
                var dir = holder.forward();
                var rand = holder.random();
                var o = DanmakuHelper.getOrientation(dir);

                double baseAngle = card.rotationOffset;

                for (int i = 0; i < laserCount; i++) {
                    double angle = 360.0 / laserCount * i + baseAngle;
                    var laserDir = o.rotateDegrees(angle);

                    var laser = holder.prepareLaser(life, center, laserDir, 50,
                            YHDanmaku.Laser.LASER, DyeColor.LIGHT_BLUE);
                    laser.setupTime(15, 10, life - 25, 10);
                    holder.shoot(laser);
                }
            }

            super.tick(holder, card);
            return tick > 10;
        }
    }

    /**
     * 延迟追踪飞刀
     */
    @SerialClass
    public static class DelayedHomingKnife extends TrailAction {

        @SerialClass.SerialField
        private int delay;
        @SerialClass.SerialField
        private double speed;
        @SerialClass.SerialField
        private int life;

        public DelayedHomingKnife setup(int delay, double speed, int life) {
            this.delay = delay;
            this.speed = speed;
            this.life = life;
            return this;
        }

        @Override
        public void execute(CardHolder holder, Vec3 pos, Vec3 dir) {
            var target = holder.target();
            if (target == null)
                return;

            var marker = holder.prepareDanmaku(delay, Vec3.ZERO,
                    YHDanmaku.Bullet.CIRCLE, DyeColor.WHITE);
            marker.setPos(pos);
            marker.afterExpiry = new HomingKnife().setup(speed, life);
            holder.shoot(marker);
        }
    }

    /**
     * 追踪飞刀
     */
    @SerialClass
    public static class HomingKnife extends TrailAction {

        @SerialClass.SerialField
        private double speed;
        @SerialClass.SerialField
        private int life;

        public HomingKnife setup(double speed, int life) {
            this.speed = speed;
            this.life = life;
            return this;
        }

        @Override
        public void execute(CardHolder holder, Vec3 pos, Vec3 dir) {
            var target = holder.target();
            if (target == null)
                return;

            var homingDir = target.subtract(pos).normalize();
            var e = holder.prepareDanmaku(life, homingDir.scale(speed),
                    YHDanmaku.Bullet.MENTOS, DyeColor.GRAY);
            e.setPos(pos);
            e.mover = new RectMover(pos, homingDir.scale(speed), Vec3.ZERO);
            holder.shoot(e);
        }
    }
}
