# Xray Toggle Key Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register a configurable `X` key that alternates local chat feedback between `[Xray] Enable` and `[Xray] Disable`.

**Architecture:** Keep the toggle state in a small Minecraft-independent class so its transition sequence can be unit tested. The existing Fabric client initializer owns the key binding, consumes queued presses on the end-client-tick event, toggles only when a player exists, and sends the resulting literal message to the local chat HUD.

**Tech Stack:** Java 21, Minecraft 1.21.11 with Yarn mappings, Fabric Loader/API, Gradle, JUnit Jupiter 5.11.4.

---

The workspace has no `.git` repository, so commit steps are intentionally omitted.

### Task 1: Add a failing toggle-state test

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/com/ctugm/xray/XrayToggleStateTest.java`

- [ ] **Step 1: Configure JUnit Jupiter**

Add the dependency inside the existing `dependencies` block:

```groovy
testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

Add this test-task configuration after the `dependencies` block:

```groovy
tasks.named('test') {
	useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/ctugm/xray/XrayToggleStateTest.java`:

```java
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
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests com.ctugm.xray.XrayToggleStateTest
```

Expected: `compileTestJava` fails because `XrayToggleState` does not exist. This confirms the test is exercising the missing behavior.

### Task 2: Implement the toggle state

**Files:**
- Create: `src/main/java/com/ctugm/xray/XrayToggleState.java`
- Test: `src/test/java/com/ctugm/xray/XrayToggleStateTest.java`

- [ ] **Step 1: Add the minimal state implementation**

Create `src/main/java/com/ctugm/xray/XrayToggleState.java`:

```java
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
```

- [ ] **Step 2: Run the focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests com.ctugm.xray.XrayToggleStateTest
```

Expected: the focused test passes with `BUILD SUCCESSFUL`.

### Task 3: Register the key and send alternating chat feedback

**Files:**
- Modify: `src/client/java/com/ctugm/xray/client/XrayClient.java`
- Create: `src/client/resources/assets/xray/lang/en_us.json`
- Test: `src/test/java/com/ctugm/xray/XrayToggleStateTest.java`

- [ ] **Step 1: Implement the Fabric client integration**

Replace `XrayClient.java` with:

```java
package com.ctugm.xray.client;

import com.ctugm.xray.XrayToggleState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class XrayClient implements ClientModInitializer {
	private static final KeyBinding.Category XRAY_CATEGORY = KeyBinding.Category.create(
			Identifier.of("xray", "general")
	);
	private static final XrayToggleState XRAY_STATE = new XrayToggleState();

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
			}
		});
	}
}
```

- [ ] **Step 2: Add Controls-screen translations**

Create `src/client/resources/assets/xray/lang/en_us.json`:

```json
{
  "key.category.xray.general": "Xray",
  "key.xray.toggle": "Toggle Xray"
}
```

- [ ] **Step 3: Compile the client integration**

Run:

```powershell
.\gradlew.bat compileClientJava
```

Expected: `BUILD SUCCESSFUL`, proving the key-binding, event, Yarn mapping, and chat APIs are valid for the pinned dependency versions.

- [ ] **Step 4: Run the complete automated suite**

Run:

```powershell
.\gradlew.bat test
```

Expected: all tests pass with `BUILD SUCCESSFUL` and no Java compilation warnings.

- [ ] **Step 5: Perform the gameplay smoke test when a client is available**

Launch the development client, join a world, and verify:

1. `Options > Controls > Key Binds` contains an `Xray` category with `Toggle Xray` bound to `X`.
2. The first `X` press adds `[Xray] Enable` to chat.
3. The second `X` press adds `[Xray] Disable` to chat.
4. Further presses continue alternating exactly once per consumed press.
