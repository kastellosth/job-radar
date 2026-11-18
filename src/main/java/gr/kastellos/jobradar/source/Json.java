package gr.kastellos.jobradar.source;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Small null-safe helpers so each source's mapping code stays readable. */
final class Json {

    private Json() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** Job descriptions arrive as HTML (sometimes HTML-escaped HTML). Scoring wants plain text. */
    static String plain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String unescaped = Jsoup.parse(html).text();
        return unescaped.contains("<") ? Jsoup.parse(unescaped).text() : unescaped;
    }

    /** Providers use ISO dates, ISO date-times or epoch millis. */
    static Instant instant(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return Instant.ofEpochMilli(value.asLong());
        }
        String s = value.asText();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Instant.parse(s.endsWith("Z") ? s : s + "Z");
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static boolean looksRemote(String... texts) {
        for (String t : texts) {
            if (t != null && t.toLowerCase().contains("remote")) {
                return true;
            }
        }
        return false;
    }
}
