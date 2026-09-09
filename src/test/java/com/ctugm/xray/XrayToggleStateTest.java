package com.ctugm.xray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class XrayToggleStateTest {
	@Test
	void successiveTogglesAlternateFromDisabledState() {
		XrayToggleState state = new XrayToggleState();

		assertFalse(state.isEnabled());
		assertEquals("[Xray] Enable", state.toggleMessage());
		assertEquals("[Xray] Disable", state.toggleMessage());
		assertEquals("[Xray] Enable", state.toggleMessage());
	}
}
