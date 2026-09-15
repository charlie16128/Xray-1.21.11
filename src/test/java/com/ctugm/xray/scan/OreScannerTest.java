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

// 測試正式公開介面、Chunk 快取、座標保存與重掃條件。
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

	// 初次掃描 3×3 Chunk；跨越一個 Chunk 後只掃描新進入的 3 個 Chunk。
	@Test
	void scansNineChunksThenOnlyThreeAfterCrossingAChunkBoundary() {
		OreScanner scanner = new OreScanner();
		Object world = new Object();
		AtomicInteger scannedChunks = new AtomicInteger();

		OreScanner.ScanResult first = scanner.scanChunksIfNeeded(
				world,
				new BlockPos(0, 0, 0),
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		).orElseThrow();

		assertEquals(9, scannedChunks.get());
		assertEquals(9, first.count());
		assertEquals(9 * 4_096, first.scannedBlockCount());
		assertTrue(scanner.scanChunksIfNeeded(
				world,
				new BlockPos(15, 0, 15),
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		).isEmpty());
		assertEquals(9, scannedChunks.get());

		OreScanner.ScanResult moved = scanner.scanChunksIfNeeded(
				world,
				new BlockPos(16, 0, 0),
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		).orElseThrow();

		assertEquals(12, scannedChunks.get());
		assertEquals(9, moved.count());
		assertEquals(3 * 4_096, moved.scannedBlockCount());
	}

	// 換世界或 reset 後，全部 9 個 Chunk 都必須重新掃描。
	@Test
	void changingContextOrResettingInvalidatesEveryCachedChunk() {
		OreScanner scanner = new OreScanner();
		AtomicInteger scannedChunks = new AtomicInteger();
		BlockPos center = new BlockPos(0, 0, 0);
		Object firstWorld = new Object();
		Object secondWorld = new Object();

		scanner.scanChunksIfNeeded(
				firstWorld,
				center,
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		);
		OreScanner.ScanResult changedWorld = scanner.scanChunksIfNeeded(
				secondWorld,
				center,
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		).orElseThrow();

		assertEquals(18, scannedChunks.get());
		assertEquals(9 * 4_096, changedWorld.scannedBlockCount());

		scanner.reset();
		OreScanner.ScanResult afterReset = scanner.scanChunksIfNeeded(
				secondWorld,
				center,
				0,
				16,
				chunk -> countedChunkResult(chunk, scannedChunks)
		).orElseThrow();

		assertEquals(27, scannedChunks.get());
		assertEquals(9 * 4_096, afterReset.scannedBlockCount());
	}

	// 座標集合不可修改；只有總數改變時 countChanged 才為 true。
	@Test
	void mergesImmutablePositionsAndReportsOnlyCountChanges() {
		OreScanner scanner = new OreScanner();
		Object world = new Object();
		BlockPos firstOre = new BlockPos(-16, 0, 0);
		BlockPos replacementOre = new BlockPos(32, 0, 0);
		BlockPos additionalOre = new BlockPos(48, 0, 0);
		OreScanner.ChunkScanner chunkScanner = chunk -> {
			boolean containsOre = chunk.z() == 0
					&& (chunk.x() == -1 || chunk.x() == 2 || chunk.x() == 3);
			return new OreScanner.ChunkScanResult(
					containsOre
							? Set.of(new BlockPos(chunk.x() * 16, 0, 0))
							: Set.of(),
					4_096
			);
		};

		OreScanner.ScanResult first = scanner.scanChunksIfNeeded(
				world,
				new BlockPos(0, 0, 0),
				0,
				16,
				chunkScanner
		).orElseThrow();
		assertTrue(first.countChanged());
		assertEquals(Set.of(firstOre), scanner.getOrePositions());
		assertThrows(UnsupportedOperationException.class,
				() -> scanner.getOrePositions().add(replacementOre));

		OreScanner.ScanResult sameCount = scanner.scanChunksIfNeeded(
				world,
				new BlockPos(16, 0, 0),
				0,
				16,
				chunkScanner
		).orElseThrow();
		assertFalse(sameCount.countChanged());
		assertEquals(Set.of(replacementOre), scanner.getOrePositions());

		OreScanner.ScanResult changedCount = scanner.scanChunksIfNeeded(
				world,
				new BlockPos(32, 0, 0),
				0,
				16,
				chunkScanner
		).orElseThrow();
		assertTrue(changedCount.countChanged());
		assertEquals(2, changedCount.count());
		assertEquals(Set.of(replacementOre, additionalOre), scanner.getOrePositions());
	}

	private static OreScanner.ChunkScanResult countedChunkResult(
			OreScanner.ChunkKey chunk,
			AtomicInteger scannedChunks
	) {
		scannedChunks.incrementAndGet();
		return new OreScanner.ChunkScanResult(
				Set.of(new BlockPos(chunk.x() * 16, 0, chunk.z() * 16)),
				4_096
		);
	}

	private static OreScanner.ScanResult scanSingleCenterOre(
			OreScanner scanner,
			Object context,
			BlockPos ore
	) {
		return scanner.scanChunksIfNeeded(
				context,
				new BlockPos(0, 0, 0),
				0,
				16,
				chunk -> new OreScanner.ChunkScanResult(
						chunk.x() == 0 && chunk.z() == 0 ? Set.of(ore) : Set.of(),
						4_096
				)
		).orElseThrow();
	}
}
