package com.eventmanagement.persistence;

import com.eventmanagement.domain.Event;
import com.eventmanagement.domain.Registration;

import java.util.List;

public record DataSnapshot(List<Event> events, List<Registration> registrations) {
    public DataSnapshot {
        events = List.copyOf(events);
        registrations = List.copyOf(registrations);
    }
}
