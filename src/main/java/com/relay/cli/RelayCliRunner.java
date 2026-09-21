package com.relay.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Interactive console for exercising the whole app without Postman/curl.
 * Runs on the main thread after Spring Boot has fully started (Tomcat
 * and the worker thread pool are already running independently), so
 * blocking here to read stdin doesn't stop the engine from processing
 * steps in the background while you type commands.
 *
 * Disable for automated tests via relay.cli.enabled=false, otherwise
 * a test run would hang forever waiting on System.in.
 */
@Component
@ConditionalOnProperty(name = "relay.cli.enabled", havingValue = "true", matchIfMissing = true)
public class RelayCliRunner implements CommandLineRunner {

    private final CliCommandHandler commandHandler;

    public RelayCliRunner(CliCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==========================================");
        System.out.println(" Relay CLI Console - type 'help' for commands");
        System.out.println("==========================================");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("relay> ");
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                System.out.print("relay> ");
                continue;
            }
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Bye.");
                break;
            }
            try {
                commandHandler.handle(line);
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
            System.out.print("relay> ");
        }
    }
}