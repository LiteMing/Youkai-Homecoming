package dev.xkmc.youkaishomecoming.content.spell.spellcard;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.ActualSpellCard;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// 1. 创建新的阶段化符卡基类
@SerialClass
public abstract class StagedSpellCard extends ActualSpellCard {

    @SerialClass.SerialField
    private int currentStage = -1;

    @Override
    public void tick(CardHolder holder) {
        super.tick(holder);

        // 检查是否为弹幕战斗
        if (isDanmakuCombat(holder)) {
            int newStage = calculateStage(holder);
            if (newStage != currentStage) {
                onStageChange(holder, currentStage, newStage);
                currentStage = newStage;
            }
            tickStaged(holder, currentStage);
        } else {
            // 传统符卡逻辑
            tickTraditional(holder);
        }
    }

    private boolean isDanmakuCombat(CardHolder holder) {
        // 检查是否有玩家处于弹幕战斗状态
        LivingEntity self = holder.self();
        return self.level().getEntitiesOfClass(Player.class,
                        self.getBoundingBox().inflate(50))
                .stream()
                .anyMatch(p -> GrazeCapability.HOLDER.get(p).isInSession(self.getUUID()));
    }

    private int calculateStage(CardHolder holder) {
        LivingEntity self = holder.self();
        float healthRatio = self.getHealth() / self.getMaxHealth();
        int totalStages = getStageCount();
        return Math.min(totalStages - 1, (int)(totalStages * (1 - healthRatio)));
    }

    protected abstract int getStageCount();
    protected abstract void tickStaged(CardHolder holder, int stage);
    protected abstract void tickTraditional(CardHolder holder);

    protected void onStageChange(CardHolder holder, int oldStage, int newStage) {
        // 清理旧阶段的ticker
        getTickers().clear();
    }
}
