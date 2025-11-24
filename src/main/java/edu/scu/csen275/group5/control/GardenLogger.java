package edu.scu.csen275.group5.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Small logging helper that appends garden events to log.txt and keeps
 * a short in-memory tail for the control room UI.
 */
public class GardenLogger {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logPath;
    private final int tailLimit;
    private final Deque<String> tailBuffer;
    private final List<Consumer<String>> listeners;

    public GardenLogger(Path logPath) {
        this(logPath, 250);
    }

    public GardenLogger(Path logPath, int tailLimit) {
        this.logPath = Objects.requireNonNull(logPath, "logPath");
        this.tailLimit = tailLimit;
        this.tailBuffer = new ArrayDeque<>(tailLimit);
        this.listeners = new CopyOnWriteArrayList<>();
        ensureFile();
        preloadTail();
    }

    public Path getLogPath() {
        return logPath;
    }

    public synchronized void log(String tag, String message) {
        String line = formatLine(tag, message);
        try {
            Files.writeString(logPath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Unable to write garden log: " + e.getMessage());
        }
        remember(line);
        broadcast(line);
    }

    public List<String> recentEntries() {
        synchronized (tailBuffer) {
            return new ArrayList<>(tailBuffer);
        }
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    private void broadcast(String entry) {
        for (Consumer<String> listener : listeners) {
            listener.accept(entry);
        }
    }

    private void ensureFile() {
        try {
            Path parent = logPath.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(logPath)) {
                Files.createFile(logPath);
                Files.writeString(logPath, "# Computerized Garden event log" + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create log file at " + logPath, e);
        }
    }

    private void preloadTail() {
        if (Files.notExists(logPath)) {
            return;
        }
        try (Stream<String> stream = Files.lines(logPath)) {
            stream.forEach(this::remember);
        } catch (IOException e) {
            System.err.println("Unable to read garden log: " + e.getMessage());
        }
    }

    private void remember(String entry) {
        synchronized (tailBuffer) {
            if (tailBuffer.size() == tailLimit) {
                tailBuffer.removeFirst();
            }
            tailBuffer.addLast(entry);
        }
    }

    private String formatLine(String tag, String message) {
        String timestamp = LocalDateTime.now().format(TS);
        return String.format("%s [%s] %s", timestamp, tag, message);
    }
}
