package com.eventmanagement.service;

import com.eventmanagement.domain.Event;
import com.eventmanagement.domain.EventStatus;
import com.eventmanagement.domain.Registration;
import com.eventmanagement.persistence.DataSnapshot;
import com.eventmanagement.persistence.EventRepository;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventService {

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("EVT-(\\d+)");
    private static final String REGISTRATION_PREFIX = "REG-";
    private static final char[] REFERENCE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int REFERENCE_LENGTH = 6;
    private static final int MAX_REFERENCE_ATTEMPTS = 1_000;

    private static final Comparator<Event> EVENT_ORDER =
            Comparator.comparing(Event::start).thenComparing(Event::id);
    private static final Comparator<Registration> REGISTRATION_ORDER =
            Comparator.comparing(Registration::registeredAt).thenComparing(Registration::reference);

    private final EventRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private List<Event> events;
    private List<Registration> registrations;

    public EventService(EventRepository repository, Clock clock) throws IOException {
        this.repository = repository;
        this.clock = clock;
        DataSnapshot snapshot = repository.load();
        this.events = new ArrayList<>(snapshot.events());
        this.registrations = new ArrayList<>(snapshot.registrations());
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public List<Event> events() {
        return events.stream().sorted(EVENT_ORDER).toList();
    }

    public Optional<Event> findEvent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim();
        return events.stream()
                .filter(event -> event.id().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public List<Registration> registrationsFor(String eventId) {
        return registrations.stream()
                .filter(registration -> registration.eventId().equalsIgnoreCase(eventId))
                .sorted(REGISTRATION_ORDER)
                .toList();
    }

    public Optional<Registration> findRegistration(String eventId, String reference) {
        if (eventId == null || reference == null) {
            return Optional.empty();
        }
        String normalizedReference = reference.trim();
        return registrations.stream()
                .filter(registration -> registration.eventId().equalsIgnoreCase(eventId.trim()))
                .filter(registration -> registration.reference().equalsIgnoreCase(normalizedReference))
                .findFirst();
    }

    public int registeredCount(Event event) {
        return (int) registrations.stream()
                .filter(registration -> registration.eventId().equalsIgnoreCase(event.id()))
                .count();
    }

    public int attendedCount(Event event) {
        return (int) registrations.stream()
                .filter(registration -> registration.eventId().equalsIgnoreCase(event.id()))
                .filter(Registration::attended)
                .count();
    }

    public int availablePlaces(Event event) {
        return Math.max(0, event.capacity() - registeredCount(event));
    }

    public boolean isPast(Event event) {
        return event.hasStarted(now());
    }

    public Event createEvent(String name, LocalDateTime start, String venue, int capacity) {
        String cleanName = requireText(name, "Event name");
        String cleanVenue = requireText(venue, "Venue");
        requireFuture(start);
        requirePositiveCapacity(capacity);

        Event event = new Event(nextEventId(), cleanName, start, cleanVenue, capacity, EventStatus.ACTIVE);
        List<Event> updatedEvents = new ArrayList<>(events);
        updatedEvents.add(event);
        commit(updatedEvents, registrations);
        return event;
    }

    public Event editEvent(String id, String name, LocalDateTime start, String venue) {
        Event existing = requireEvent(id);
        if (isPast(existing)) {
            throw new EventServiceException("This event has passed and can no longer be edited.");
        }
        String cleanName = requireText(name, "Event name");
        String cleanVenue = requireText(venue, "Venue");
        requireFuture(start);

        Event updated = existing.withDetails(cleanName, start, cleanVenue);
        commit(replaceEvent(updated), registrations);
        return updated;
    }

    public Event updateCapacity(String id, int capacity) {
        Event existing = requireEvent(id);
        requirePositiveCapacity(capacity);
        int registered = registeredCount(existing);
        if (capacity < registered) {
            throw new EventServiceException("Invalid capacity: it cannot be lower than the "
                    + registered + " attendee(s) already registered.");
        }

        Event updated = existing.withCapacity(capacity);
        commit(replaceEvent(updated), registrations);
        return updated;
    }

    public Event cancelEvent(String id) {
        Event existing = requireEvent(id);
        if (existing.isCancelled()) {
            throw new EventServiceException("Event " + existing.id() + " is already cancelled.");
        }
        Event updated = existing.withStatus(EventStatus.CANCELLED);
        commit(replaceEvent(updated), registrations);
        return updated;
    }

    public Event restoreEvent(String id) {
        Event existing = requireEvent(id);
        if (!existing.isCancelled()) {
            throw new EventServiceException("Event " + existing.id() + " is not cancelled.");
        }
        Event updated = existing.withStatus(EventStatus.ACTIVE);
        commit(replaceEvent(updated), registrations);
        return updated;
    }

    public Registration registerAttendee(String eventId, String attendeeName) {
        Event event = requireEvent(eventId);
        if (event.isCancelled()) {
            throw new EventServiceException("This event has been cancelled and cannot accept registrations.");
        }
        if (isPast(event)) {
            throw new EventServiceException("This event has already taken place and cannot accept registrations.");
        }

        int registered = registeredCount(event);
        if (registered >= event.capacity()) {
            throw new EventServiceException("This event has reached its capacity of "
                    + event.capacity() + ". Further registrations are blocked.");
        }

        String cleanName = requireText(attendeeName, "Attendee name");
        Registration registration = new Registration(
                nextRegistrationReference(), event.id(), cleanName, now(), false);
        List<Registration> updatedRegistrations = new ArrayList<>(registrations);
        updatedRegistrations.add(registration);
        commit(events, updatedRegistrations);
        return registration;
    }

    public Registration cancelRegistration(String eventId, String reference) {
        Event event = requireEvent(eventId);
        Registration registration = requireRegistration(event, reference);
        List<Registration> updatedRegistrations = new ArrayList<>(registrations);
        updatedRegistrations.removeIf(existing ->
                existing.reference().equalsIgnoreCase(registration.reference()));
        commit(events, updatedRegistrations);
        return registration;
    }

    public Registration markAttended(String eventId, String reference) {
        Event event = requireEvent(eventId);
        Registration registration = requireRegistration(event, reference);
        if (registration.attended()) {
            return registration;
        }
        Registration updated = registration.markAttended();
        List<Registration> updatedRegistrations = registrations.stream()
                .map(existing -> existing.reference().equalsIgnoreCase(updated.reference())
                        ? updated
                        : existing)
                .toList();
        commit(events, updatedRegistrations);
        return updated;
    }

    private Registration requireRegistration(Event event, String reference) {
        String normalized = reference == null ? "" : reference.trim();
        return registrations.stream()
                .filter(registration -> registration.eventId().equalsIgnoreCase(event.id()))
                .filter(registration -> registration.reference().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new EventServiceException(
                        "No registration found with reference " + normalized + " for this event."));
    }

    private Event requireEvent(String id) {
        return findEvent(id).orElseThrow(() -> new EventServiceException(
                "No event found with ID " + (id == null ? "" : id.trim()) + "."));
    }

    private List<Event> replaceEvent(Event updated) {
        return events.stream()
                .map(existing -> existing.id().equalsIgnoreCase(updated.id()) ? updated : existing)
                .toList();
    }

    private void commit(List<Event> newEvents, List<Registration> newRegistrations) {
        DataSnapshot snapshot = new DataSnapshot(newEvents, newRegistrations);
        try {
            repository.save(snapshot);
        } catch (IOException ex) {
            throw new EventServiceException(
                    "Could not save data: " + ex.getMessage() + ". Your change was not applied.", ex);
        }
        this.events = new ArrayList<>(snapshot.events());
        this.registrations = new ArrayList<>(snapshot.registrations());
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new EventServiceException(label + " cannot be empty.");
        }
        return value.trim();
    }

    private void requireFuture(LocalDateTime start) {
        if (start == null) {
            throw new EventServiceException("Event date & time is required.");
        }
        if (!start.isAfter(now())) {
            throw new EventServiceException("Event date & time must be in the future.");
        }
    }

    private static void requirePositiveCapacity(int capacity) {
        if (capacity <= 0) {
            throw new EventServiceException("Capacity must be a positive whole number.");
        }
    }

    private String nextEventId() {
        int highest = 0;
        for (Event event : events) {
            Matcher matcher = EVENT_ID_PATTERN.matcher(event.id());
            if (matcher.matches()) {
                try {
                    highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Keep scanning; this is not an ID generated by the application.
                }
            }
        }
        return String.format(Locale.ROOT, "EVT-%04d", highest + 1);
    }

    private String nextRegistrationReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            StringBuilder candidate = new StringBuilder(REGISTRATION_PREFIX);
            for (int index = 0; index < REFERENCE_LENGTH; index++) {
                candidate.append(REFERENCE_ALPHABET[random.nextInt(REFERENCE_ALPHABET.length)]);
            }
            String reference = candidate.toString();
            boolean alreadyUsed = registrations.stream()
                    .anyMatch(registration -> registration.reference().equalsIgnoreCase(reference));
            if (!alreadyUsed) {
                return reference;
            }
        }
        throw new EventServiceException(
                "Could not generate a unique registration reference. Please try again.");
    }
}
