package agents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Agent Builder that uses pwd to determine current location before running
 * commands.
 */
public class AgentBuilder {

    public static void main(String[] args) {
        try {
            // Use pwd to determine current location
            String currentDir = getCurrentDirectory();
            System.out.println("Current directory: " + currentDir);

            // Run a build command
            runCommand("mvn package");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getCurrentDirectory() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec("pwd");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        process.waitFor();
        return line;
    }

    private static void runCommand(String command) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        process.waitFor();
    }
}