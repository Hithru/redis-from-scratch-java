import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * SimpleCommandHandler:
 * - Implements PING -> +PONG\r\n
 * - Encapsulates RESP response details.
 */
public class SimpleCommandHandler implements CommandHandler {

    private static final byte[] PONG_RESPONSE =
            "+PONG\r\n".getBytes(StandardCharsets.UTF_8);

    @Override
    public void handlePing(SocketChannel clientChannel) throws IOException {
        ByteBuffer response = ByteBuffer.wrap(PONG_RESPONSE);

        // Ensure all bytes are written
        while (response.hasRemaining()) {
            clientChannel.write(response);
        }
    }
}
