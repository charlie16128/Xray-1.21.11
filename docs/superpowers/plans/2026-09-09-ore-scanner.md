# Ore Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scan and retain diamond-ore coordinates in an exact 16 x 16 x 16 volume when Xray is enabled, rescanning on block movement and reporting only count changes.

**Architecture:** A reusable `OreScanner` enumerates and filters block positions without depending on client-world APIs, owns an immutable current result, and tracks reported counts. `XrayClient` supplies the Minecraft-specific diamond predicate and controls enable, movement, world-change, reset, and chat behavior.

**Tech Stack:** Java 21, Minecraft 1.21.11 with Yarn mappings, Fabric Loader/API, Gradle, JUnit Jupiter 5.11.4.

---

The workspace has no `.git` repository, so commit steps are intentionally omitted.

### Task 1: Specify the reusable 16-cube scanner

**Files:**
- Create: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`

- [ ] **Step 1: Write failing scanner behavior tests**

Create `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`:

```java
package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreScannerTest {
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
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest
```

Expected: `compileTestJava` fails because `OreScanner` does not exist.

### Task 2: Implement `OreScanner`

**Files:**
- Create: `src/main/java/com/ctugm/xray/scan/OreScanner.java`
- Test: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`

- [ ] **Step 1: Add the minimal reusable scanner**

Create `src/main/java/com/ctugm/xray/scan/OreScanner.java`:

```java
package com.ctugm.xray.scan;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class OreScanner {
	private static final int MIN_OFFSET = -8;
	private static final int MAX_OFFSET = 7;

	private Set<BlockPos> orePositions = Set.of();
	private Integer lastReportedCount;

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
	}

	public record ScanResult(Set<BlockPos> positions, boolean countChanged) {
		public int count() {
			return positions.size();
		}
	}
}
```

- [ ] **Step 2: Run the focused tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests com.ctugm.xray.scan.OreScannerTest
```

Expected: all three scanner tests pass with `BUILD SUCCESSFUL`.

### Task 3: Integrate diamond scanning with the Xray lifecycle

**Files:**
- Modify: `src/client/java/com/ctugm/xray/client/XrayClient.java`
- Test: `src/test/java/com/ctugm/xray/scan/OreScannerTest.java`

- [ ] **Step 1: Add movement- and world-aware scanning**

Replace `XrayClient.java` with:

```java
package com.ctugm.xray.client;

import com.ctugm.xray.XrayToggleState;
import com.ctugm.xray.scan.OreScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public class XrayClient implements ClientModInitializer {
	private static final KeyBinding.Category XRAY_CATEGORY = KeyBinding.Category.create(
			Identifier.of("xray", "general")
	);
	private static final XrayToggleState XRAY_STATE = new XrayToggleState();
	private static final OreScanner ORE_SCANNER = new OreScanner();

	private static BlockPos lastScanCenter;
	private static ClientWorld lastScanWorld;

	@Override
	public void onInitializeClient() {
		KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.xray.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				XRAY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				if (client.player == null) {
					continue;
				}

				client.player.sendMessage(Text.literal(XRAY_STATE.toggleMessage()), false);
				if (!XRAY_STATE.isEnabled()) {
					resetScanState();
				}
			}

			if (!XRAY_STATE.isEnabled()) {
				return;
			}

			if (client.player == null || client.world == null) {
				resetScanState();
				return;
			}

			BlockPos center = client.player.getBlockPos().toImmutable();
			if (client.world == lastScanWorld && center.equals(lastScanCenter)) {
				return;
			}

			OreScanner.ScanResult result = ORE_SCANNER.scan(center, position -> {
				if (client.world.isOutOfHeightLimit(position)) {
					return false;
				}

				BlockState state = client.world.getBlockState(position);
				return state.isOf(Blocks.DIAMOND_ORE)
						|| state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE);
			});
			lastScanCenter = center;
			lastScanWorld = client.world;

			if (result.countChanged()) {
				client.player.sendMessage(
						Text.literal("[Xray] Diamond ores: " + result.count()),
						false
				);
			}
		});
	}

	public static Set<BlockPos> getDetectedOrePositions() {
		return ORE_SCANNER.getOrePositions();
	}

	private static void resetScanState() {
		ORE_SCANNER.reset();
		lastScanCenter = null;
		lastScanWorld = null;
	}
}
```

- [ ] **Step 2: Compile the client integration**

Run:

```powershell
.\gradlew.bat compileClientJava
```

Expected: `BUILD SUCCESSFUL`, proving the pinned Yarn APIs for world height, block state, block constants, and immutable positions are valid.

- [ ] **Step 3: Run the complete build freshly**

Run:

```powershell
.\gradlew.bat build --rerun-tasks
```

Expected: all scanner and toggle tests pass and the remapped mod JAR is produced with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Perform a manual gameplay smoke test when a client is available**

Launch the development client, join a world, and verify:

1. Enabling Xray prints the enable message followed by one diamond count.
2. Looking around without changing block position does not print another count.
3. Moving one block rescans, but prints only when the count changes.
4. Both regular and deepslate diamond ore coordinates appear in `getDetectedOrePositions()` when they are within the scan cube.
5. Disabling Xray clears `getDetectedOrePositions()`.
