package dev.hithru.redis.server;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.hithru.redis.command.CommandHandler;
import dev.hithru.redis.protocol.RespParser;

/**
 * Represents the state for a single client connection.
 * - Reads bytes from the client
 * - Accumulates them into an internal buffer
 * - Uses RespParser to parse RESP Arrays of Bulk Strings into List<String> commands
 */
public class ClientSession {
    private static final int BUFFER_SIZE = 1024;

    private final SocketChannel channel;
    private final StringBuilder inputBuffer = new StringBuilder();
    private final RespParser respParser = new RespParser();

    public ClientSession(SocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Reads data from client and processes all complete commands in the buffer.
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

        if (bytesRead > 0) {
            buffer.flip();
            String chunk = StandardCharsets.UTF_8.decode(buffer).toString();
            inputBuffer.append(chunk);

            processCommands(handler);
        }

        // bytesRead == 0: nothing more to do right now
        return true;
    }

    /**
     * Try to parse as many full RESP Array commands as possible.
     */
    private void processCommands(CommandHandler handler) throws IOException {
        while (true) {
            List<String> command = respParser.tryParseArrayOfBulkStrings(inputBuffer);
            if (command == null) {
                // No full command available yet
                break;
            }

            handler.handleCommand(channel, command);
        }
    }
}
