package com.ctugm.xray.client;

import com.ctugm.xray.XrayToggleState;
import com.ctugm.xray.client.render.OreRenderer;
import com.ctugm.xray.scan.OreScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.Set;

// 註冊 Xray 按鍵、掃描流程與 Renderer。
public class XrayClient implements ClientModInitializer {
	// 在 Minecraft 控制設定中建立 Xray 專用按鍵分類。
	private static final KeyBinding.Category XRAY_CATEGORY = KeyBinding.Category.create(
			Identifier.of("xray", "general")
	);
	// 全域共用的開關狀態與礦物掃描器。
	private static final XrayToggleState XRAY_STATE = new XrayToggleState();
	private static final OreScanner ORE_SCANNER = new OreScanner();

    // 初始化 Client 功能。
    @Override
    public void onInitializeClient() {
        OreRenderer.initialize();

        // 預設使用 X 鍵切換 Xray。
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.xray.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				XRAY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 使用 while 可處理同一 tick 內累積的多次按鍵事件。
			while (toggleKey.wasPressed()) {
				if (client.player == null) {
					continue;
				}

				client.player.sendMessage(Text.literal(XRAY_STATE.toggleMessage()), false);
				if (!XRAY_STATE.isEnabled()) {
					resetScanState();
				}
			}

			// 停用狀態不執行任何世界查詢或掃描。
			if (!XRAY_STATE.isEnabled()) {
				return;
			}

			// 尚未進入世界或玩家不存在時，丟棄上一個世界的快取。
			if (client.player == null || client.world == null) {
				resetScanState();
				return;
			}

			BlockPos center = client.player.getBlockPos().toImmutable();
			// OreScanner 會自行判斷玩家是否仍在同一 Chunk，並只掃描新增 Chunk。
			Optional<OreScanner.ScanResult> scanResult = ORE_SCANNER.scanWorldIfNeeded(
					client.world,
					center,
					state -> state.isOf(Blocks.DIAMOND_ORE)
							|| state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
			);
			if (scanResult.isEmpty()) {
				return;
			}

			OreScanner.ScanResult result = scanResult.get();
			// 每次真正掃描時都顯示實際檢查數，不包含直接沿用的 Chunk 快取。
			client.player.sendMessage(
					Text.literal("[Xray] Scanned blocks: " + result.scannedBlockCount()),
					false
			);

			// 礦物總數沒變時不重複洗版。
			if (result.countChanged()) {
				client.player.sendMessage(
						Text.literal("[Xray] Diamond ores: " + result.count()),
						false
				);
			}
		});
	}

    // 提供 Renderer 目前找到的礦物座標。
    public static Set<BlockPos> getDetectedOrePositions() {
        return ORE_SCANNER.getOrePositions();
    }

    // 提供 Renderer 目前的開關狀態。
    public static boolean isXrayEnabled() {
        return XRAY_STATE.isEnabled();
    }

	// 統一清除掃描器的所有座標與 Chunk 快取。
	private static void resetScanState() {
		ORE_SCANNER.reset();
	}
}
