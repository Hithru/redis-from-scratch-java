package dev.hithru.redis.command;

import dev.hithru.redis.protocol.RespWriter;
import dev.hithru.redis.store.InMemoryKeyValueStore;
import dev.hithru.redis.store.list.InMemoryListStore;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;

public class SimpleCommandHandler implements CommandHandler {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final InMemoryListStore listStore = new InMemoryListStore();

    private final Map<String, Deque<BlpopWaiter>> blpopWaiters = new HashMap<>();


    private static class BlpopWaiter {
        final SocketChannel channel;
        final Long deadlineMillis; // null = infinite wait

        BlpopWaiter(SocketChannel channel, Long deadlineMillis) {
            this.channel = channel;
            this.deadlineMillis = deadlineMillis;
        }
    }

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
            case "RPUSH" -> handleRpush(clientChannel, commandArgs);
            case "LPUSH"  -> handleLpush(clientChannel, commandArgs);
            case "LRANGE" -> handleLrange(clientChannel, commandArgs);
            case "LLEN"   -> handleLlen(clientChannel, commandArgs);
            case "LPOP"   -> handleLpop(clientChannel, commandArgs);
            case "BLPOP"  -> handleBlpop(clientChannel, commandArgs);
            default -> RespWriter.writeError(clientChannel, "ERR unknown command '" + cmd + "'");
        }
    }

    private void handlePing(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() == 1) {
            RespWriter.writeSimpleString(clientChannel, "PONG");
        } else {
            String msg = args.get(1);
            RespWriter.writeBulkString(clientChannel, msg);
        }
    }

    private void handleEcho(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'ECHO'");
            return;
        }
        String msg = args.get(1);
        RespWriter.writeBulkString(clientChannel, msg);
    }

    // SET key value [PX ms]
    private void handleSet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 3) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'SET'");
            return;
        }

        String key = args.get(1);
        String value = args.get(2);

        Long expireAtMs = null;
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
                RespWriter.writeError(clientChannel, "ERR syntax error");
                return;
            }
        }

        store.set(key, value, expireAtMs);
        RespWriter.writeSimpleString(clientChannel, "OK");
    }

    private void handleGet(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'GET'");
            return;
        }

        String key = args.get(1);
        long now = System.currentTimeMillis();
        String value = store.get(key, now);

        if (value == null) {
            RespWriter.writeNullBulkString(clientChannel);
        } else {
            RespWriter.writeBulkString(clientChannel, value);
        }
    }

    // RPUSH key value [value ...] -> :<new_length>
    private void handleRpush(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 3) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'RPUSH'");
            return;
        }

        String key = args.get(1);

        List<String> valuesToAppend = new ArrayList<>(args.size() - 2);
        for (int i = 2; i < args.size(); i++) {
            valuesToAppend.add(args.get(i));
        }

        int newLength = listStore.rpush(key, valuesToAppend);

        satisfyBlpopWaiters(key);

        RespWriter.writeInteger(clientChannel, newLength);
    }

    private void handleLpush(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 3) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'LPUSH'");
            return;
        }

        String key = args.get(1);

        List<String> valuesToPrepend = new ArrayList<>(args.size() - 2);
        for (int i = 2; i < args.size(); i++) {
            valuesToPrepend.add(args.get(i));
        }

        int newLength = listStore.lpush(key, valuesToPrepend);

        satisfyBlpopWaiters(key);

        RespWriter.writeInteger(clientChannel, newLength);
    }

    private void handleLrange(SocketChannel clientChannel, List<String> args) throws IOException {
        // LRANGE key start stop
        if (args.size() < 4) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'LRANGE'");
            return;
        }

        String key = args.get(1);
        int start;
        int stop;
        try {
            start = Integer.parseInt(args.get(2));
            stop = Integer.parseInt(args.get(3));
        } catch (NumberFormatException e) {
            RespWriter.writeError(clientChannel, "ERR value is not an integer or out of range");
            return;
        }


        List<String> range = listStore.lrange(key, start, stop);

        RespWriter.writeArrayOfBulkStrings(clientChannel, range);
    }

    private void handleLlen(SocketChannel clientChannel, List<String> args) throws IOException {
        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'LLEN'");
            return;
        }

        String key = args.get(1);
        int length = listStore.size(key);

        RespWriter.writeInteger(clientChannel, length);
    }

    private void handleLpop(SocketChannel clientChannel, List<String> args) throws IOException {

        if (args.size() < 2) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'LPOP'");
            return;
        }

        String key = args.get(1);

        if (args.size() == 2) {
            String value = listStore.lpop(key);
            if (value == null) {
                RespWriter.writeNullBulkString(clientChannel);
            } else {
                RespWriter.writeBulkString(clientChannel, value);
            }
        } else if (args.size() == 3) {
            int count;
            try {
                count = Integer.parseInt(args.get(2));
            } catch (NumberFormatException e) {
                RespWriter.writeError(clientChannel, "ERR value is not an integer or out of range");
                return;
            }

            if (count <= 0) {
                RespWriter.writeArrayOfBulkStrings(clientChannel, List.of());
                return;
            }

            var removed = listStore.lpopMany(key, count);
            RespWriter.writeArrayOfBulkStrings(clientChannel, removed);
        } else {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'LPOP'");
        }
    }

    private void handleBlpop(SocketChannel clientChannel, List<String> args) throws IOException {
        // BLPOP key timeout
        if (args.size() < 3) {
            RespWriter.writeError(clientChannel, "ERR wrong number of arguments for 'BLPOP'");
            return;
        }

        String key = args.get(1);
        String timeoutStr = args.get(2);

        double timeoutSeconds;
        try {
            timeoutSeconds = Double.parseDouble(timeoutStr);
        } catch (NumberFormatException e) {
            RespWriter.writeError(clientChannel, "ERR value is not a valid timeout");
            return;
        }

        long timeoutMs = (long) (timeoutSeconds * 1000);

        // 1. Try immediate pop first
        String value = listStore.lpop(key);
        if (value != null) {
            RespWriter.writeArrayOfBulkStrings(clientChannel, List.of(key, value));
            return;
        }

        // 2. If no element and timeout <= 0 -> infinite wait
        if (timeoutMs <= 0) {
            registerBlpopWaiter(key, clientChannel, null);
            return; // no immediate response
        }

        // 3. Finite timeout: register waiter with deadline
        long now = System.currentTimeMillis();
        long deadline = now + timeoutMs;
        registerBlpopWaiter(key, clientChannel, deadline);
        // response will be sent on push or timeout
    }

    private void registerBlpopWaiter(String key, SocketChannel clientChannel, Long deadlineMillis) {
        blpopWaiters
                .computeIfAbsent(key, k -> new ArrayDeque<>())
                .addLast(new BlpopWaiter(clientChannel, deadlineMillis));
    }

    // Wake up BLPOP waiters when new elements are pushed to this key
    private void satisfyBlpopWaiters(String key) {
        Deque<BlpopWaiter> waiters = blpopWaiters.get(key);
        if (waiters == null || waiters.isEmpty()) {
            return;
        }

        while (!waiters.isEmpty()) {
            BlpopWaiter waiter = waiters.pollFirst();

            String popped = listStore.lpop(key);
            if (popped == null) {
                // No more elements; stop here
                break;
            }

            try {
                // BLPOP reply: [key, value]
                RespWriter.writeArrayOfBulkStrings(waiter.channel, List.of(key, popped));
            } catch (IOException e) {
                // Client may have disconnected; ignore and continue
            }
        }

        if (waiters.isEmpty()) {
            blpopWaiters.remove(key);
        }
    }

    @Override
    public void onTick() {
        if (blpopWaiters.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, Deque<BlpopWaiter>>> mapIt = blpopWaiters.entrySet().iterator();
        while (mapIt.hasNext()) {
            Map.Entry<String, Deque<BlpopWaiter>> entry = mapIt.next();
            Deque<BlpopWaiter> queue = entry.getValue();

            if (queue.isEmpty()) {
                mapIt.remove();
                continue;
            }

            Iterator<BlpopWaiter> qIt = queue.iterator();
            while (qIt.hasNext()) {
                BlpopWaiter waiter = qIt.next();

                if (waiter.deadlineMillis == null) {
                    // Infinite wait, no timeout
                    continue;
                }

                if (now >= waiter.deadlineMillis) {
                    // Timeout: respond with null array
                    try {
                        RespWriter.writeNullArray(waiter.channel);
                    } catch (IOException e) {
                        // Ignore write failure; client may be gone
                    }
                    qIt.remove();
                }
            }

            if (queue.isEmpty()) {
                mapIt.remove();
            }
        }
    }



    InMemoryKeyValueStore getStringStore() {
        return store;
    }

    InMemoryKeyValueStore getStore() {
        return store;
    }
}
