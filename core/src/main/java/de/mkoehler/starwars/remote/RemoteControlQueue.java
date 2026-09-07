package de.mkoehler.starwars.remote;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Cross-thread bridge between the embedded MCP server (design.md 3.13,
 * running on its own thread(s), started from {@code Lwjgl3Launcher}) and the
 * render thread every live game/Scene2D object must actually be touched
 * from — the exact same "enqueue a Runnable, drain it at the start of the
 * next frame" pattern this codebase already uses for KryoNet's network
 * callbacks (see {@code Client}/{@code GameNetworkServer}'s class Javadocs),
 * just for a second, unrelated external thread instead of the network one.
 * <p>
 * Unlike the fire-and-forget network queues, a caller here (an MCP tool
 * handler) needs the *result* of its action, not just to have it eventually
 * run — {@link #submit} returns a {@link CompletableFuture} the calling
 * thread can block on with a timeout.
 */
public final class RemoteControlQueue {

    private static final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();

    private RemoteControlQueue() {
    }

    /**
     * Schedules {@code action} to run on the render thread the next time
     * {@link #drain()} is called, and returns a future that completes with
     * its result (or completes exceptionally if it threw).
     *
     * @param action the action to run on the render thread; may safely
     *               touch live Scene2D/libGDX state
     * @param <T>    the action's result type
     * @return a future completed once {@code action} has run
     */
    public static <T> CompletableFuture<T> submit(Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pending.add(() -> {
            try {
                future.complete(action.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Runs every action queued by {@link #submit} since the last call, in
     * order. Must only be called from the render thread — call this once
     * per frame (see {@code StarWarsGame#render()}), before delegating to
     * the current screen, same as this codebase's other per-frame queue
     * drains.
     */
    public static void drain() {
        Runnable action;
        while ((action = pending.poll()) != null) {
            action.run();
        }
    }
}
