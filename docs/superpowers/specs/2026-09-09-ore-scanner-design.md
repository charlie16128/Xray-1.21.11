# Ore Scanner Design

## Goal

While Xray is enabled, scan an exact 16 x 16 x 16 block volume centered on the player's current block position. Detect both normal and deepslate diamond ore, retain every detected coordinate for future rendering, and report the count only when it changes.

This change finds and stores ore positions. It does not render them through terrain.

## Architecture

Add a reusable `OreScanner` whose scan operation accepts a center position and an ore-matching predicate. The scanner owns the latest immutable coordinate set and the previously reported count, allowing future ore types to reuse the same volume traversal and result tracking.

The Fabric client initializer remains responsible for Minecraft-specific integration: it supplies a predicate that reads the client world and recognizes `Blocks.DIAMOND_ORE` and `Blocks.DEEPSLATE_DIAMOND_ORE`, triggers scans, and writes count changes to local chat.

## Scan Volume

For each axis, scan offsets `-8` through `+7`, inclusive. This produces exactly 16 positions per axis and 4,096 candidate blocks total. The asymmetry is required because an even-sized cube cannot have one exact center block with equal extents on both sides.

Positions outside the world's build-height limits are tested as non-matches. Blocks in the client world around the player are read without requesting or loading additional chunks.

## Triggering and Lifecycle

- Enabling Xray forces an immediate scan during the same end-client-tick callback.
- While enabled, moving to a different block coordinate triggers another scan.
- Camera movement within the same block does not scan again.
- A world change triggers another scan even if the new player coordinates are identical.
- Disabling Xray or leaving the world clears stored coordinates, the last scan origin, and count-reporting history.
- Xray starts disabled, so no scanning occurs before the first enable action.

## Results and Chat

Every completed scan replaces the stored immutable coordinate set. The positions are updated even when the number of matches stays the same.

The first completed scan after enable reports `[Xray] Diamond ores: N`. Later scans report that message only when `N` differs from the previously reported count. Resetting or disabling makes the next scan an initial report again.

## Components

- `OreScanner`: enumerates the exact cube, filters coordinates, stores the latest immutable results, detects count changes, and resets scan state.
- `OreScannerTest`: verifies volume size and bounds, filtering, immutable current results, same-count position replacement, changed-count reporting, and reset behavior.
- `XrayClient`: integrates world/player lifecycle, detects block-position or world changes, supplies the diamond-ore predicate, and emits local chat messages.

## Error Handling

- When either the player or world is absent, scan state is cleared and no count message is emitted.
- World-height positions are skipped before block-state access.
- The scan runs only on the client thread through `ClientTickEvents.END_CLIENT_TICK`, so the stored result needs no concurrent mutation support.

## Verification

- Follow a red-green TDD cycle for `OreScanner` behavior.
- Run the focused scanner tests.
- Compile the client integration against Minecraft 1.21.11 Yarn mappings.
- Run the complete Gradle build with all tasks rerun.
- A manual client smoke test should verify enable-time scanning, movement-triggered rescans, count-change suppression, and clearing on disable.
