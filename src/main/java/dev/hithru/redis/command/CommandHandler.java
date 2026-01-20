package dev.hithru.redis.command;
import java.io.IOException;
import java.nio.channels.SocketChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * CommandHandler:
 *  - Handles Redis-style commands parsed from RESP.
 *  - commandArgs.get(0) is the command name (e.g. "PING", "ECHO").
 *  - Remaining elements are arguments.
 */
public interface CommandHandler {
    void handleCommand(SocketChannel clientChannel, List<String> commandArgs) throws IOException;
}

