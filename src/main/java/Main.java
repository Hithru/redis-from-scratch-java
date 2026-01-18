import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from program will appear here!");
        int port = 6379;

        CommandHandler handler = new SimpleCommandHandler();
        RedisServer server = new RedisServer(port, handler);

        try {
            server.start(); // runs the event loop, blocks forever
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}
