# OreScanner API Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every unused public `OreScanner` entry point while preserving the world scanner, Chunk cache, renderer data, and runtime counters.

**Architecture:** Keep `scanWorldIfNeeded(...)` as the sole scan entry point used by `XrayClient`. Retain the stateful Chunk-cache routine as a package-private test seam, remove the coordinate-predicate scanner branch, and return only runtime-consumed counters from `ScanResult` while positions remain available through `getOrePositions()`.

**Tech Stack:** Java 21, Fabric Loom, Minecraft 1.21.11 mappings, JUnit Jupiter 5, Gradle Wrapper

---

## File map

- Modify `src/main/java/com/ctugm/xray/scan/OreScanner.java`: remove legacy overloads and coordinate scanning, narrow the result record, and expose only the cache seam to package tests.
- Modify `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`: lock down the public surface and test caching through the package-private seam.
- No runtime caller changes are expected in `XrayClient` or `OreRenderer`.

### Task 1: Lock down the public API

**Files:**
- Modify: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`
- Modify: `src/main/java/com/ctugm/xray/scan/OreScanner.java`

- [ ] **Step 1: Add a failing public-surface test**

Add these imports:

```java
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;
```

Add this test:

```java
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
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest.exposesOnlyRuntimeScannerOperations
```

Expected: FAIL because the public method set also contains `scan` and `scanIfNeeded`.

- [ ] **Step 3: Make the minimal GREEN visibility change**

Remove the `public` modifier from both `scan(...)` methods and both coordinate-predicate `scanIfNeeded(...)` methods. Remove `private` from `scanChunksIfNeeded(...)`, `ChunkScanResult`, `ChunkScanner`, and `ChunkKey` so package tests can use the cache seam. Do not change method bodies in this step.

- [ ] **Step 4: Run the focused scanner suite**

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest
```

Expected: PASS.

- [ ] **Step 5: Commit the API boundary**

```powershell
git add src/main/java/com/ctugm/xray/scan/OreScanner.java src/test/java/com/ctugm/xray/scan/OreScannerTest.java
git commit -m "test: lock down OreScanner public API"
```

### Task 2: Move behavioral tests to the cache seam

**Files:**
- Modify: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`

- [ ] **Step 1: Replace coordinate-scanner tests**

Keep `exposesOnlyRuntimeScannerOperations()` and replace the other tests with:

```java
@Test
void scansNineChunksThenOnlyThreeAfterCrossingAChunkBoundary() {
	OreScanner scanner = new OreScanner();
	Object world = new Object();
	AtomicInteger scannedChunks = new AtomicInteger();

	OreScanner.ScanResult first = scanner.scanChunksIfNeeded(
			world, new BlockPos(0, 0, 0), 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks)
	).orElseThrow();

	assertEquals(9, scannedChunks.get());
	assertEquals(9, first.count());
	assertEquals(9 * 4_096, first.scannedBlockCount());
	assertTrue(scanner.scanChunksIfNeeded(
			world, new BlockPos(15, 0, 15), 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks)
	).isEmpty());
	assertEquals(9, scannedChunks.get());

	OreScanner.ScanResult moved = scanner.scanChunksIfNeeded(
			world, new BlockPos(16, 0, 0), 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks)
	).orElseThrow();

	assertEquals(12, scannedChunks.get());
	assertEquals(9, moved.count());
	assertEquals(3 * 4_096, moved.scannedBlockCount());
}

@Test
void changingContextOrResettingInvalidatesEveryCachedChunk() {
	OreScanner scanner = new OreScanner();
	AtomicInteger scannedChunks = new AtomicInteger();
	BlockPos center = new BlockPos(0, 0, 0);
	Object firstWorld = new Object();
	Object secondWorld = new Object();

	scanner.scanChunksIfNeeded(firstWorld, center, 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks));
	OreScanner.ScanResult changedWorld = scanner.scanChunksIfNeeded(
			secondWorld, center, 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks)
	).orElseThrow();

	assertEquals(18, scannedChunks.get());
	assertEquals(9 * 4_096, changedWorld.scannedBlockCount());

	scanner.reset();
	OreScanner.ScanResult afterReset = scanner.scanChunksIfNeeded(
			secondWorld, center, 0, 16,
			chunk -> countedChunkResult(chunk, scannedChunks)
	).orElseThrow();

	assertEquals(27, scannedChunks.get());
	assertEquals(9 * 4_096, afterReset.scannedBlockCount());
}

