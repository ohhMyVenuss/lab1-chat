package vn.ltm.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.StringJoiner;

/** Length-safe text framing for the initial Chat TCP/UDP implementation. */
public final class ChatWire {
    private ChatWire() { }

    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static String frame(String command, String... fields) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(command);
        for (String field : fields) joiner.add(encode(field));
        return joiner.toString();
    }

    public static String[] split(String frame) {
        return frame.split("\\|", -1);
    }

    public static String field(String[] parts, int index) {
        if (index < 0 || index >= parts.length) return "";
        return decode(parts[index]);
    }
}
