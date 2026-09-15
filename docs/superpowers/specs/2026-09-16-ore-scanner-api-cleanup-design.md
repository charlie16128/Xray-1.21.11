# OreScanner API Cleanup Design

## Goal

Remove legacy and test-only public scanning APIs from `OreScanner` while preserving the scanner behavior used by `XrayClient`.

## Public API

`OreScanner` will keep only the runtime-facing operations:

- `scanWorldIfNeeded(World, BlockPos, Predicate<BlockState>)`
- `getOrePositions()`
- `reset()`
- `ScanResult`, containing only the values consumed at runtime: `count`, `countChanged`, and `scannedBlockCount`

The coordinate-predicate `scan(...)` and `scanIfNeeded(...)` overloads will be removed. The default player-relative height constants and the coordinate-based `scanChunk(...)` implementation will also be removed because nothing in runtime code uses them.

## Internal Structure

The existing 3×3 Chunk selection, per-Chunk cache, world identity check, scan-height check, result merging, and empty-`ChunkSection` optimization will remain unchanged.

The shared Chunk-cache routine and its small supporting types will become package-private test seams. They are implementation details available only to tests in `com.ctugm.xray.scan`; they are not public module APIs.

## Data Flow

`XrayClient` continues to call `scanWorldIfNeeded(...)`. That method derives the dimension height, scans only missing Chunks through `scanWorldChunk(...)`, updates the cached ore-position set, and returns counts needed for chat messages. `OreRenderer` continues to retrieve the immutable position set through `getOrePositions()`.

## Tests

Tests will no longer call the removed coordinate-predicate public methods. They will verify the cache core through the package-private seam:

- the initial scan requests all nine Chunks;
- another call within the same Chunk performs no scan;
- moving to an adjacent Chunk scans only the three newly required Chunks;
- changing scan context or calling `reset()` causes a full scan again;
- merged ore positions remain immutable and count changes are reported correctly.

A reflection-based API-surface test will assert that the removed public entry points do not return.

## Compatibility and Errors

No runtime caller changes are required because `XrayClient` already uses only the retained API. World access and predicate exceptions keep their existing behavior; this cleanup introduces no new error handling or dependencies.
