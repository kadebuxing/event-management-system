package com.eventmanagement.cli;

import com.eventmanagement.domain.Event;
import com.eventmanagement.domain.Registration;
import com.eventmanagement.service.EventService;
import com.eventmanagement.service.EventServiceException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ConsoleApp {

    private static final DateTimeFormatter INPUT_DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> EVENT_TABLE_HEADERS =
            List.of("ID", "Name", "Date & time", "Venue", "Capacity", "Registered", "Status");

    private final EventService service;
    private final ConsoleIO io;
    private final String dataFile;

    public ConsoleApp(EventService service, ConsoleIO io, String dataFile) {
        this.service = service;
        this.io = io;
        this.dataFile = dataFile;
    }

    public void run() {
        io.println("==========================================");
        io.println("        Event Management System");
        io.println("==========================================");
        io.println("Data file: " + dataFile);
        io.println("Manage events, registrations and attendance.");
        try {
            while (true) {
                printMenu();
                String option = io.readLine("Choose an option: ").trim();
                io.println();
                try {
                    switch (option) {
                        case "1" -> createEventFlow();
                        case "2" -> listEventsFlow();
                        case "3" -> viewEventDetailsFlow();
                        case "4" -> editEventFlow();
                        case "5" -> cancelOrRestoreEventFlow();
                        case "6" -> updateCapacityFlow();
                        case "7" -> registerAttendeeFlow();
                        case "8" -> cancelRegistrationFlow();
                        case "9" -> markAttendanceFlow();
                        case "0" -> {
                            io.println("Goodbye.");
                            return;
                        }
                        default -> io.println("Invalid option. Enter a number from 0 to 9.");
                    }
                } catch (ConsoleIO.EndOfInputException ex) {
                    throw ex;
                } catch (EventServiceException ex) {
                    io.println("Error: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    io.println("Unexpected error: " + ex.getMessage());
                }
            }
        } catch (ConsoleIO.EndOfInputException ex) {
            io.println();
            io.println("Input closed. Goodbye.");
        }
    }

    private void printMenu() {
        io.println();
        io.println("Main menu");
        io.println("1. Create event");
        io.println("2. List / search events");
        io.println("3. View event details");
        io.println("4. Edit event");
        io.println("5. Cancel / restore event");
        io.println("6. Update event capacity");
        io.println("7. Register attendee");
        io.println("8. Cancel registration");
        io.println("9. Mark attendance");
        io.println("0. Exit");
    }

    private void createEventFlow() {
        io.println("--- Create event ---");
        while (true) {
            String name = io.readLine("Event name: ");
            String dateRaw = io.readLine("Date & time (yyyy-MM-dd HH:mm): ");
            String venue = io.readLine("Venue: ");
            String capacityRaw = io.readLine("Capacity: ");

            List<String> errors = new ArrayList<>();
            if (name.isBlank()) {
                errors.add("Event name cannot be empty.");
            }
            LocalDateTime start = parseDateTime(dateRaw);
            if (start == null) {
                errors.add("Date & time must use the format yyyy-MM-dd HH:mm and be a real date.");
            } else if (!start.isAfter(service.now())) {
                errors.add("Date & time must be in the future.");
            }
            if (venue.isBlank()) {
                errors.add("Venue cannot be empty.");
            }
            Integer capacity = parsePositiveCapacity(capacityRaw, errors);

            if (!errors.isEmpty()) {
                printErrors("Event not saved:", errors);
                io.println("Please enter the event details again.");
                io.println();
                continue;
            }

            try {
                Event event = service.createEvent(name, start, venue, capacity);
                io.println("Event saved with a unique ID.");
                printEventSummary(event);
                return;
            } catch (EventServiceException ex) {
                printErrors("Event not saved:", List.of(ex.getMessage()));
                io.println("Please enter the event details again.");
                io.println();
            }
        }
    }

    private void listEventsFlow() {
        io.println("--- Events ---");
        List<Event> events = service.events();
        if (events.isEmpty()) {
            io.println("No events");
            return;
        }
        printGroupedEvents(events);
        io.println();
        io.println("1. Filter events");
        io.println("0. Back");
        while (true) {
            String option = io.readLine("Choose an option: ").trim();
            if (option.equals("0")) {
                return;
            }
            if (option.equals("1")) {
                filterEventsFlow();
                return;
            }
            io.println("Invalid option. Enter 1 to filter or 0 to go back.");
        }
    }

    private void filterEventsFlow() {
        io.println("Filter events by:");
        io.println("1. Name");
        io.println("2. ID");
        io.println("3. Date");
        io.println("4. Venue");
        io.println("5. Any field");
        io.println("0. Cancel");
        int field = readIntChoice("Choose a field: ", 0, 5);
        if (field == 0) {
            return;
        }
        String query = io.readLine("Filter text: ").trim();
        if (query.isEmpty()) {
            io.println("Filter text cannot be empty.");
            return;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<Event> matches = service.events().stream()
                .filter(event -> matchesFilter(event, field, needle))
                .toList();
        if (matches.isEmpty()) {
            io.println("No events match your filter.");
            return;
        }
        io.println();
        io.println("Filter results:");
        printGroupedEvents(matches);
    }

    private void viewEventDetailsFlow() {
        chooseEvent("View event details", service.events()).ifPresent(this::printEventDetails);
    }

    private void editEventFlow() {
        Optional<Event> chosen = chooseEvent("Edit event", service.events());
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        if (service.isPast(event)) {
            io.println("This event has passed and can no longer be edited.");
            return;
        }

        while (true) {
            io.println();
            io.println("Press Enter to keep the current value.");
            String nameRaw = io.readLine("New name [" + event.name() + "]: ");
            String dateRaw = io.readLine("New date & time [" + formatDate(event.start()) + "]: ");
            String venueRaw = io.readLine("New venue [" + event.venue() + "]: ");

            String name = nameRaw.isBlank() ? event.name() : nameRaw.trim();
            String venue = venueRaw.isBlank() ? event.venue() : venueRaw.trim();
            LocalDateTime start = dateRaw.isBlank() ? event.start() : parseDateTime(dateRaw);

            List<String> errors = new ArrayList<>();
            if (start == null) {
                errors.add("Date & time must use the format yyyy-MM-dd HH:mm and be a real date.");
            } else if (!start.isAfter(service.now())) {
                errors.add("Date & time must be in the future.");
            }
            if (!errors.isEmpty()) {
                printErrors("Event not updated:", errors);
                continue;
            }

            try {
                Event updated = service.editEvent(event.id(), name, start, venue);
                io.println("Event updated.");
                printEventSummary(updated);
                return;
            } catch (EventServiceException ex) {
                printErrors("Event not updated:", List.of(ex.getMessage()));
                return;
            }
        }
    }

    private void cancelOrRestoreEventFlow() {
        Optional<Event> chosen = chooseEvent("Cancel or restore event", service.events());
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        if (event.isCancelled()) {
            io.println("Event " + event.id() + " is currently cancelled.");
            if (!io.confirm("Restore this event? (y/N): ")) {
                io.println("No changes made.");
                return;
            }
            Event restored = service.restoreEvent(event.id());
            io.println("Event restored. It can accept registrations again, subject to capacity.");
            printEventSummary(restored);
        } else {
            io.println("Event " + event.id() + " is currently active.");
            io.println("Existing registrations are kept if the event is cancelled.");
            if (!io.confirm("Cancel this event? (y/N): ")) {
                io.println("No changes made.");
                return;
            }
            Event cancelled = service.cancelEvent(event.id());
            io.println("Event cancelled. It is listed in the Cancelled section and cannot accept new registrations.");
            printEventSummary(cancelled);
        }
    }

    private void updateCapacityFlow() {
        Optional<Event> chosen = chooseEvent("Update event capacity", service.events());
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        io.println();
        io.println("Event: " + event.id() + " - " + event.name());
        io.println("Current capacity: " + event.capacity());
        io.println("Registered attendees: " + service.registeredCount(event));

        while (true) {
            String raw = io.readLine("New capacity (blank to cancel): ").trim();
            if (raw.isEmpty()) {
                return;
            }
            int capacity;
            try {
                capacity = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                io.println("Capacity not updated: Capacity must be a positive whole number.");
                continue;
            }
            try {
                Event updated = service.updateCapacity(event.id(), capacity);
                io.println("Capacity updated.");
                printEventSummary(updated);
                return;
            } catch (EventServiceException ex) {
                io.println("Capacity not updated: " + ex.getMessage());
            }
        }
    }

    private void registerAttendeeFlow() {
        Optional<Event> chosen = chooseEvent("Register attendee", service.events());
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        if (event.isCancelled()) {
            io.println("This event has been cancelled and cannot accept registrations.");
            return;
        }
        if (service.isPast(event)) {
            io.println("This event has already taken place and cannot accept registrations.");
            return;
        }
        int registered = service.registeredCount(event);
        if (registered >= event.capacity()) {
            io.println("This event has reached its capacity of " + event.capacity()
                    + ". Further registrations are blocked.");
            return;
        }

        io.println();
        io.println("Event: " + event.id() + " - " + event.name() + " ("
                + registered + " / " + event.capacity() + " registered)");
        String name = io.readLine("Attendee name (blank to cancel): ").trim();
        if (name.isEmpty()) {
            return;
        }

        try {
            Registration registration = service.registerAttendee(event.id(), name);
            int newCount = service.registeredCount(event);
            io.println("Registration confirmed.");
            io.println("Reference: " + registration.reference());
            io.println("Attendee: " + registration.attendeeName());
            io.println("Event: " + event.id() + " - " + event.name());
            io.println("Registered: " + newCount + " / " + event.capacity());
            if (newCount >= event.capacity()) {
                io.println("The venue is now at full capacity. Further registrations are blocked.");
            }
        } catch (EventServiceException ex) {
            io.println("Registration failed: " + ex.getMessage());
        }
    }

    private void cancelRegistrationFlow() {
        List<Event> candidates = service.events().stream()
                .filter(event -> service.registeredCount(event) > 0)
                .toList();
        if (candidates.isEmpty()) {
            io.println("No registrations have been made yet.");
            return;
        }
        Optional<Event> chosen = chooseEvent("Cancel registration", candidates);
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        printRegistrations(event);

        while (true) {
            String reference = io.readLine("Registration reference (blank to cancel): ").trim();
            if (reference.isEmpty()) {
                return;
            }
            Optional<Registration> found = service.findRegistration(event.id(), reference);
            if (found.isEmpty()) {
                io.println("No registration found with that reference for this event.");
                continue;
            }
            Registration registration = found.get();
            io.println("Registration " + registration.reference()
                    + " belongs to " + registration.attendeeName() + ".");
            if (!io.confirm("Cancel this registration? (y/N): ")) {
                io.println("Cancellation aborted.");
                return;
            }
            try {
                service.cancelRegistration(event.id(), registration.reference());
                io.println("Registration cancelled. The place is available again.");
                io.println("Registered: " + service.registeredCount(event) + " / " + event.capacity());
                return;
            } catch (EventServiceException ex) {
                io.println("Registration not cancelled: " + ex.getMessage());
                return;
            }
        }
    }

    private void markAttendanceFlow() {
        List<Event> candidates = service.events().stream()
                .filter(event -> service.registeredCount(event) > 0)
                .toList();
        if (candidates.isEmpty()) {
            io.println("There are no registrations to mark.");
            return;
        }
        Optional<Event> chosen = chooseEvent("Mark attendance", candidates);
        if (chosen.isEmpty()) {
            return;
        }
        Event event = chosen.get();
        printRegistrations(event);

        while (true) {
            String reference = io.readLine("Registration reference (blank to cancel): ").trim();
            if (reference.isEmpty()) {
                return;
            }
            Optional<Registration> found = service.findRegistration(event.id(), reference);
            if (found.isEmpty()) {
                io.println("No registration found. Only registered attendees can be marked as attended.");
                continue;
            }
            if (found.get().attended()) {
                io.println("That attendee is already marked as attended.");
                return;
            }
            try {
                Registration attended = service.markAttended(event.id(), found.get().reference());
                io.println(attended.attendeeName() + " marked as attended.");
                io.println("Registered: " + service.registeredCount(event)
                        + ", attended: " + service.attendedCount(event) + ".");
                return;
            } catch (EventServiceException ex) {
                io.println("Attendance not updated: " + ex.getMessage());
                return;
            }
        }
    }

    private Optional<Event> chooseEvent(String heading, List<Event> candidates) {
        io.println("--- " + heading + " ---");
        if (candidates.isEmpty()) {
            io.println("No events.");
            return Optional.empty();
        }
        printGroupedEvents(candidates);
        while (true) {
            String raw = io.readLine("Event ID (blank to cancel): ").trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            Optional<Event> found = service.findEvent(raw);
            if (found.isEmpty()) {
                io.println("No event found with ID " + raw + ".");
                continue;
            }
            boolean listed = candidates.stream()
                    .anyMatch(event -> event.id().equalsIgnoreCase(found.get().id()));
            if (!listed) {
                io.println("That event is not available for this action.");
                continue;
            }
            return found;
        }
    }

    private void printGroupedEvents(List<Event> events) {
        List<Event> active = events.stream().filter(event -> !event.isCancelled()).toList();
        List<Event> cancelled = events.stream().filter(Event::isCancelled).toList();
        if (!active.isEmpty()) {
            io.println("Active events:");
            printEventTable(active);
        }
        if (!cancelled.isEmpty()) {
            if (!active.isEmpty()) {
                io.println();
            }
            io.println("Cancelled events:");
            printEventTable(cancelled);
        }
    }

    private void printEventTable(List<Event> events) {
        List<List<String>> rows = events.stream()
                .map(event -> List.of(
                        event.id(),
                        event.name(),
                        formatDate(event.start()),
                        event.venue(),
                        Integer.toString(event.capacity()),
                        Integer.toString(service.registeredCount(event)),
                        event.isCancelled() ? "Cancelled" : "Active"))
                .toList();
        TablePrinter.print(io, EVENT_TABLE_HEADERS, rows);
    }

    private void printEventDetails(Event event) {
        int registered = service.registeredCount(event);
        int attended = service.attendedCount(event);
        int available = service.availablePlaces(event);

        io.println();
        io.println("Event " + event.id());
        io.println("Name: " + event.name());
        io.println("Date & time: " + formatDate(event.start()));
        io.println("Venue: " + event.venue());
        io.println("Status: " + (event.isCancelled() ? "Cancelled" : "Active"));
        io.println("Capacity: " + event.capacity());
        io.println("Registered: " + registered);
        io.println("Attended: " + attended);
        io.println("Available places: " + available);
        if (event.isCancelled()) {
            io.println("This event is cancelled and cannot accept new registrations.");
        } else if (service.isPast(event)) {
            io.println("This event has already started.");
        } else if (available == 0) {
            io.println("This event is at full capacity.");
        }
        printRegistrations(event);
    }

    private void printRegistrations(Event event) {
        io.println();
        io.println("Registrations for " + event.id() + " ("
                + service.registeredCount(event) + " registered, "
                + service.attendedCount(event) + " attended):");
        List<Registration> registrations = service.registrationsFor(event.id());
        if (registrations.isEmpty()) {
            io.println("No registrations.");
            return;
        }
        List<List<String>> rows = registrations.stream()
                .map(registration -> List.of(
                        registration.reference(),
                        registration.attendeeName(),
                        formatDate(registration.registeredAt()),
                        registration.attended() ? "Yes" : "No"))
                .toList();
        TablePrinter.print(io, List.of("Reference", "Attendee", "Registered at", "Attended"), rows);
    }

    private void printEventSummary(Event event) {
        io.println("  ID: " + event.id());
        io.println("  Name: " + event.name());
        io.println("  Date & time: " + formatDate(event.start()));
        io.println("  Venue: " + event.venue());
        io.println("  Capacity: " + event.capacity());
        io.println("  Registered: " + service.registeredCount(event) + " / " + event.capacity());
        io.println("  Status: " + (event.isCancelled() ? "Cancelled" : "Active"));
    }

    private void printErrors(String heading, List<String> errors) {
        io.println(heading);
        for (String error : errors) {
            io.println("  - " + error);
        }
    }

    private static int parsePositiveCapacity(String raw, List<String> errors) {
        try {
            int capacity = Integer.parseInt(raw.trim());
            if (capacity <= 0) {
                errors.add("Capacity must be a positive whole number.");
                return 0;
            }
            return capacity;
        } catch (NumberFormatException ex) {
            errors.add("Capacity must be a positive whole number.");
            return 0;
        }
    }

    private int readIntChoice(String prompt, int minimum, int maximum) {
        while (true) {
            String raw = io.readLine(prompt).trim();
            try {
                int choice = Integer.parseInt(raw);
                if (choice >= minimum && choice <= maximum) {
                    return choice;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the same message.
            }
            io.println("Invalid choice. Enter a number from " + minimum + " to " + maximum + ".");
        }
    }

    private static boolean matchesFilter(Event event, int field, String needle) {
        String name = event.name().toLowerCase(Locale.ROOT);
        String id = event.id().toLowerCase(Locale.ROOT);
        String date = formatDate(event.start()).toLowerCase(Locale.ROOT);
        String venue = event.venue().toLowerCase(Locale.ROOT);
        return switch (field) {
            case 1 -> name.contains(needle);
            case 2 -> id.contains(needle);
            case 3 -> date.contains(needle);
            case 4 -> venue.contains(needle);
            default -> String.join(" ", id, name, date, venue).contains(needle);
        };
    }

    private static LocalDateTime parseDateTime(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, INPUT_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Try the ISO format next.
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String formatDate(LocalDateTime dateTime) {
        return DISPLAY_DATE_TIME.format(dateTime);
    }
}
