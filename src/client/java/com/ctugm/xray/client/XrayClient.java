package com.ctugm.xray.client;

import com.ctugm.xray.XrayToggleState;
import com.ctugm.xray.client.render.OreRenderer;
import com.ctugm.xray.scan.OreScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.Set;

public class XrayClient implements ClientModInitializer {
	private static final KeyBinding.Category XRAY_CATEGORY = KeyBinding.Category.create(
			Identifier.of("xray", "general")
	);
	private static final XrayToggleState XRAY_STATE = new XrayToggleState();
	private static final OreScanner ORE_SCANNER = new OreScanner();

    @Override
    public void onInitializeClient() {
        OreRenderer.initialize();

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
			Optional<OreScanner.ScanResult> scanResult = ORE_SCANNER.scanIfNeeded(
					client.world,
					center,
					client.world.getBottomY(),
					client.world.getBottomY() + client.world.getHeight(),
					position -> {
						if (client.world.isOutOfHeightLimit(position)) {
							return false;
						}

						BlockState state = client.world.getBlockState(position);
						return state.isOf(Blocks.DIAMOND_ORE)
								|| state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE);
					}
			);
			if (scanResult.isEmpty()) {
				return;
			}

			OreScanner.ScanResult result = scanResult.get();
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

    public static boolean isXrayEnabled() {
        return XRAY_STATE.isEnabled();
    }

	private static void resetScanState() {
		ORE_SCANNER.reset();
	}
}
