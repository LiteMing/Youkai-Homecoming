package dev.xkmc.youkaishomecoming.content.spell.certification.network;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: the firm certification quote (design doc §5.2, §18). The
 * client displays it as the official quote; the quoteId must be echoed back on
 * start so the definition cannot be swapped.
 */
@SerialClass
public class CertificationQuoteToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String quoteId = "";
	@SerialClass.SerialField
	public String definitionHash = "";
	@SerialClass.SerialField
	public int durationTicks = 0;
	@SerialClass.SerialField
	public double arenaHalfSize = 0;
	@SerialClass.SerialField
	public long startCostUnits = 0;
	@SerialClass.SerialField
	public long issueCostUnits = 0;
	@SerialClass.SerialField
	public long castCostUnits = 0;
	@SerialClass.SerialField
	public long nodeCostUnits = 0;
	@SerialClass.SerialField
	public long totalSpawnUpperBound = 0;
	@SerialClass.SerialField
	public long projectileTicks = 0;
	@SerialClass.SerialField
	public int maxSpawnPerTick = 0;
	@SerialClass.SerialField
	public int peakAliveUpperBound = 0;
	@SerialClass.SerialField
	public long hookExecutionUpperBound = 0;
	@SerialClass.SerialField
	public int rewardDurationTicks = 0;
	@SerialClass.SerialField
	public int spellHp = 0;
	@SerialClass.SerialField
	public int specialNodeQuota = 0;
	@SerialClass.SerialField
	public int ordinaryNodes = 0;
	@SerialClass.SerialField
	public int freeNodeCount = 0;
	@SerialClass.SerialField
	public int experimentalNodes = 0;
	@SerialClass.SerialField
	public int operatorOnlyNodes = 0;
	@SerialClass.SerialField
	public int maxSpawnBudget = 0;
	@SerialClass.SerialField
	public int maxPeakBudget = 0;
	@SerialClass.SerialField
	public long maxProjectileTicksBudget = 0;
	@SerialClass.SerialField
	public long maxHookExecutionsBudget = 0;
	public String capabilities = "";

	public CertificationQuoteToClient() {
	}

	public CertificationQuoteToClient(CertificationQuote quote) {
		this.quoteId = quote.quoteId();
		this.definitionHash = quote.definitionHash();
		this.durationTicks = quote.durationTicks();
		this.arenaHalfSize = quote.arenaHalfSize();
		this.startCostUnits = quote.startCostUnits();
		this.issueCostUnits = quote.issueCostUnits();
		this.castCostUnits = quote.castCostUnits();
		this.nodeCostUnits = quote.draftBudget().nodeCostUnits(quote.nodeSummary());
		this.totalSpawnUpperBound = quote.analysis().totalSpawnUpperBound();
		this.projectileTicks = quote.analysis().projectileTicks();
		this.maxSpawnPerTick = quote.analysis().maxSpawnPerTick();
		this.peakAliveUpperBound = quote.analysis().peakAliveUpperBound();
		this.hookExecutionUpperBound = quote.analysis().hookExecutionUpperBound();
		this.rewardDurationTicks = quote.rewardDurationTicks();
		this.spellHp = quote.spellHp();
		this.specialNodeQuota = quote.specialNodeQuota();
		this.ordinaryNodes = quote.nodeSummary().ordinaryNodes();
		this.freeNodeCount = quote.draftBudget().freeNodeCount();
		this.experimentalNodes = quote.nodeSummary().experimentalNodes();
		this.operatorOnlyNodes = quote.nodeSummary().operatorOnlyNodes();
		this.maxSpawnBudget = quote.maxSpawnPerTickBudget();
		this.maxPeakBudget = quote.draftBudget().maxPeakAlive();
		this.maxProjectileTicksBudget = quote.draftBudget().maxProjectileTicks();
		this.maxHookExecutionsBudget = quote.draftBudget().maxHookExecutions();
		this.capabilities = quote.analysis().requiredCapabilities().toString();
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		CertificationClientHandler.acceptQuote(this);
	}
}
