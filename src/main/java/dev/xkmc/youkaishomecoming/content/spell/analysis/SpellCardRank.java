package dev.xkmc.youkaishomecoming.content.spell.analysis;

import dev.xkmc.youkaishomecoming.content.spell.preview.SpellEditorLocalization;
import net.minecraft.util.StringRepresentable;

/**
 * 圣德太子·冠位十二阶符卡基底分级体系。
 * 从低到高分为 6 德（智、义、信、礼、仁、德），每德分大小两阶，共 12 阶。
 * 每一阶对应由低到高的分析器容量预算（免费节点、单 tick 生成、峰值存活、总弹幕刻）与专属边框色彩。
 */
public enum SpellCardRank implements StringRepresentable {
	LESSER_WISDOM("lesser_wisdom", "小智", "Lesser Wisdom", 1,
			0xFF1A141A, 0xFF555566, 0xFF2A202A, 0xFF140E14,
			5, 128, 10_000, 100_000_000L, 1_000_000L, 0),
	GREATER_WISDOM("greater_wisdom", "大智", "Greater Wisdom", 2,
			0xFF221118, 0xFF777788, 0xFF3D2030, 0xFF160C14,
			8, 192, 15_000, 150_000_000L, 1_500_000L, 0),

	LESSER_RIGHTEOUSNESS("lesser_righteousness", "小义", "Lesser Righteousness", 3,
			0xFF20262B, 0xFF99AAB8, 0xFF354450, 0xFF1A2228,
			12, 256, 20_000, 200_000_000L, 2_000_000L, 0),
	GREATER_RIGHTEOUSNESS("greater_righteousness", "大义", "Greater Righteousness", 4,
			0xFF2C353C, 0xFFCCE0F0, 0xFF4A5E6E, 0xFF202830,
			16, 384, 30_000, 300_000_000L, 3_000_000L, 0),

	LESSER_FAITH("lesser_faith", "小信", "Lesser Faith", 5,
			0xFF2E2410, 0xFFD4A837, 0xFF4D3D1A, 0xFF221A08,
			20, 512, 40_000, 400_000_000L, 4_000_000L, 1),
	GREATER_FAITH("greater_faith", "大信", "Greater Faith", 6,
			0xFF3D3012, 0xFFFFD700, 0xFF66501C, 0xFF2B200A,
			25, 768, 60_000, 600_000_000L, 6_000_000L, 1),

	LESSER_PROPRIETY("lesser_propriety", "小礼", "Lesser Propriety", 7,
			0xFF2E0E14, 0xFFD9455B, 0xFF521822, 0xFF20060A,
			30, 1024, 80_000, 800_000_000L, 8_000_000L, 2),
	GREATER_PROPRIETY("greater_propriety", "大礼", "Greater Propriety", 8,
			0xFF3D0A14, 0xFFFF3344, 0xFF6B1220, 0xFF28040A,
			36, 1536, 100_000, 1_000_000_000L, 10_000_000L, 2),

	LESSER_BENEVOLENCE("lesser_benevolence", "小仁", "Lesser Benevolence", 9,
			0xFF082633, 0xFF26B8DE, 0xFF104459, 0xFF041820,
			42, 2048, 150_000, 1_500_000_000L, 15_000_000L, 3),
	GREATER_BENEVOLENCE("greater_benevolence", "大仁", "Greater Benevolence", 10,
			0xFF0A3344, 0xFF00E5FF, 0xFF145873, 0xFF06222E,
			50, 3072, 200_000, 2_000_000_000L, 20_000_000L, 3),

	LESSER_VIRTUE("lesser_virtue", "小德", "Lesser Virtue", 11,
			0xFF240A36, 0xFFC247EB, 0xFF421063, 0xFF180424,
			60, 4096, 300_000, 3_000_000_000L, 30_000_000L, 4),
	GREATER_VIRTUE("greater_virtue", "大德", "Greater Virtue", 12,
			0xFF300A47, 0xFFFFD700, 0xFF57107D, 0xFF200430,
			80, 8192, 500_000, 5_000_000_000L, 50_000_000L, 6);

	private final String id;
	private final String displayNameZh;
	private final String displayNameEn;
	private final int tierNumber;
	// 边框调色 (NativeImage ABGR 格式)
	private final int outerBorder;
	private final int goldLine;
	private final int headerFill;
	private final int footerFill;

