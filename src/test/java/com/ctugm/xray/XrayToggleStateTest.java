package com.ctugm.xray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// 測試 Xray 開關狀態與訊息。
class XrayToggleStateTest {
	// 從停用狀態開始，每次切換都應交替回傳 Enable 與 Disable。
	@Test
	void successiveTogglesAlternateFromDisabledState() {
		XrayToggleState state = new XrayToggleState();

		assertFalse(state.isEnabled());
		assertEquals("[Xray] Enable", state.toggleMessage());
		assertEquals("[Xray] Disable", state.toggleMessage());
		assertEquals("[Xray] Enable", state.toggleMessage());
	}
}
