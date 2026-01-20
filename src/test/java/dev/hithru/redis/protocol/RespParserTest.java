package dev.hithru.redis.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    @Test
    void parsesSimplePingArray() {
        RespParser parser = new RespParser();
        StringBuilder buf = new StringBuilder("*1\r\n$4\r\nPING\r\n");

        List<String> result = parser.tryParseArrayOfBulkStrings(buf);

        assertNotNull(result);
        assertEquals(List.of("PING"), result);
        assertEquals(0, buf.length(), "Buffer should be fully consumed");
    }

    @Test
    void parsesEchoCommandWithArg() {
        RespParser parser = new RespParser();
        StringBuilder buf = new StringBuilder("*2\r\n$4\r\nECHO\r\n$3\r\nhey\r\n");

        List<String> result = parser.tryParseArrayOfBulkStrings(buf);

        assertNotNull(result);
        assertEquals(List.of("ECHO", "hey"), result);
        assertEquals(0, buf.length());
    }

    @Test
    void returnsNullWhenIncomplete() {
        RespParser parser = new RespParser();
        StringBuilder buf = new StringBuilder("*2\r\n$4\r\nECHO\r\n$3\r\nhe"); // incomplete "hey\r\n"

        List<String> result = parser.tryParseArrayOfBulkStrings(buf);

        assertNull(result);
        assertEquals("*2\r\n$4\r\nECHO\r\n$3\r\nhe", buf.toString(), "Buffer should remain unchanged");
    }

    @Test
    void parsesMultipleCommandsFromSingleBuffer() {
        RespParser parser = new RespParser();
        StringBuilder buf = new StringBuilder(
                "*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n"
        );

        List<String> first = parser.tryParseArrayOfBulkStrings(buf);
        assertEquals(List.of("PING"), first);

        List<String> second = parser.tryParseArrayOfBulkStrings(buf);
        assertEquals(List.of("ECHO", "hello"), second);

        assertEquals(0, buf.length());
    }
}
