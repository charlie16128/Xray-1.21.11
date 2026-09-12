package com.ctugm.xray;

// 管理 Xray 開關狀態與切換訊息。
public final class XrayToggleState {
	// Java 的 boolean 預設為 false，因此遊戲啟動時 Xray 為停用狀態。
	private boolean enabled;

	// 回傳目前是否啟用 Xray。
	public boolean isEnabled() {
		return enabled;
	}

	// 切換狀態並回傳對應的聊天訊息。
	public String toggleMessage() {
		enabled = !enabled;
		return enabled ? "[Xray] Enable" : "[Xray] Disable";
	}
}
