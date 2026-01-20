import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SimpleCommandHandler implements CommandHandler {

    // In-memory key-value store with optional expiry
    private final Map<String, ValueEntry> store = new HashMap<>();

    @Override
    public void handleCommand(SocketChannel clientChannel, List<String> commandArgs) throws IOException {
        if (commandArgs == null || commandArgs.isEmpty()) {
            RespWriter.writeError(clientChannel, "ERR empty command");
            return;
        }

        String cmd = commandArgs.get(0).toUpperCase(Locale.ROOT);

        switch (cmd) {
            case "PING" -> handlePing(clientChannel, commandArgs);
            case "ECHO" -> handleEcho(clientChannel, commandArgs);
            case "SET"  -> handleSet(clientChannel, commandArgs);
            case "GET"  -> handleGet(clientChannel, commandArgs);
            default -> RespWriter.writeError(clientChannel, "ERR unknown command '" + cmd + "'");
        }
    }

    // ----- PING -----

    private void handlePing(SocketChannel clientChannel, List<String> args) throws IOException {
        // PING or PING <message>
        if (args.size() == 1) {
            RespWriter.writeSimpleString(clientChannel, "PONG");
        } else {
            String msg = args.get(1);
            RespWriter.writeBulkString(clientChannel, msg);
        }
    }

    // ----- ECHO -----

    private void handleEcho(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'ECHO'");
            return;
        }
        String msg = args.get(1);
        RespWriter.writeBulkString(clientChannel, msg);
    }

    // ----- SET -----
    // SET key value [PX milliseconds]
    private void handleSet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 3) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'SET'");
            return;
        }

        String key = args.get(1);
        String value = args.get(2);

        Long expireAtMs = null; // no expiry by default

        // Parse optional arguments: currently only PX <ms>
        int i = 3;
        long now = System.currentTimeMillis();

        while (i < args.size()) {
            String opt = args.get(i);
            if ("PX".equalsIgnoreCase(opt)) {
                if (i + 1 >= args.size()) {
                    RespWriter.writeError(clientChannel, "ERR syntax error");
                    return;
                }
                String ttlStr = args.get(i + 1);
                long ttlMs;
                try {
                    ttlMs = Long.parseLong(ttlStr);
                } catch (NumberFormatException e) {
                    RespWriter.writeError(clientChannel, "ERR value is not an integer or out of range");
                    return;
                }
                expireAtMs = now + ttlMs;
                i += 2;
            } else {
                // Unknown option; for now just treat as syntax error
                RespWriter.writeError(clientChannel, "ERR syntax error");
                return;
            }
        }

        store.put(key, new ValueEntry(value, expireAtMs));

        RespWriter.writeSimpleString(clientChannel, "OK");
    }

    // ----- GET -----
    // GET key -> Bulk string or Null bulk string
    private void handleGet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'GET'");
            return;
        }

        String key = args.get(1);
        long now = System.currentTimeMillis();

        ValueEntry entry = store.get(key);
        if (entry == null) {
            RespWriter.writeNullBulkString(clientChannel);
            return;
        }

        // Passive expiration check
        if (entry.isExpired(now)) {
            store.remove(key);
            RespWriter.writeNullBulkString(clientChannel);
            return;
        }

        RespWriter.writeBulkString(clientChannel, entry.getValue());
    }
}
