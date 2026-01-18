import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * CommandHandler:
 * - Strategy / Command pattern for handling Redis commands.
 * - For now, only PING is needed.
 * - Later you can add handleSet, handleGet, etc.
 */
public interface CommandHandler {
    void handlePing(SocketChannel clientChannel) throws IOException;
}
