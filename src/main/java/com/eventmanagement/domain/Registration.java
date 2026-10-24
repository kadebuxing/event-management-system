package com.eventmanagement.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record Registration(
        String reference,
        String eventId,
        String attendeeName,
        LocalDateTime registeredAt,
        boolean attended
) {
    public Registration {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(attendeeName, "attendeeName");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }

    public Registration markAttended() {
        return new Registration(reference, eventId, attendeeName, registeredAt, true);
    }
}
