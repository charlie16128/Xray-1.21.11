package com.ctugm.xray.scan;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

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

	// 使用世界的 ChunkSection 資料跳過全空區段。
	public Optional<ScanResult> scanWorldIfNeeded(
			World world,
			BlockPos center,
			Predicate<BlockState> oreMatcher
	) {
		int minY = world.getBottomY();
		int maxYExclusive = minY + world.getHeight();

		return scanChunksIfNeeded(
				world,
				center,
				minY,
				maxYExclusive,
				chunk -> scanWorldChunk(
						world,
						chunk,
						minY,
						maxYExclusive,
						oreMatcher
				)
		);
	}

	// 共用 Chunk 快取與重掃判斷；保留 package-private 供同套件測試使用。
	Optional<ScanResult> scanChunksIfNeeded(
			Object scanContext,
			BlockPos center,
			int minY,
			int maxYExclusive,
			ChunkScanner chunkScanner
	) {
		ChunkKey centerChunk = ChunkKey.from(center);
		boolean scanRangeChanged = lastMinY == null
				|| lastMinY != minY
				|| lastMaxYExclusive == null
				|| lastMaxYExclusive != maxYExclusive;

		// 同一世界、同一 Chunk 且高度範圍沒變時，不重複掃描。
		if (scanContext == lastScanContext
				&& centerChunk.equals(lastScanChunk)
				&& !scanRangeChanged) {
			return Optional.empty();
		}

		// 切換世界或高度範圍時，舊 Chunk 快取不能沿用。
		if (scanContext != lastScanContext || scanRangeChanged) {
			orePositionsByChunk.clear();
			lastReportedCount = null;
		}

		Set<ChunkKey> requiredChunks = chunksAround(centerChunk);
		orePositionsByChunk.keySet().retainAll(requiredChunks);
		int scannedBlockCount = 0;

		// 跨越一個 Chunk 時，通常只會新增並掃描外側的 3 個 Chunk。
		for (ChunkKey chunk : requiredChunks) {
			if (orePositionsByChunk.containsKey(chunk)) {
				continue;
			}

			ChunkScanResult chunkResult = chunkScanner.scan(chunk);
			orePositionsByChunk.put(chunk, chunkResult.positions());
			scannedBlockCount += chunkResult.scannedBlockCount();
		}

		ScanResult result = updateScanResult(scannedBlockCount);

		// 紀錄本次掃描狀態。
		lastScanContext = scanContext;
		lastScanChunk = centerChunk;
		lastMinY = minY;
		lastMaxYExclusive = maxYExclusive;

		return Optional.of(result);
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

	// 跳過全空 Section，只掃描仍包含方塊的 16×16×16 區段。
	private ChunkScanResult scanWorldChunk(
			World world,
			ChunkKey chunk,
			int minY,
			int maxYExclusive,
			Predicate<BlockState> oreMatcher
	) {
		Set<BlockPos> foundPositions = new HashSet<>();
		int scannedBlockCount = 0;
		int minX = chunk.x() * CHUNK_SIZE;
		int minZ = chunk.z() * CHUNK_SIZE;
		WorldChunk worldChunk = world.getChunk(chunk.x(), chunk.z());
		ChunkSection[] sections = worldChunk.getSectionArray();

		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			ChunkSection section = sections[sectionIndex];
			int sectionMinY = world.sectionIndexToCoord(sectionIndex) * CHUNK_SIZE;
			int sectionMaxYExclusive = sectionMinY + CHUNK_SIZE;
			int scanMinY = Math.max(minY, sectionMinY);
			int scanMaxYExclusive = Math.min(maxYExclusive, sectionMaxYExclusive);

			if (scanMinY >= scanMaxYExclusive || section.isEmpty()) {
				continue;
			}

			for (int localX = 0; localX < CHUNK_SIZE; localX++) {
				for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
					for (int y = scanMinY; y < scanMaxYExclusive; y++) {
						BlockState state = section.getBlockState(
								localX,
								y - sectionMinY,
								localZ
						);
						scannedBlockCount++;

						if (oreMatcher.test(state)) {
							foundPositions.add(new BlockPos(
									minX + localX,
									y,
									minZ + localZ
							));
						}
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

		// 判斷礦物數量是否和上次不同。
		boolean countChanged = lastReportedCount == null
				|| lastReportedCount != orePositions.size();

		lastReportedCount = orePositions.size();

		return new ScanResult(orePositions.size(), countChanged, scannedBlockCount);
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

	// 保存正式呼叫端使用的礦物數量、數量變化與本次掃描格數。
	public record ScanResult(
			int count,
			boolean countChanged,
			int scannedBlockCount
	) {
	}

	// 保存單一 Chunk 的礦物座標與掃描格數。
	record ChunkScanResult(
			Set<BlockPos> positions,
			int scannedBlockCount
	) {
	}

	@FunctionalInterface
	interface ChunkScanner {
		ChunkScanResult scan(ChunkKey chunk);
	}

	// 作為快取索引的 Chunk 座標。
	record ChunkKey(int x, int z) {
		// floorDiv 可讓負方塊座標正確對應到負 Chunk。
		private static ChunkKey from(BlockPos position) {
			return new ChunkKey(
					Math.floorDiv(position.getX(), CHUNK_SIZE),
					Math.floorDiv(position.getZ(), CHUNK_SIZE)
			);
		}
	}
}
