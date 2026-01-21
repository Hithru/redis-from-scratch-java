package dev.hithru.redis.protocol;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RespWriter
 *
 * Utility class for writing RESP2-encoded responses to a SocketChannel.
 * Supports:
 *  - Simple Strings (+OK\r\n)
 *  - Bulk Strings ($3\r\nfoo\r\n)
 *  - Null Bulk Strings ($-1\r\n)
 *  - Errors (-ERR ...\r\n)
 */
public class RespWriter {

    private static final String CRLF = "\r\n";

    private RespWriter() {
        // utility class, no instances
    }

    public static void writeSimpleString(SocketChannel channel, String value) throws IOException {
        String resp = "+" + value + CRLF;
        writeAll(channel, resp.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBulkString(SocketChannel channel, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        String header = "$" + bytes.length + CRLF;

        ByteBuffer[] buffers = new ByteBuffer[] {
                ByteBuffer.wrap(header.getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap(bytes),
                ByteBuffer.wrap(CRLF.getBytes(StandardCharsets.UTF_8))
        };

        for (ByteBuffer buffer : buffers) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    public static void writeNullBulkString(SocketChannel channel) throws IOException {
        String resp = "$-1" + CRLF;
        writeAll(channel, resp.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeError(SocketChannel channel, String message) throws IOException {
        String resp = "-" + message + CRLF;
        writeAll(channel, resp.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeInteger(SocketChannel channel, long value) throws IOException {
        String resp = ":" + value + CRLF;
        writeAll(channel, resp.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAll(SocketChannel channel, byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    public static void writeArrayOfBulkStrings(SocketChannel channel, List<String> values) throws IOException {
        // Array header: *<count>\r\n
        String header = "*" + values.size() + CRLF;
        writeAll(channel, header.getBytes(StandardCharsets.UTF_8));

        for (String value : values) {
            writeBulkString(channel, value);
        }
    }
}
