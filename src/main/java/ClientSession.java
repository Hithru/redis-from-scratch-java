import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Represents the state for a single client connection.
 * - Reads bytes from the client
 * - Accumulates them into a StringBuilder
 * - Naively searches for "PING" and triggers handler for each occurrence
 */
public class ClientSession {
    private static final int BUFFER_SIZE = 1024;

    private final SocketChannel channel;
    private final StringBuilder inputBuffer = new StringBuilder();

    public ClientSession(SocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Reads data from client and processes commands.
     *
     * @return true if connection remains open, false if client closed it.
     */
    public boolean readFromClient(CommandHandler handler) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            // Client closed the connection
            System.out.println("Client disconnected: " + channel.getRemoteAddress());
            return false;
        }

        if (bytesRead == 0) {
            // No data right now
            return true;
        }

        buffer.flip();
        String chunk = StandardCharsets.UTF_8.decode(buffer).toString();
        inputBuffer.append(chunk);

        // For this stage: we only care about PING commands
        processCommands(handler);

        return true;
    }

    private void processCommands(CommandHandler handler) throws IOException {
        while (true) {
            int idx = inputBuffer.indexOf("PING");
            if (idx == -1) {
                // No complete PING found yet
                break;
            }

            // Consume everything up to and including "PING"
            inputBuffer.delete(0, idx + "PING".length());

            // Delegate to handler
            handler.handlePing(channel);
        }
    }
}
