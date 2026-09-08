package de.mkoehler.starwars.net;

import com.esotericsoftware.minlog.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Installs a {@link Log.Logger} that prefixes every KryoNet/minlog line with
 * an absolute wall-clock timestamp instead of minlog's default
 * time-since-process-start elapsed timer (e.g. {@code "00:06"}, {@code "02:22"}).
 * <p>
 * The client and dedicated server are separate processes, started at
 * different moments — their default relative timestamps can't be lined up
 * against each other when correlating a client-side event with a
 * server-side one, which came up directly while investigating an
 * intermittent screen-transition pause (CLAUDE.md). Every other part of the
 * line (level label, {@code [category]}, message, exception trace) is kept
 * in the same shape minlog's own default {@link Log.Logger} already
 * produces, so existing log-reading habits still apply.
 * <p>
 * Installed once by both {@link NetworkClient} and {@link NetworkServer}'s
 * constructors — idempotent, installing the same logger again is harmless —
 * so either one alone is enough to cover the whole process, including
 * KryoNet's own internal log lines (connect/disconnect, etc.), not just
 * this project's own {@link Log#info}/{@link Log#warn} calls.
 */
final class NetworkLogging {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile boolean installed;

    private NetworkLogging() {
    }

    static void useAbsoluteTimestamps() {
        if (installed) {
            return;
        }
        installed = true;
        Log.setLogger(new Log.Logger() {
            @Override
            public void log(int level, String category, String message, Throwable ex) {
                StringBuilder builder = new StringBuilder(256);
                builder.append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
                switch (level) {
                    case Log.LEVEL_ERROR -> builder.append(" ERROR:");
                    case Log.LEVEL_WARN -> builder.append("  WARN:");
                    case Log.LEVEL_INFO -> builder.append("  INFO:");
                    case Log.LEVEL_DEBUG -> builder.append(" DEBUG:");
                    case Log.LEVEL_TRACE -> builder.append(" TRACE:");
                    default -> {
                    }
                }
                if (category != null) {
                    builder.append(" [").append(category).append(']');
                }
                builder.append(' ').append(message);
                if (ex != null) {
                    StringWriter writer = new StringWriter(256);
                    ex.printStackTrace(new PrintWriter(writer));
                    builder.append('\n').append(writer.toString().trim());
                }
                print(builder.toString());
            }
        });
    }
}
