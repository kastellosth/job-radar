package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A pasted link, split once into the parts every board looks at. Each board's source class has a
 * {@code static CareersLink link(Link)} that answers null when the link is not its own (see CareersLink).
 *
 * @param host     lower-case host, e.g. "apply.workable.com"
 * @param path     the path's non-empty parts, e.g. ["learnworlds"]
 * @param rawQuery what follows "?", still encoded; null when there is none
 * @param text     the link as pasted, for messages
 */
record Link(String host, List<String> path, String rawQuery, String text) {

    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}");

    /** The first part of the path, where most boards put the company: apply.workable.com/<b>learnworlds</b>. */
    String first() {
        return path.isEmpty() ? null : path.get(0);
    }

    /** A board whose company id is one plain word; lower-cased unless the board's ids are case-sensitive. */
    CareersLink simple(Provider provider, String slug, boolean caseSensitive) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("No company name found in the link: " + text);
        }
        return new CareersLink(provider, caseSensitive ? slug : slug.toLowerCase(Locale.ROOT));
    }
}
