package gr.kastellos.jobradar.source;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The "key=value&key=value" part of a link, decoded. Pairs without a key are left out. */
final class QueryString {

    record Param(String key, String value) {
    }

    private QueryString() {
    }

    static List<Param> parse(String rawQuery) {
        List<Param> params = new ArrayList<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            params.add(new Param(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim()));
        }
        return params;
    }
}
