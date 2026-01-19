import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the state for a single client connection.
 * - Reads bytes from the client
 * - Accumulates them into an internal buffer
 * - Parses RESP Arrays of Bulk Strings into List<String> commands
 */
public class ClientSession {
    private static final int BUFFER_SIZE = 1024;

    private final SocketChannel channel;
    private final StringBuilder inputBuffer = new StringBuilder();

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
            List<String> command = tryParseRespArrayOfBulkStrings();
            if (command == null) {
                // No full command available yet
                break;
            }

            handler.handleCommand(channel, command);
        }
    }

    /**
     * Tries to parse a single RESP Array of Bulk Strings from the start of inputBuffer.
     *
     * On success:
     *  - returns a List<String> representing the array elements
     *  - removes the consumed text from inputBuffer
     * On incomplete data:
     *  - returns null and leaves inputBuffer unchanged
     */
    private List<String> tryParseRespArrayOfBulkStrings() {
        if (inputBuffer.length() == 0) {
            return null;
        }

        int len = inputBuffer.length();
        int idx = 0;

        // Expect an Array
        if (inputBuffer.charAt(idx) != '*') {
            // For now, if it's not an array, we don't know how to handle it
            // You could choose to clear the buffer or close the connection instead.
            return null;
        }

        int lineEnd = inputBuffer.indexOf("\r\n", idx);
        if (lineEnd == -1) {
            // Incomplete line
            return null;
        }

        String countStr = inputBuffer.substring(idx + 1, lineEnd);
        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            // Invalid RESP, bail out
            return null;
        }

        idx = lineEnd + 2; // position after the array header CRLF

        List<String> elements = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            // Need at least "$<len>\r\n"
            if (idx >= len || inputBuffer.charAt(idx) != '$') {
                return null; // incomplete or invalid
            }

            int bulkLenLineEnd = inputBuffer.indexOf("\r\n", idx);
            if (bulkLenLineEnd == -1) {
                return null; // incomplete
            }

            String bulkLenStr = inputBuffer.substring(idx + 1, bulkLenLineEnd);
            int bulkLen;
            try {
                bulkLen = Integer.parseInt(bulkLenStr);
            } catch (NumberFormatException e) {
                return null;
            }

            idx = bulkLenLineEnd + 2; // position at start of bulk data

            // Need bulkLen bytes + CRLF
            if (idx + bulkLen + 2 > len) {
                return null; // incomplete
            }

            String value = inputBuffer.substring(idx, idx + bulkLen);
            elements.add(value);

            idx += bulkLen;

            // Skip trailing CRLF
            if (!inputBuffer.substring(idx, idx + 2).equals("\r\n")) {
                return null; // invalid
            }
            idx += 2;
        }

        // Successfully parsed one full RESP array
        // Remove consumed data from buffer
        inputBuffer.delete(0, idx);

        return elements;
    }
}
