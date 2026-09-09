# Xray Toggle Key Design

## Goal

Register a configurable client-side key mapping that defaults to `X`. Each accepted press toggles an in-memory Xray state and writes the new state to the in-game chat as `[Xray] Enable` or `[Xray] Disable`.

This change only establishes the input and status feedback. It does not implement Xray rendering.

## Architecture

Use Fabric API's standard client key-mapping pattern:

- Register the mapping from the existing `ClientModInitializer` with `KeyBindingHelper`.
- Put the mapping in an Xray controls category and provide English translation entries for both the category and action.
- Consume key presses from `ClientTickEvents.END_CLIENT_TICK`.
- Keep the toggle state client-side and initialize it as disabled.

This follows Fabric's documented approach and keeps the implementation compatible with Minecraft's Controls screen and key-conflict handling.

## Behavior and Data Flow

1. Minecraft initializes the Xray client entry point.
2. The mod registers an `X` key mapping.
3. At the end of each client tick, the listener consumes queued presses.
4. If no player is present, queued presses are consumed and discarded; the state remains unchanged.
5. Otherwise, the state is inverted once per consumed press.
6. The local player's chat receives exactly one corresponding message:
   - Disabled to enabled: `[Xray] Enable`
   - Enabled to disabled: `[Xray] Disable`

The message is local client feedback and is not sent to the multiplayer server as player chat.

## Components

- `XrayClient`: registers the key mapping and tick callback, then sends the local chat message.
- A small state component: owns the disabled/enabled value and produces the next status. Keeping this logic independent from Minecraft APIs makes the alternating sequence directly testable.
- `assets/xray/lang/en_us.json`: supplies readable labels in the Controls screen.

## Error Handling

- A missing player is a normal condition at menus or during world transitions; queued presses are discarded without toggling or displaying a message.
- Multiple queued presses made while a player is present are consumed individually so no discrete in-game press is silently lost.

## Testing and Verification

- Add a unit test proving the state starts disabled and successive toggles produce `Enable`, `Disable`, then `Enable`.
- Run the focused unit test first and confirm it fails before implementation.
- Implement the smallest production change that passes the test.
- Run the complete Gradle test suite and compile the client source set.
- If a game client is available, manually confirm the binding appears in Controls and that pressing `X` alternates the two chat messages.
