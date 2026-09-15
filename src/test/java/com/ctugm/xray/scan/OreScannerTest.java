package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 測試掃描邊界、座標保存與重掃條件。
class OreScannerTest {
	// 公開介面只保留正式遊戲流程正在使用的方法。
	@Test
	void exposesOnlyRuntimeScannerOperations() {
		Set<String> publicMethods = Arrays.stream(OreScanner.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.map(method -> method.getName())
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"scanWorldIfNeeded",
				"getOrePositions",
				"reset"
		), publicMethods);
	}

	// 掃描範圍必須涵蓋 3×3 Chunk 與預設 16 格高度。
	@Test
	void scansThreeByThreeChunksAcrossDefaultHeight() {
		OreScanner scanner = new OreScanner();
		BlockPos center = new BlockPos(10, 20, 30);

		OreScanner.ScanResult result = scanner.scan(center, position -> true);

		assertEquals(36_864, result.count());
		assertEquals(36_864, result.scannedBlockCount());
		assertTrue(result.positions().contains(new BlockPos(-16, 12, 0)));
		assertTrue(result.positions().contains(new BlockPos(31, 27, 47)));
		assertFalse(result.positions().contains(new BlockPos(-17, 12, 0)));
		assertFalse(result.positions().contains(new BlockPos(32, 12, 0)));
		assertFalse(result.positions().contains(new BlockPos(0, 11, 0)));
		assertFalse(result.positions().contains(new BlockPos(0, 28, 0)));
		assertFalse(result.positions().contains(new BlockPos(0, 12, -1)));
		assertFalse(result.positions().contains(new BlockPos(0, 12, 48)));
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
				new BlockPos(32, 0, 0)
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

	// 只有 Chunk、世界或 reset 狀態改變時才允許重新掃描。
	@Test
	void rescansOnlyAfterChunkWorldChangeOrReset() {
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
		assertEquals(36_864, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				firstWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isEmpty());
		assertEquals(36_864, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				firstWorld,
				center.add(1, 0, 0),
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isEmpty());
		assertEquals(36_864, testedPositions.get());

		OreScanner.ScanResult movedChunkResult = scanner.scanIfNeeded(
				firstWorld,
				center.add(16, 0, 0),
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).orElseThrow();
		assertEquals(12_288, movedChunkResult.scannedBlockCount());
		assertEquals(49_152, testedPositions.get());

		assertTrue(scanner.scanIfNeeded(
				secondWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(86_016, testedPositions.get());

		scanner.reset();

		assertTrue(scanner.scanIfNeeded(
				secondWorld,
				center,
				position -> {
					testedPositions.incrementAndGet();
					return false;
				}
		).isPresent());
		assertEquals(122_880, testedPositions.get());
	}
}
