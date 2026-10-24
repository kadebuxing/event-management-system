package com.eventmanagement.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record Event(
        String id,
        String name,
        LocalDateTime start,
        String venue,
        int capacity,
        EventStatus status
) {
    public Event {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(status, "status");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    public boolean hasStarted(LocalDateTime now) {
        return !start.isAfter(now);
    }

    public Event withDetails(String newName, LocalDateTime newStart, String newVenue) {
        return new Event(id, newName, newStart, newVenue, capacity, status);
    }

    public Event withCapacity(int newCapacity) {
        return new Event(id, name, start, venue, newCapacity, status);
    }

    public Event withStatus(EventStatus newStatus) {
        return new Event(id, name, start, venue, capacity, newStatus);
    }
}
