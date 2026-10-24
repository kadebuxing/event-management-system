package com.eventmanagement.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ConsoleIO {

    private final BufferedReader reader;
    private final PrintStream out;

    public ConsoleIO(InputStream in, PrintStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new EndOfInputException();
            }
            return line;
        } catch (IOException ex) {
            throw new EndOfInputException();
        }
    }

    public boolean confirm(String prompt) {
        while (true) {
            String answer = readLine(prompt).trim().toLowerCase(Locale.ROOT);
            if (answer.isEmpty() || answer.equals("n") || answer.equals("no")) {
                return false;
            }
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            println("Please answer y or n.");
        }
    }

    public void println() {
        out.println();
    }

    public void println(String message) {
        out.println(message);
    }

    public static final class EndOfInputException extends RuntimeException {
    }
}
