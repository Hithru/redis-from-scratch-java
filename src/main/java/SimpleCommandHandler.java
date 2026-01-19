import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SimpleCommandHandler implements CommandHandler {

    private static final String CRLF = "\r\n";

    // In-memory key-value store with optional expiry
    private final Map<String, ValueEntry> store = new HashMap<>();

    @Override
    public void handleCommand(SocketChannel clientChannel, List<String> commandArgs) throws IOException {
        if (commandArgs == null || commandArgs.isEmpty()) {
            writeError(clientChannel, "ERR empty command");
            return;
        }

        String cmd = commandArgs.get(0).toUpperCase(Locale.ROOT);

        switch (cmd) {
            case "PING" -> handlePing(clientChannel, commandArgs);
            case "ECHO" -> handleEcho(clientChannel, commandArgs);
            case "SET"  -> handleSet(clientChannel, commandArgs);
            case "GET"  -> handleGet(clientChannel, commandArgs);
            default -> writeError(clientChannel, "ERR unknown command '" + cmd + "'");
        }
    }

    // ----- PING -----

    private void handlePing(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() == 1) {
            writeSimpleString(clientChannel, "PONG");
        } else {
            String msg = args.get(1);
            writeBulkString(clientChannel, msg);
        }
    }

    // ----- ECHO -----

    private void handleEcho(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            writeError(clientChannel, "ERR wrong number of arguments for 'ECHO'");
            return;
        }
        String msg = args.get(1);
        writeBulkString(clientChannel, msg);
    }

    // ----- SET -----
    // SET key value [PX milliseconds]
    private void handleSet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 3) {
            writeError(clientChannel, "ERR wrong number of arguments for 'SET'");
            return;
        }

        String key = args.get(1);
        String value = args.get(2);

        Long expireAtMs = null; // no expiry by default

        // Parse optional arguments: currently only PX <ms>
        // Command arguments are case-insensitive (PX, px, pX, etc.)
        int i = 3;
        long now = System.currentTimeMillis();

        while (i < args.size()) {
            String opt = args.get(i);
            if ("PX".equalsIgnoreCase(opt)) {
                if (i + 1 >= args.size()) {
                    writeError(clientChannel, "ERR syntax error");
                    return;
                }
                String ttlStr = args.get(i + 1);
                long ttlMs;
                try {
                    ttlMs = Long.parseLong(ttlStr);
                } catch (NumberFormatException e) {
                    writeError(clientChannel, "ERR value is not an integer or out of range");
                    return;
                }
                expireAtMs = now + ttlMs;
                i += 2;
            } else {
                // Unknown option; for now just treat as syntax error
                writeError(clientChannel, "ERR syntax error");
                return;
            }
        }

        store.put(key, new ValueEntry(value, expireAtMs));

        writeSimpleString(clientChannel, "OK");
    }

    // ----- GET -----
    // GET key -> Bulk string or Null bulk string
    private void handleGet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            writeError(clientChannel, "ERR wrong number of arguments for 'GET'");
            return;
        }

        String key = args.get(1);
        long now = System.currentTimeMillis();

        ValueEntry entry = store.get(key);
        if (entry == null) {
            writeNullBulkString(clientChannel);
            return;
        }

        // Passive expiration check
        if (entry.isExpired(now)) {
            store.remove(key);
            writeNullBulkString(clientChannel);
            return;
        }

        writeBulkString(clientChannel, entry.getValue());
    }

    // ----- RESP helpers -----

    private void writeSimpleString(SocketChannel clientChannel, String value) throws IOException {
        String resp = "+" + value + CRLF;
        writeAll(clientChannel, resp.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBulkString(SocketChannel clientChannel, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        String header = "$" + bytes.length + CRLF;

        ByteBuffer[] buffers = new ByteBuffer[] {
                ByteBuffer.wrap(header.getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap(bytes),
                ByteBuffer.wrap(CRLF.getBytes(StandardCharsets.UTF_8))
        };

        for (ByteBuffer buffer : buffers) {
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }
        }
    }

    private void writeNullBulkString(SocketChannel clientChannel) throws IOException {
        // Null bulk string: $-1\r\n
        String resp = "$-1" + CRLF;
        writeAll(clientChannel, resp.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(SocketChannel clientChannel, String message) throws IOException {
        String resp = "-" + message + CRLF;
        writeAll(clientChannel, resp.getBytes(StandardCharsets.UTF_8));
    }

    private void writeAll(SocketChannel clientChannel, byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            clientChannel.write(buffer);
        }
    }
}
