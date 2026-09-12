package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

// 掃描玩家周圍 3×3 Chunk，並快取每個 Chunk 的礦物座標。
public final class OreScanner {
	// Minecraft 每個 Chunk 在 X/Z 方向皆為 16 格。
	private static final int CHUNK_SIZE = 16;
	// 半徑 1 代表中心 Chunk 加周圍 8 個 Chunk，共 3×3。
	private static final int CHUNK_RADIUS = 1;
	// 未指定世界高度時，保留舊 API 的玩家上下各 8 格掃描範圍。
	private static final int DEFAULT_MIN_Y_OFFSET = -8;
	private static final int DEFAULT_MAX_Y_OFFSET = 7;

	// 提供給 Renderer 的完整礦物座標集合。
	private Set<BlockPos> orePositions = Set.of();
	// 每個 Chunk 各自保存掃描結果，跨 Chunk 時可重用重疊區域。
	private final Map<ChunkKey, Set<BlockPos>> orePositionsByChunk = new HashMap<>();

	// 上一次回報的總數，用來避免重複顯示相同的礦物數量。
	private Integer lastReportedCount;
	// 使用物件身分區分世界；切換世界後舊快取不可沿用。
	private Object lastScanContext;
	// 記錄玩家上次所在 Chunk，作為是否需要重掃的判斷依據。
	private ChunkKey lastScanChunk;
	// 記錄上次的絕對高度範圍，維度高度改變時會使快取失效。
	private Integer lastMinY;
	private Integer lastMaxYExclusive;

	// 使用預設高度範圍，必要時才掃描。
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

	// 只有 Chunk、世界或高度範圍改變時才更新掃描結果。
	public Optional<ScanResult> scanIfNeeded(
			Object scanContext,
			BlockPos center,
			int minY,
			int maxYExclusive,
			Predicate<BlockPos> oreMatcher
	) {
		ChunkKey centerChunk = ChunkKey.from(center);
		boolean scanRangeChanged = lastMinY == null
				|| lastMinY != minY
				|| lastMaxYExclusive == null
				|| lastMaxYExclusive != maxYExclusive;

		// 同一世界、同一 Chunk 且高度範圍沒變時，不重複掃描
		if (scanContext == lastScanContext
				&& centerChunk.equals(lastScanChunk)
				&& !scanRangeChanged) {
			return Optional.empty();
		}

		// 切換世界或高度範圍時，舊 Chunk 快取不能沿用
		if (scanContext != lastScanContext || scanRangeChanged) {
			orePositionsByChunk.clear();
			lastReportedCount = null;
		}

		Set<ChunkKey> requiredChunks = chunksAround(centerChunk);
		orePositionsByChunk.keySet().retainAll(requiredChunks);
		int scannedBlockCount = 0;

		// 跨越一個 Chunk 時，通常只會新增並掃描外側的 3 個 Chunk
		for (ChunkKey chunk : requiredChunks) {
			if (orePositionsByChunk.containsKey(chunk)) {
				continue;
			}

			ChunkScanResult chunkResult = scanChunk(
					chunk,
					minY,
					maxYExclusive,
					oreMatcher
			);
			orePositionsByChunk.put(chunk, chunkResult.positions());
			scannedBlockCount += chunkResult.scannedBlockCount();
		}

		ScanResult result = updateScanResult(scannedBlockCount);

		// 紀錄本次掃描狀態
		lastScanContext = scanContext;
		lastScanChunk = centerChunk;
		lastMinY = minY;
		lastMaxYExclusive = maxYExclusive;

		return Optional.of(result);
	}

	// 使用預設高度範圍立即掃描。
	public ScanResult scan(BlockPos center, Predicate<BlockPos> oreMatcher) {
		return scan(
				center,
				center.getY() + DEFAULT_MIN_Y_OFFSET,
				center.getY() + DEFAULT_MAX_Y_OFFSET + 1,
				oreMatcher
		);
	}