@Test
void mergesImmutablePositionsAndReportsOnlyCountChanges() {
	OreScanner scanner = new OreScanner();
	BlockPos firstOre = new BlockPos(0, 0, 0);
	BlockPos replacementOre = new BlockPos(1, 0, 0);

	OreScanner.ScanResult first = scanSingleCenterOre(scanner, new Object(), firstOre);
	assertTrue(first.countChanged());
	assertEquals(Set.of(firstOre), scanner.getOrePositions());
	assertThrows(UnsupportedOperationException.class,
			() -> scanner.getOrePositions().add(replacementOre));

	OreScanner.ScanResult sameCount = scanSingleCenterOre(
			scanner, new Object(), replacementOre);
	assertFalse(sameCount.countChanged());
	assertEquals(Set.of(replacementOre), scanner.getOrePositions());

	OreScanner.ScanResult changedCount = scanner.scanChunksIfNeeded(
			new Object(), new BlockPos(0, 0, 0), 0, 16,
			chunk -> new OreScanner.ChunkScanResult(
					chunk.x() == 0 && chunk.z() == 0
							? Set.of(firstOre, replacementOre)
							: Set.of(),
					4_096
			)
	).orElseThrow();
	assertTrue(changedCount.countChanged());
	assertEquals(2, changedCount.count());
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
			context, new BlockPos(0, 0, 0), 0, 16,
			chunk -> new OreScanner.ChunkScanResult(
					chunk.x() == 0 && chunk.z() == 0 ? Set.of(ore) : Set.of(),
					4_096
			)
	).orElseThrow();
}
```

- [ ] **Step 2: Verify the migrated tests**

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest
```

Expected: PASS while the legacy implementations still exist.

- [ ] **Step 3: Commit the test migration**

```powershell
git add src/test/java/com/ctugm/xray/scan/OreScannerTest.java
git commit -m "test: cover OreScanner cache through internal seam"
```

### Task 3: Remove obsolete code and narrow the result

**Files:**
- Modify: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`
- Modify: `src/main/java/com/ctugm/xray/scan/OreScanner.java`

- [ ] **Step 1: Add a failing result-shape test**

```java
@Test
void scanResultContainsOnlyRuntimeCounters() {
	assertEquals(
			Set.of("count", "countChanged", "scannedBlockCount"),
			Arrays.stream(OreScanner.ScanResult.class.getRecordComponents())
					.map(component -> component.getName())
					.collect(Collectors.toSet())
	);
}
```

- [ ] **Step 2: Run the shape test and verify RED**

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest.scanResultContainsOnlyRuntimeCounters
```

Expected: FAIL because the record has a `positions` component instead of `count`.

- [ ] **Step 3: Delete the coordinate-scanning branch**

Delete `DEFAULT_MIN_Y_OFFSET`, `DEFAULT_MAX_Y_OFFSET`, both coordinate-predicate `scanIfNeeded(...)` overloads, both `scan(...)` overloads, and `scanChunk(...)`. Keep the world method, cache method, `scanWorldChunk(...)`, result merging, getter, and reset logic.

- [ ] **Step 4: Replace result construction and definition**

Replace:

```java
return new ScanResult(orePositions, countChanged, scannedBlockCount);
```

with:

```java
return new ScanResult(orePositions.size(), countChanged, scannedBlockCount);
```

Replace the record with:

```java
public record ScanResult(
		int count,
		boolean countChanged,
		int scannedBlockCount
) {
}
```

- [ ] **Step 5: Verify the focused suite and production compilation**

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest
.\gradlew.bat compileClientJava
```

Expected: both commands report BUILD SUCCESSFUL.

- [ ] **Step 6: Commit the cleanup**

```powershell
git add src/main/java/com/ctugm/xray/scan/OreScanner.java src/test/java/com/ctugm/xray/scan/OreScannerTest.java
git commit -m "refactor: remove legacy OreScanner APIs"
```

### Task 4: Verify the repository

**Files:**
- Verify: `src/main/java/com/ctugm/xray/scan/OreScanner.java`
- Verify: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`

- [ ] **Step 1: Confirm removed symbols have no references**

```powershell
rg -n "DEFAULT_MIN_Y_OFFSET|DEFAULT_MAX_Y_OFFSET|Predicate<BlockPos>|scanChunk\(" src/main src/test
```

Expected: no matches.

- [ ] **Step 2: Run all tests**

```powershell
.\gradlew.bat test
```

Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 3: Compile client sources**

```powershell
.\gradlew.bat compileClientJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Inspect final changes**

```powershell
git diff -- src/main/java/com/ctugm/xray/scan/OreScanner.java src/test/java/com/ctugm/xray/scan/OreScannerTest.java
git status --short
```

Expected: only the planned scanner and test cleanup, with no unrelated changes.
