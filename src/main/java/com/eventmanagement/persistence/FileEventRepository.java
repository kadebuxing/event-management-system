package com.eventmanagement.persistence;

import com.eventmanagement.domain.Event;
import com.eventmanagement.domain.EventStatus;
import com.eventmanagement.domain.Registration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stores all application data in one UTF-8 text file.
 *
 * <p>Saving uses a temporary file in the same directory, forces it to disk and
 * then replaces the data file with an atomic move. A crash during a save can
 * therefore leave the previous complete data file in place but never a
 * half-written one. Concurrent writers are not coordinated (OPEN-12).</p>
 */
public final class FileEventRepository implements EventRepository {

    private static final String HEADER = "# event-management data v1";
    private static final String EVENT_PREFIX = "E";
    private static final String REGISTRATION_PREFIX = "R";
    private static final char SEPARATOR = '|';

    private final Path dataFile;

    public FileEventRepository(Path dataFile) {
        this.dataFile = dataFile.toAbsolutePath().normalize();
    }

    public Path dataFile() {
        return dataFile;
    }

    @Override
    public DataSnapshot load() throws IOException {
        if (!Files.exists(dataFile)) {
            return new DataSnapshot(List.of(), List.of());
        }

        List<Event> events = new ArrayList<>();
        List<Registration> registrations = new ArrayList<>();
        Set<String> eventIds = new HashSet<>();
        Set<String> registrationReferences = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                try {
                    if (EVENT_PREFIX.equals(parts[0])) {
                        Event event = parseEvent(parts);
                        if (!eventIds.add(event.id().toUpperCase(Locale.ROOT))) {
                            throw new IllegalArgumentException("duplicate event ID " + event.id());
                        }
                        events.add(event);
                    } else if (REGISTRATION_PREFIX.equals(parts[0])) {
                        Registration registration = parseRegistration(parts);
                        if (!registrationReferences.add(registration.reference().toUpperCase(Locale.ROOT))) {
                            throw new IllegalArgumentException(
                                    "duplicate registration reference " + registration.reference());
                        }
                        registrations.add(registration);
                    } else {
                        throw new IllegalArgumentException("unknown record type " + parts[0]);
                    }
                } catch (IllegalArgumentException | DateTimeParseException ex) {
                    throw new IOException(
                            "Invalid data file " + dataFile + " at line " + lineNumber + ": " + ex.getMessage(), ex);
                }
            }
        }

        Set<String> knownEventIds = events.stream()
                .map(event -> event.id().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (Registration registration : registrations) {
            if (!knownEventIds.contains(registration.eventId().toUpperCase(Locale.ROOT))) {
                throw new IOException("Invalid data file " + dataFile + ": registration "
                        + registration.reference() + " refers to unknown event " + registration.eventId());
            }
        }

        return new DataSnapshot(events, registrations);
    }

    private static Event parseEvent(String[] parts) {
        if (parts.length != 7) {
            throw new IllegalArgumentException("event record must have 7 fields");
        }
        String id = requireValue(parts[1], "event ID");
        String name = decode(parts[2]);
        LocalDateTime start = LocalDateTime.parse(parts[3]);
        String venue = decode(parts[4]);
        int capacity = Integer.parseInt(parts[5]);
        EventStatus status = EventStatus.valueOf(parts[6]);
        return new Event(id, name, start, venue, capacity, status);
    }

    private static Registration parseRegistration(String[] parts) {
        if (parts.length != 6) {
            throw new IllegalArgumentException("registration record must have 6 fields");
        }
        String reference = requireValue(parts[1], "registration reference");
        String eventId = requireValue(parts[2], "event ID");
        String attendeeName = decode(parts[3]);
        LocalDateTime registeredAt = LocalDateTime.parse(parts[4]);
        boolean attended = parseBoolean(parts[5]);
        return new Registration(reference, eventId, attendeeName, registeredAt, attended);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("attendance flag must be true or false");
    }

    private static String requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is missing");
        }
        return value;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void save(DataSnapshot snapshot) throws IOException {
        Path parent = dataFile.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, "events-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(HEADER);
                writer.newLine();
                for (Event event : snapshot.events()) {
                    writer.write(String.join(String.valueOf(SEPARATOR),
                            EVENT_PREFIX,
                            event.id(),
                            encode(event.name()),
                            event.start().toString(),
                            encode(event.venue()),
                            Integer.toString(event.capacity()),
                            event.status().name()));
                    writer.newLine();
                }
                for (Registration registration : snapshot.registrations()) {
                    writer.write(String.join(String.valueOf(SEPARATOR),
                            REGISTRATION_PREFIX,
                            registration.reference(),
                            registration.eventId(),
                            encode(registration.attendeeName()),
                            registration.registeredAt().toString(),
                            Boolean.toString(registration.attended())));
                    writer.newLine();
                }
            }

            // Make sure the bytes are on disk before the old file is replaced.
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            try {
                Files.move(temporaryFile, dataFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
