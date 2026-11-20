package gr.kastellos.jobradar.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

/**
 * A posting as it comes out of a source, before it is scored or stored.
 * Every source maps its own JSON into this one shape.
 */
public record JobPosting(
        String source,
        String externalId,
        String company,
        String title,
        String location,
        boolean remote,
        String url,
        String description,
        Instant postedAt,
        Pay pay) {

    /** A posting whose board has no pay field; the pay, if any, is read from its text at ingest. */
    public JobPosting(String source, String externalId, String company, String title, String location,
                      boolean remote, String url, String description, Instant postedAt) {
        this(source, externalId, company, title, location, remote, url, description, postedAt, null);
    }

    /**
     * The same job is often listed on several boards, or reposted with a new id.
     * Company + title + location, normalised, is a good-enough identity for "same job".
     */
    public String fingerprint() {
        return normalise(company) + "|" + normalise(title) + "|" + (remote ? "remote" : normalise(location));
    }

    static String normalise(String s) {
        if (s == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
