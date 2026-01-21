package dev.hithru.redis.command;

import dev.hithru.redis.protocol.RespWriter;
import dev.hithru.redis.store.InMemoryKeyValueStore;
import dev.hithru.redis.store.list.InMemoryListStore;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimpleCommandHandler implements CommandHandler {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final InMemoryListStore listStore = new InMemoryListStore();

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
            case "LRANGE" -> handleLrange(clientChannel, commandArgs);
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

    InMemoryKeyValueStore getStringStore() {
        return store;
    }

    InMemoryKeyValueStore getStore() {
        return store;
    }
}
