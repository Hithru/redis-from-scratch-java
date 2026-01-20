package dev.hithru.redis.server;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

import dev.hithru.redis.command.CommandHandler;

/**
 * RedisServer
 * - Implements an event loop using Java NIO (Selector + Channels)
 * - Accepts multiple clients and delegates I/O to ClientSession
 */
public class RedisServer {
    private final int port;
    private final CommandHandler commandHandler;

    private Selector selector;
    private ServerSocketChannel serverChannel;

    public RedisServer(int port, CommandHandler commandHandler) {
        this.port = port;
        this.commandHandler = commandHandler;
    }

    public void start() throws IOException {
        // 1. Open selector (multiplexes events)
        selector = Selector.open();

        // 2. Open non-blocking server channel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));

        // 3. Register server channel for ACCEPT events
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server listening on port " + port + "...");

        // 4. Run the event loop
        eventLoop();
    }

    private void eventLoop() throws IOException {
        while (true) {
            // Block until at least one channel is ready
            selector.select();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove(); // very important

                try {
                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                } catch (IOException e) {
                    closeKey(key);
                    System.out.println("Client error: " + e.getMessage());
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept(); // may be null in non-blocking mode
        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);
        System.out.println("Accepted connection from " + clientChannel.getRemoteAddress());

        ClientSession session = new ClientSession(clientChannel);

        // Attach session so we can get it back in handleRead
        clientChannel.register(selector, SelectionKey.OP_READ, session);
    }

    private void handleRead(SelectionKey key) throws IOException {
        Object attachment = key.attachment();
        if (!(attachment instanceof ClientSession)) {
            closeKey(key);
            return;
        }

        ClientSession session = (ClientSession) attachment;
        boolean open = session.readFromClient(commandHandler);

        if (!open) {
            closeKey(key);
        }
    }

    private void closeKey(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
        key.cancel();
    }
}
