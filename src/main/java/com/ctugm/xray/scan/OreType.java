package com.ctugm.xray.scan;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum OreType {
    // COAL(0xFF454545, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    // COPPER(0xFFE67E52, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    // IRON(0xFFD8B49A, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    // GOLD(0xFFFFD84D, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    // REDSTONE(0xFFFF3B30, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    // LAPIS(0xFF3366FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    // EMERALD(0xFF38E879, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    // NETHER_GOLD(0xFFFFC928, Blocks.NETHER_GOLD_ORE),
    // NETHER_QUARTZ(0xFFF2E2D0, Blocks.NETHER_QUARTZ_ORE),
    // ANCIENT_DEBRIS(0xFF8C5A4A, Blocks.ANCIENT_DEBRIS),
    DIAMOND(0xFF4DEBFF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);

    private final int color;
    private final Set<Block> blocks;

    OreType(int color, Block... blocks) {
        this.color = color;
        this.blocks = Set.copyOf(Arrays.asList(blocks));
    }

    public int color() {
        return color;
    }

    public Set<Block> blocks() {
        return blocks;
    }

    public boolean matches(BlockState state) {
        return blocks.contains(state.getBlock());
    }

    public static Optional<OreType> fromState(BlockState state) {
        return Arrays.stream(values())
                .filter(oreType -> oreType.matches(state))
                .findFirst();
    }
}
