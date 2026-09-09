package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class OreScanner {
	private static final int MIN_OFFSET = -8;
	private static final int MAX_OFFSET = 7;

	private Set<BlockPos> orePositions = Set.of();
	private Integer lastReportedCount;
	private Object lastScanContext;
	private BlockPos lastScanCenter;

	public Optional<ScanResult> scanIfNeeded(
			Object scanContext,
			BlockPos center,
			Predicate<BlockPos> oreMatcher
	) {
		if (scanContext == lastScanContext && center.equals(lastScanCenter)) {
			return Optional.empty();
		}

		ScanResult result = scan(center, oreMatcher);
		lastScanContext = scanContext;
		lastScanCenter = center.toImmutable();
		return Optional.of(result);
	}

	public ScanResult scan(BlockPos center, Predicate<BlockPos> oreMatcher) {
		Set<BlockPos> foundPositions = new HashSet<>();

		for (int x = MIN_OFFSET; x <= MAX_OFFSET; x++) {
			for (int y = MIN_OFFSET; y <= MAX_OFFSET; y++) {
				for (int z = MIN_OFFSET; z <= MAX_OFFSET; z++) {
					BlockPos position = center.add(x, y, z);
					if (oreMatcher.test(position)) {
						foundPositions.add(position);
					}
				}
			}
		}

		orePositions = Set.copyOf(foundPositions);
		boolean countChanged = lastReportedCount == null
				|| lastReportedCount != orePositions.size();
		lastReportedCount = orePositions.size();
		return new ScanResult(orePositions, countChanged);
	}

	public Set<BlockPos> getOrePositions() {
		return orePositions;
	}

	public void reset() {
		orePositions = Set.of();
		lastReportedCount = null;
		lastScanContext = null;
		lastScanCenter = null;
	}

	public record ScanResult(Set<BlockPos> positions, boolean countChanged) {
		public int count() {
			return positions.size();
		}
	}
}
