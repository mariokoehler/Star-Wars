package de.mkoehler.starwars.lwjgl3.mcp;

import de.mkoehler.starwars.ConnectScreen;
import de.mkoehler.starwars.remote.RemoteControlQueue;
import de.mkoehler.starwars.remote.RemoteControlRegistry;
import de.mkoehler.starwars.remote.RemoteControllable;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Embedded, dev-only MCP server (design.md 3.13) letting Claude Code
 * remote-control the running client over stdio, in place of OS-level
 * keyboard/mouse/screenshot automation — started only when the client is
 * launched with {@code --mcp} ({@code Lwjgl3Launcher}), never in a normal
 * player-facing run.
 * <p>
 * Exposes tools as thin wrappers around whichever {@link RemoteControllable}
 * screen is currently registered in {@link RemoteControlRegistry} — see that
 * interface for the registration contract. Every tool marshals its actual
 * work onto the render thread via {@link RemoteControlQueue#submit}, since
 * only that thread may touch live Scene2D/libGDX state, and blocks (with a
 * generous timeout, {@link #TOOL_TIMEOUT_SECONDS}) waiting for the result —
 * mirroring how {@code ConnectScreen.attemptConnect()} already blocks the
 * render thread for a real login attempt, just blocking this class's own
 * caller thread instead.
 * <p>
 * <b>stdout hygiene:</b> the MCP stdio transport requires stdout to carry
 * <i>only</i> JSON-RPC messages — any stray {@code println} (libGDX's
 * default application logger, KryoNet's console logger, etc.) would corrupt
 * the stream. {@link #start()} captures the real stdout first and redirects
 * {@link System#out} to {@link System#err} for everything else, then hands
 * the captured real stdout stream to {@link StdioServerTransportProvider}
 * explicitly — the same technique the SDK's own test fixtures use.
 */
public final class McpBridge {

    /**
     * How long a tool call waits for the render thread to process it before
     * giving up — generous enough to cover {@code ConnectScreen}'s own
     * blocking connect attempt ({@code NetworkConstants.CONNECTION_TIMEOUT_MILLIS})
     * plus a real margin, not just a normal frame's worth of time.
     */
    private static final long TOOL_TIMEOUT_SECONDS = 15;

    private McpBridge() {
    }

    /**
     * Starts the embedded MCP server on the current thread's stdio streams.
     * Non-blocking — the SDK reads/processes incoming messages on its own
     * thread(s), so this returns immediately and the caller ({@code
     * Lwjgl3Launcher}) can go on to start the actual libGDX application.
     */
    public static void start() {
        PrintStream realStdOut = System.out;
        System.setOut(new PrintStream(System.err, true));

        McpJsonMapper mapper = McpJsonDefaults.getMapper();
        StdioServerTransportProvider transportProvider =
            new StdioServerTransportProvider(mapper, stripLeadingUtf8Bom(System.in), realStdOut);

        McpServer.sync(transportProvider)
            .serverInfo("starwars-client", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .toolCall(getActiveScreenTool(), McpBridge::handleGetActiveScreen)
            .toolCall(connectScreenLoginTool(), McpBridge::handleConnectScreenLogin)
            .build();
    }

    /**
     * Strips a single leading UTF-8 byte-order mark from {@code in}, if
     * present — some MCP clients/shells on Windows are known to prepend
     * one to a spawned process's stdin, and the SDK's own JSON parser
     * treats it as a stray character rather than silently skipping it,
     * which would otherwise break the very first message and, since MCP
     * errors here only ever go through SLF4J (never stdout, by design),
     * fail with no visible symptom at all.
     */
    private static InputStream stripLeadingUtf8Bom(InputStream in) {
        try {
            PushbackInputStream pushback = new PushbackInputStream(in, 3);
            byte[] maybeBom = new byte[3];
            int read = pushback.read(maybeBom, 0, 3);
            if (read == 3 && (maybeBom[0] & 0xFF) == 0xEF && (maybeBom[1] & 0xFF) == 0xBB
                    && (maybeBom[2] & 0xFF) == 0xBF) {
                return pushback; // BOM consumed, not pushed back.
            }
            if (read > 0) {
                pushback.unread(maybeBom, 0, read);
            }
            return pushback;
        } catch (IOException e) {
            return in;
        }
    }

    private static McpSchema.Tool getActiveScreenTool() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of());
        return McpSchema.Tool.builder("get_active_screen", schema)
            .description("Returns which screen is currently active in the running StarWars client "
                + "(e.g. \"CONNECT\", or \"NONE\" if no remote-controllable screen is active right now) "
                + "and that screen's current state. Only the Connect screen exposes state in this first "
                + "version - check the \"screen\" field before calling a screen-specific tool.")
            .build();
    }

    private static McpSchema.Tool connectScreenLoginTool() {
        Map<String, Object> stringProperty = Map.of("type", "string");
        Map<String, Object> properties = Map.of(
            "host", stringProperty,
            "displayName", stringProperty,
            "login", stringProperty,
            "password", stringProperty);
        Map<String, Object> schema = Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("host", "displayName", "login", "password"));
        return McpSchema.Tool.builder("connect_screen_login", schema)
            .description("Fills in the Connect screen's Server/Display Name/Login/Password fields and "
                + "presses Connect, exactly as a real user would. Only works while get_active_screen "
                + "reports \"CONNECT\" as the active screen. On success the client moves on to Ship "
                + "Selection and the returned \"loggedIn\" field is true; on failure it stays on the "
                + "Connect screen and the returned state includes the shown error message.")
            .build();
    }

    private static McpSchema.CallToolResult handleGetActiveScreen(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        return runOnRenderThread(() -> {
            RemoteControllable active = RemoteControlRegistry.getActive();
            if (active == null) {
                return Map.of("screen", "NONE");
            }
            Map<String, Object> state = new HashMap<>(active.describeState());
            state.put("screen", active.screenName());
            return state;
        });
    }

    private static McpSchema.CallToolResult handleConnectScreenLogin(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments();
        String host = (String) arguments.get("host");
        String displayName = (String) arguments.get("displayName");
        String login = (String) arguments.get("login");
        String password = (String) arguments.get("password");

        return runOnRenderThread(() -> {
            RemoteControllable active = RemoteControlRegistry.getActive();
            if (!(active instanceof ConnectScreen connectScreen)) {
                String currentScreen = active == null ? "NONE" : active.screenName();
                return Map.of("loggedIn", false,
                    "error", "Connect screen is not active (current screen: " + currentScreen + ").");
            }

            connectScreen.remoteLogin(host, displayName, login, password);

            // remoteLogin() calls the same attemptConnect() a real Connect press does - on success
            // that disposes this screen (unregistering it) and switches to Ship Selection, so
            // whether the registry still points at this exact instance tells success from failure
            // without needing a second signal threaded through attemptConnect() itself.
            if (RemoteControlRegistry.getActive() == connectScreen) {
                Map<String, Object> state = new HashMap<>(connectScreen.describeState());
                state.put("loggedIn", false);
                return state;
            }
            return Map.of("loggedIn", true);
        });
    }

    /**
     * Submits {@code action} to {@link RemoteControlQueue}, blocks for its
     * result, and wraps it as a successful (JSON text) or error
     * {@link McpSchema.CallToolResult} — the one place every tool handler
     * funnels through, so timeout/serialization/interruption handling isn't
     * duplicated per tool.
     */
    private static McpSchema.CallToolResult runOnRenderThread(java.util.concurrent.Callable<Map<String, Object>> action) {
        try {
            Map<String, Object> result = RemoteControlQueue.submit(action).get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String json = McpJsonDefaults.getMapper().writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                .content(List.of(McpSchema.TextContent.builder(json).build()))
                .isError(false)
                .build();
        } catch (IOException e) {
            return errorResult("Failed to serialize result: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("Interrupted while waiting for the render thread.");
        } catch (Exception e) {
            return errorResult("Tool call failed: " + e.getMessage());
        }
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
            .content(List.of(McpSchema.TextContent.builder(message).build()))
            .isError(true)
            .build();
    }
}
