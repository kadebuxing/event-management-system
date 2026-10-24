package com.eventmanagement;

import com.eventmanagement.cli.ConsoleApp;
import com.eventmanagement.cli.ConsoleIO;
import com.eventmanagement.persistence.FileEventRepository;
import com.eventmanagement.service.EventService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path dataFile = Path.of(System.getProperty("event.data.file", "data/events.db"));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        ConsoleIO io = new ConsoleIO(System.in, out);

        EventService service;
        try {
            service = new EventService(new FileEventRepository(dataFile), Clock.systemDefaultZone());
        } catch (IOException | RuntimeException ex) {
            System.err.println("Could not start Event Management System: " + ex.getMessage());
            System.exit(1);
            return;
        }

        new ConsoleApp(service, io, dataFile.toAbsolutePath().normalize().toString()).run();
    }
}
