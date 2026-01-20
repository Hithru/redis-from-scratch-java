import java.util.ArrayList;
import java.util.List;

/**
 * RespParser
 *
 * Parses RESP2 values. For now we only need:
 * - Arrays of Bulk Strings (commands): ["PING"], ["SET", "foo", "bar"], etc.
 *
 * The main method operates on a StringBuilder buffer and:
 * - Returns a parsed List<String> when a full Array is available.
 * - Removes the consumed bytes from the buffer.
 * - Returns null if there's not enough data yet.
 */
public class RespParser {

    private static final String CRLF = "\r\n";

    /**
     * Tries to parse a single RESP Array of Bulk Strings from the start of inputBuffer.
     *
     * On success:
     *  - returns List<String> representing array elements
     *  - removes consumed text from inputBuffer
     *
     * On incomplete data or invalid syntax:
     *  - returns null and leaves inputBuffer unchanged
     */
    public List<String> tryParseArrayOfBulkStrings(StringBuilder inputBuffer) {
        if (inputBuffer.length() == 0) {
            return null;
        }

        int len = inputBuffer.length();
        int idx = 0;

        // Expect an Array
        if (inputBuffer.charAt(idx) != '*') {
            // For now, commands are always sent as Arrays.
            // If something else appears, we currently ignore it.
            return null;
        }

        // Read "*<count>\r\n"
        int lineEnd = inputBuffer.indexOf(CRLF, idx);
        if (lineEnd == -1) {
            // Not even a full first line yet
            return null;
        }

        String countStr = inputBuffer.substring(idx + 1, lineEnd);
        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            // Invalid RESP; for this toy server, bail out gracefully.
            return null;
        }

        idx = lineEnd + CRLF.length(); // after the array header line

        List<String> elements = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            // Expect "$<len>\r\n"
            if (idx >= len || inputBuffer.charAt(idx) != '$') {
                return null; // incomplete or invalid
            }

            int bulkLenLineEnd = inputBuffer.indexOf(CRLF, idx);
            if (bulkLenLineEnd == -1) {
                return null; // incomplete
            }

            String bulkLenStr = inputBuffer.substring(idx + 1, bulkLenLineEnd);
            int bulkLen;
            try {
                bulkLen = Integer.parseInt(bulkLenStr);
            } catch (NumberFormatException e) {
                return null;
            }

            idx = bulkLenLineEnd + CRLF.length(); // now at start of bulk data

            // Need bulkLen bytes + trailing CRLF
            if (idx + bulkLen + CRLF.length() > len) {
                return null; // incomplete
            }

            String value = inputBuffer.substring(idx, idx + bulkLen);
            elements.add(value);

            idx += bulkLen;

            // Expect trailing CRLF
            if (!inputBuffer.substring(idx, idx + CRLF.length()).equals(CRLF)) {
                return null; // invalid
            }
            idx += CRLF.length();
        }

        // Successfully parsed a full array; remove consumed data
        inputBuffer.delete(0, idx);

        return elements;
    }
}