	// 容量预算标尺
	private final int freeNodeCount;
	private final int maxSpawnPerTick;
	private final int maxPeakAlive;
	private final long maxProjectileTicks;
	private final long maxHookExecutions;
	private final int experimentalGrants;

	SpellCardRank(String id, String displayNameZh, String displayNameEn, int tierNumber,
				  int outerBorder, int goldLine, int headerFill, int footerFill,
				  int freeNodeCount, int maxSpawnPerTick, int maxPeakAlive,
				  long maxProjectileTicks, long maxHookExecutions, int experimentalGrants) {
		this.id = id;
		this.displayNameZh = displayNameZh;
		this.displayNameEn = displayNameEn;
		this.tierNumber = tierNumber;
		this.outerBorder = outerBorder;
		this.goldLine = goldLine;
		this.headerFill = headerFill;
		this.footerFill = footerFill;
		this.freeNodeCount = freeNodeCount;
		this.maxSpawnPerTick = maxSpawnPerTick;
		this.maxPeakAlive = maxPeakAlive;
		this.maxProjectileTicks = maxProjectileTicks;
		this.maxHookExecutions = maxHookExecutions;
		this.experimentalGrants = experimentalGrants;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public int tierNumber() {
		return tierNumber;
	}

	public String displayName() {
		return SpellEditorLocalization.isChinese() ? displayNameZh : displayNameEn;
	}

	public int outerBorder() {
		return outerBorder;
	}

	public int goldLine() {
		return goldLine;
	}

	public int headerFill() {
		return headerFill;
	}

	public int footerFill() {
		return footerFill;
	}

	public SpellDraftBudget createBudget() {
		SpellRankConfig.Settings settings = SpellRankConfig.current(this);
		return new SpellDraftBudget(
				settings.freeNodeCount(), settings.maxSpawnPerTick(), settings.maxPeakAlive(),
				settings.maxProjectileTicks(), settings.maxHookExecutions(),
				settings.experimentalGrant(SpellCapability.TELEPORT),
				settings.experimentalGrant(SpellCapability.ERASE_ENEMY_DANMAKU),
				settings.experimentalGrant(SpellCapability.CLEAR_SCREEN),
				settings.experimentalGrant(SpellCapability.BOSS_ON_DAMAGE),
				settings.experimentalGrant(SpellCapability.CONFINED_TARGET), 0
		);
	}

	public static SpellCardRank fromBudget(SpellDraftBudget budget) {
		if (budget == null) return LESSER_WISDOM;
		// Reverse search from highest tier to lowest
		var vals = values();
		for (int i = vals.length - 1; i >= 0; i--) {
			var r = vals[i];
			var settings = SpellRankConfig.current(r);
			if (budget.freeNodeCount() >= settings.freeNodeCount()
					&& budget.maxSpawnPerTick() >= settings.maxSpawnPerTick()
					&& budget.maxPeakAlive() >= settings.maxPeakAlive()
					&& budget.maxProjectileTicks() >= settings.maxProjectileTicks()
					&& budget.maxHookExecutions() >= settings.maxHookExecutions()) {
				return r;
			}
		}
		return LESSER_WISDOM;
	}

	public static SpellCardRank fromTier(int tier) {
		int index = Math.max(0, Math.min(values().length - 1, tier - 1));
		return values()[index];
	}

	public static SpellCardRank byName(String name) {
		for (SpellCardRank rank : values()) {
			if (rank.id.equalsIgnoreCase(name) || rank.name().equalsIgnoreCase(name)) {
				return rank;
			}
		}
		return LESSER_WISDOM;
	}

	// Defaults remain embedded for frame colors and backwards-compatible fallback;
	// numeric draft permissions are resolved through SpellRankConfig at runtime.
	int defaultFreeNodeCount() { return freeNodeCount; }
	int defaultMaxSpawnPerTick() { return maxSpawnPerTick; }
	int defaultMaxPeakAlive() { return maxPeakAlive; }
	long defaultMaxProjectileTicks() { return maxProjectileTicks; }
	long defaultMaxHookExecutions() { return maxHookExecutions; }
	int defaultExperimentalGrants() { return experimentalGrants; }
}
