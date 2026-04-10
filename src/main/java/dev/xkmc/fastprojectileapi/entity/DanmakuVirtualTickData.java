package dev.xkmc.fastprojectileapi.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DanmakuVirtualTickData {

	private boolean preparedTickState;
	private boolean standardSequentialFallback;
	private boolean preparedSequentialFallback;
	private boolean parallelReady;
	private boolean applyMovePending;
	@Nullable
	private ProjectileMovement movement;
	@Nullable
	private Vec3 src;
	@Nullable
	private Vec3 dst;
	@Nullable
	private Vec3 effectiveDst;
	@Nullable
	private AABB searchBox;
	private float radius;
	private float graze;
	private boolean checkBlock;
	private int sectionX0;
	private int sectionY0;
	private int sectionZ0;
	private int sectionX1;
	private int sectionY1;
	private int sectionZ1;
	@Nullable
	private HitResult blockHit;
	@Nullable
	private Entity hitEntity;
	private final ArrayList<PreparedCandidate> candidates = new ArrayList<>();
	private final ArrayList<Player> grazedPlayers = new ArrayList<>();

	void reset() {
		preparedTickState = false;
		standardSequentialFallback = false;
		preparedSequentialFallback = false;
		parallelReady = false;
		applyMovePending = false;
		movement = null;
		src = null;
		dst = null;
		effectiveDst = null;
		searchBox = null;
		radius = 0;
		graze = 0;
		checkBlock = false;
		sectionX0 = 0;
		sectionY0 = 0;
		sectionZ0 = 0;
		sectionX1 = 0;
		sectionY1 = 0;
		sectionZ1 = 0;
		blockHit = null;
		hitEntity = null;
		candidates.clear();
		grazedPlayers.clear();
	}

	void markPreparedTickState() {
		preparedTickState = true;
	}

	boolean hasPreparedTickState() {
		return preparedTickState;
	}

	void markStandardSequentialFallback() {
		standardSequentialFallback = true;
	}

	boolean usesStandardSequentialFallback() {
		return standardSequentialFallback;
	}

	void markPreparedSequentialFallback() {
		preparedTickState = false;
		preparedSequentialFallback = true;
		parallelReady = false;
		applyMovePending = false;
		blockHit = null;
		hitEntity = null;
		candidates.clear();
		grazedPlayers.clear();
	}

	boolean usesPreparedSequentialFallback() {
		return preparedSequentialFallback;
	}

	void markParallelReady(ProjectileMovement movement,
	                       Vec3 src,
	                       Vec3 dst,
	                       AABB searchBox,
	                       float radius,
	                       float graze,
	                       boolean checkBlock,
	                       int sectionX0,
	                       int sectionY0,
	                       int sectionZ0,
	                       int sectionX1,
	                       int sectionY1,
	                       int sectionZ1) {
		preparedTickState = false;
		preparedSequentialFallback = false;
		parallelReady = true;
		this.movement = movement;
		this.src = src;
		this.dst = dst;
		this.effectiveDst = dst;
		this.searchBox = searchBox;
		this.radius = radius;
		this.graze = graze;
		this.checkBlock = checkBlock;
		this.sectionX0 = sectionX0;
		this.sectionY0 = sectionY0;
		this.sectionZ0 = sectionZ0;
		this.sectionX1 = sectionX1;
		this.sectionY1 = sectionY1;
		this.sectionZ1 = sectionZ1;
		this.blockHit = null;
		this.hitEntity = null;
		this.candidates.clear();
		this.grazedPlayers.clear();
	}

	boolean isParallelReady() {
		return parallelReady;
	}

	@Nullable
	ProjectileMovement movement() {
		return movement;
	}

	@Nullable
	Vec3 src() {
		return src;
	}

	@Nullable
	Vec3 dst() {
		return dst;
	}

	@Nullable
	Vec3 effectiveDst() {
		return effectiveDst;
	}

	@Nullable
	AABB searchBox() {
		return searchBox;
	}

	float radius() {
		return radius;
	}

	float graze() {
		return graze;
	}

	boolean checkBlock() {
		return checkBlock;
	}

	int sectionX0() {
		return sectionX0;
	}

	int sectionY0() {
		return sectionY0;
	}

	int sectionZ0() {
		return sectionZ0;
	}

	int sectionX1() {
		return sectionX1;
	}

	int sectionY1() {
		return sectionY1;
	}

	int sectionZ1() {
		return sectionZ1;
	}

	void setBlockHit(@Nullable HitResult blockHit, Vec3 effectiveDst) {
		this.blockHit = blockHit;
		this.effectiveDst = effectiveDst;
	}

	@Nullable
	HitResult blockHit() {
		return blockHit;
	}

	void clearCandidates() {
		candidates.clear();
	}

	void addCandidate(Entity entity, Vec3 deltaMovement, AABB hitBox, @Nullable AABB grazeBox) {
		candidates.add(new PreparedCandidate(entity, deltaMovement, hitBox, grazeBox));
	}

	List<PreparedCandidate> candidates() {
		return candidates;
	}

	void clearHitAndGraze() {
		hitEntity = null;
		grazedPlayers.clear();
	}

	void setHitEntity(@Nullable Entity hitEntity) {
		this.hitEntity = hitEntity;
	}

	@Nullable
	Entity hitEntity() {
		return hitEntity;
	}

	void addGraze(Player player) {
		grazedPlayers.add(player);
	}

	List<Player> grazedPlayers() {
		return grazedPlayers;
	}

	void queueApplyMove() {
		applyMovePending = true;
	}

	boolean isApplyMovePending() {
		return applyMovePending;
	}

	void clearApplyMovePending() {
		applyMovePending = false;
	}

	public static class PreparedCandidate {

		private final Entity entity;
		private final Vec3 deltaMovement;
		private final AABB hitBox;
		@Nullable
		private final AABB grazeBox;

		PreparedCandidate(Entity entity, Vec3 deltaMovement, AABB hitBox, @Nullable AABB grazeBox) {
			this.entity = entity;
			this.deltaMovement = deltaMovement;
			this.hitBox = hitBox;
			this.grazeBox = grazeBox;
		}

		public Entity entity() {
			return entity;
		}

		public Vec3 deltaMovement() {
			return deltaMovement;
		}

		public AABB hitBox() {
			return hitBox;
		}

		@Nullable
		public AABB grazeBox() {
			return grazeBox;
		}
	}

}
