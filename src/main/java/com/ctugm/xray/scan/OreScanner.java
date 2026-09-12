package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class OreScanner {
	private static final int CHUNK_SIZE = 16;
	private static final int CHUNK_RADIUS = 1;
	private static final int DEFAULT_MIN_Y_OFFSET = -8;
	private static final int DEFAULT_MAX_Y_OFFSET = 7;

	// 儲存目前找到的礦物位置
	private Set<BlockPos> orePositions = Set.of();

	// 紀錄上一次掃描結果，避免不必要的重複處理
	private Integer lastReportedCount;
	private Object lastScanContext;
	private BlockPos lastScanCenter;

	public Optional<ScanResult> scanIfNeeded(
			Object scanContext,
			BlockPos center,
			Predicate<BlockPos> oreMatcher
	) {
		return scanIfNeeded(
				scanContext,
				center,
				center.getY() + DEFAULT_MIN_Y_OFFSET,
				center.getY() + DEFAULT_MAX_Y_OFFSET + 1,
				oreMatcher
		);
	}

	public Optional<ScanResult> scanIfNeeded(
			Object scanContext,
			BlockPos center,
			int minY,
			int maxYExclusive,
			Predicate<BlockPos> oreMatcher
	) {
		// 掃描環境與中心位置沒變時，直接跳過
		if (scanContext == lastScanContext && center.equals(lastScanCenter)) {
			return Optional.empty();
		}

		ScanResult result = scan(center, minY, maxYExclusive, oreMatcher);

		// 紀錄本次掃描狀態
		lastScanContext = scanContext;
		lastScanCenter = center.toImmutable();

		return Optional.of(result);
	}

	public ScanResult scan(BlockPos center, Predicate<BlockPos> oreMatcher) {
		return scan(
				center,
				center.getY() + DEFAULT_MIN_Y_OFFSET,
				center.getY() + DEFAULT_MAX_Y_OFFSET + 1,
				oreMatcher
		);
	}

	public ScanResult scan(
			BlockPos center,
			int minY,
			int maxYExclusive,
			Predicate<BlockPos> oreMatcher
	) {
		Set<BlockPos> foundPositions = new HashSet<>();
		int centerChunkX = Math.floorDiv(center.getX(), CHUNK_SIZE);
		int centerChunkZ = Math.floorDiv(center.getZ(), CHUNK_SIZE);
		int minX = (centerChunkX - CHUNK_RADIUS) * CHUNK_SIZE;
		int maxXExclusive = (centerChunkX + CHUNK_RADIUS + 1) * CHUNK_SIZE;
		int minZ = (centerChunkZ - CHUNK_RADIUS) * CHUNK_SIZE;
		int maxZExclusive = (centerChunkZ + CHUNK_RADIUS + 1) * CHUNK_SIZE;

		// 掃描玩家所在 Chunk 與周圍 8 個 Chunk；Y 使用目前維度的完整絕對高度
		for (int x = minX; x < maxXExclusive; x++) {
			for (int y = minY; y < maxYExclusive; y++) {
				for (int z = minZ; z < maxZExclusive; z++) {
					BlockPos position = new BlockPos(x, y, z);

					// 符合 oreMatcher 的方塊就記錄起來
					if (oreMatcher.test(position)) {
						foundPositions.add(position);
					}
				}
			}
		}

		orePositions = Set.copyOf(foundPositions);

		// 判斷礦物數量是否和上次不同
		boolean countChanged = lastReportedCount == null
				|| lastReportedCount != orePositions.size();

		lastReportedCount = orePositions.size();

		return new ScanResult(orePositions, countChanged);
	}

	// 取得目前掃描到的礦物位置
	public Set<BlockPos> getOrePositions() {
		return orePositions;
	}

	// 清除所有掃描紀錄
	public void reset() {
		orePositions = Set.of();
		lastReportedCount = null;
		lastScanContext = null;
		lastScanCenter = null;
	}

	// 包裝掃描結果
	public record ScanResult(Set<BlockPos> positions, boolean countChanged) {
		public int count() {
			return positions.size();
		}
	}
}
