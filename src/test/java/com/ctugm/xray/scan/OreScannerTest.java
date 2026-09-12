package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 測試掃描邊界、座標保存與重掃條件。
class OreScannerTest {
	// 掃描範圍的每一軸都必須精確包含 16 格。
	@Test
	void scansExactlySixteenBlocksOnEveryAxis() {
		OreScanner scanner = new OreScanner();
		BlockPos center = new BlockPos(10, 20, 30);

		OreScanner.ScanResult result = scanner.scan(center, position -> true);

		assertEquals(4096, result.count());
		assertTrue(result.positions().contains(center.add(-8, -8, -8)));
		assertTrue(result.positions().contains(center.add(7, 7, 7)));
		assertFalse(result.positions().contains(center.add(8, 0, 0)));
		assertFalse(result.positions().contains(center.add(0, 8, 0)));
		assertFalse(result.positions().contains(center.add(0, 0, 8)));
	}

	// 只保存 matcher 接受的座標，且公開集合不可被呼叫端修改。
	@Test
	void filtersAndStoresAnImmutableCoordinateSet() {
		OreScanner scanner = new OreScanner();
		BlockPos center = new BlockPos(0, 0, 0);
		Set<BlockPos> matching = Set.of(
				center.add(-8, -8, -8),
				center,
				center.add(7, 7, 7),
				center.add(8, 0, 0)
		);

		OreScanner.ScanResult result = scanner.scan(center, matching::contains);

		assertEquals(Set.of(
				center.add(-8, -8, -8),
				center,
				center.add(7, 7, 7)
		), result.positions());
		assertEquals(result.positions(), scanner.getOrePositions());
		assertThrows(UnsupportedOperationException.class,
				() -> result.positions().add(center.add(1, 1, 1)));
	}

	// 礦物數量相同時不重複標記變更，reset 後第一次結果要重新回報。
	@Test
	void reportsOnlyInitialAndChangedCountsAndResetStartsOver() {
		OreScanner scanner = new OreScanner();
		BlockPos center = new BlockPos(0, 0, 0);

		OreScanner.ScanResult first = scanner.scan(center, Set.of(center)::contains);
		OreScanner.ScanResult sameCount = scanner.scan(
				center,
				Set.of(center.add(1, 0, 0))::contains
		);
		OreScanner.ScanResult changedCount = scanner.scan(
				center,
				Set.of(center, center.add(1, 0, 0))::contains
		);

		assertTrue(first.countChanged());
		assertFalse(sameCount.countChanged());
		assertEquals(Set.of(center.add(1, 0, 0)), sameCount.positions());
		assertTrue(changedCount.countChanged());

		scanner.reset();

		assertTrue(scanner.getOrePositions().isEmpty());
		assertTrue(scanner.scan(center, Set.of(center, center.add(1, 0, 0))::contains)
				.countChanged());
	}

	// 只有位置、世界或 reset 狀態改變時才允許重新掃描。
	@Test
	void rescansOnlyAfterMovementWorldChangeOrReset() {
		OreScanner scanner = new OreScanner();
		BlockPos center = new BlockPos(0, 0, 0);
		Object firstWorld = new Object();
		Object secondWorld = new Object();
		AtomicInteger testedPositions = new AtomicInteger();

		assertTrue(scanner.scanIfNeeded(
				firstWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(4096, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				firstWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isEmpty());
		assertEquals(4096, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				firstWorld,
				center.add(1, 0, 0),
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(8192, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				secondWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(12288, testedPositions.get());

		scanner.reset();

		assertTrue(scanner.scanIfNeeded(
				secondWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(16384, testedPositions.get());
	}
}