	// 立即掃描中心周圍的完整 3×3 Chunk。
	public ScanResult scan(
			BlockPos center,
			int minY,
			int maxYExclusive,
			Predicate<BlockPos> oreMatcher
	) {
		orePositionsByChunk.clear();
		int scannedBlockCount = 0;

		for (ChunkKey chunk : chunksAround(ChunkKey.from(center))) {
			ChunkScanResult chunkResult = scanChunk(
					chunk,
					minY,
					maxYExclusive,
					oreMatcher
			);
			orePositionsByChunk.put(chunk, chunkResult.positions());
			scannedBlockCount += chunkResult.scannedBlockCount();
		}

		return updateScanResult(scannedBlockCount);
	}

	// 建立中心周圍的 3×3 Chunk 座標集合。
	private Set<ChunkKey> chunksAround(ChunkKey centerChunk) {
		Set<ChunkKey> chunks = new HashSet<>();

		for (int chunkX = centerChunk.x() - CHUNK_RADIUS;
			 chunkX <= centerChunk.x() + CHUNK_RADIUS;
			 chunkX++) {
			for (int chunkZ = centerChunk.z() - CHUNK_RADIUS;
				 chunkZ <= centerChunk.z() + CHUNK_RADIUS;
				 chunkZ++) {
				chunks.add(new ChunkKey(chunkX, chunkZ));
			}
		}

		return chunks;
	}

	// 掃描單一 Chunk，並重用 Mutable 座標減少物件配置。
	private ChunkScanResult scanChunk(
			ChunkKey chunk,
			int minY,
			int maxYExclusive,
			Predicate<BlockPos> oreMatcher
	) {
		Set<BlockPos> foundPositions = new HashSet<>();
		int scannedBlockCount = 0;
		int minX = chunk.x() * CHUNK_SIZE;
		int minZ = chunk.z() * CHUNK_SIZE;
		BlockPos.Mutable position = new BlockPos.Mutable();

		// 每次 oreMatcher.test 都代表實際檢查了一個世界方塊。
		for (int x = minX; x < minX + CHUNK_SIZE; x++) {
			for (int z = minZ; z < minZ + CHUNK_SIZE; z++) {
				for (int y = minY; y < maxYExclusive; y++) {
					position.set(x, y, z);
					scannedBlockCount++;

					if (oreMatcher.test(position)) {
						foundPositions.add(position.toImmutable());
					}
				}
			}
		}

		return new ChunkScanResult(
				Set.copyOf(foundPositions),
				scannedBlockCount
		);
	}

	// 合併所有 Chunk 快取並建立掃描結果。
	private ScanResult updateScanResult(int scannedBlockCount) {
		Set<BlockPos> foundPositions = new HashSet<>();
		for (Set<BlockPos> chunkPositions : orePositionsByChunk.values()) {
			foundPositions.addAll(chunkPositions);
		}

		orePositions = Set.copyOf(foundPositions);

		// 判斷礦物數量是否和上次不同
		boolean countChanged = lastReportedCount == null
				|| lastReportedCount != orePositions.size();

		lastReportedCount = orePositions.size();

		return new ScanResult(orePositions, countChanged, scannedBlockCount);
	}

	// 回傳目前範圍內不可修改的礦物座標集合。
	public Set<BlockPos> getOrePositions() {
		return orePositions;
	}

	// 清除所有座標與掃描快取。
	public void reset() {
		orePositions = Set.of();
		lastReportedCount = null;
		lastScanContext = null;
		lastScanChunk = null;
		lastMinY = null;
		lastMaxYExclusive = null;
		orePositionsByChunk.clear();
	}

	// 保存合併後的座標、數量變化與本次掃描格數。
	public record ScanResult(
			Set<BlockPos> positions,
			boolean countChanged,
			int scannedBlockCount
	) {
		public int count() {
			return positions.size();
		}
	}

	// 保存單一 Chunk 的礦物座標與掃描格數。
	private record ChunkScanResult(
			Set<BlockPos> positions,
			int scannedBlockCount
	) {
	}

	// 作為快取索引的 Chunk 座標。
	private record ChunkKey(int x, int z) {
		// floorDiv 可讓負方塊座標正確對應到負 Chunk。
		private static ChunkKey from(BlockPos position) {
			return new ChunkKey(
					Math.floorDiv(position.getX(), CHUNK_SIZE),
					Math.floorDiv(position.getZ(), CHUNK_SIZE)
			);
		}
	}
}
