package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class OreScanner {
	// 掃描範圍：中心點周圍 -8 ~ 7，共 16 格
	private static final int MIN_OFFSET = -8;
	private static final int MAX_OFFSET = 7;

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
		// 掃描環境與中心位置沒變時，直接跳過
		if (scanContext == lastScanContext && center.equals(lastScanCenter)) {
			return Optional.empty();
		}

		ScanResult result = scan(center, oreMatcher);

		// 紀錄本次掃描狀態
		lastScanContext = scanContext;
		lastScanCenter = center.toImmutable();

		return Optional.of(result);
	}

	public ScanResult scan(BlockPos center, Predicate<BlockPos> oreMatcher) {
		Set<BlockPos> foundPositions = new HashSet<>();

		// 掃描中心周圍 16 × 16 × 16 的方塊
		for (int x = MIN_OFFSET; x <= MAX_OFFSET; x++) {
			for (int y = MIN_OFFSET; y <= MAX_OFFSET; y++) {
				for (int z = MIN_OFFSET; z <= MAX_OFFSET; z++) {
					BlockPos position = center.add(x, y, z);

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
