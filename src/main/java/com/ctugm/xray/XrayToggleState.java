package com.ctugm.xray;

public final class XrayToggleState {
	private boolean enabled;

	public boolean isEnabled() {
		return enabled;
	}

	public String toggleMessage() {
		enabled = !enabled;
		return enabled ? "[Xray] Enable" : "[Xray] Disable";
	}
}
