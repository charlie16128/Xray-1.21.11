package com.ctugm.xray.scan;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

// 定義可掃描的礦物、顯示顏色與對應方塊。
public enum OreType {
    // 尚未啟用的礦物類型。每個類型可同時包含普通與深層版本。
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

    // ARGB 格式的礦物顯示顏色。
    private final int color;
    // 同一礦物類型所包含的所有方塊，例如普通鑽石礦與深層鑽石礦。
    private final Set<Block> blocks;

    // 建立一種礦物及其所有方塊版本。
    OreType(int color, Block... blocks) {
        this.color = color;
        this.blocks = Set.copyOf(Arrays.asList(blocks));
    }

    // 回傳 ARGB 顯示顏色。
    public int color() {
        return color;
    }

    // 回傳不可修改的方塊集合。
    public Set<Block> blocks() {
        return blocks;
    }

    // 判斷方塊是否屬於此礦物。
    public boolean matches(BlockState state) {
        return blocks.contains(state.getBlock());
    }

    // 從方塊狀態查找已啟用的礦物類型。
    public static Optional<OreType> fromState(BlockState state) {
        return Arrays.stream(values())
                .filter(oreType -> oreType.matches(state))
                .findFirst();
    }
}
