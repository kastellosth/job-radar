package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A company's careers page link, turned into the job board and the company's id on it ("slug").
 * Examples: apply.workable.com/learnworlds, boards.greenhouse.io/kaizengaming,
 * jobs.lever.co/acme, careers.smartrecruiters.com/NetcompanyIntrasoft,
 * accenture.wd103.myworkdayjobs.com/AccentureCareers?locationCountry=566388c1eb974c42bd9e3da4c2f57d60,
 * careers.ey.com/ey/search/?q=&locationsearch=Greece (SuccessFactors, on the company's own domain).
 *
 * Each board's source class knows its own links ({@code static CareersLink link(Link)}); this class splits the
 * link once and asks them in turn. A new board is one more entry in {@link #BOARDS}.
 */
public record CareersLink(Provider provider, String slug) {

    /** Asked in this order; SuccessFactors is last because it is recognised by the shape of any domain's link. */
    private static final List<Function<Link, CareersLink>> BOARDS = List.of(
            WorkableSource::link,
            GreenhouseSource::link,
            LeverSource::link,
            SmartRecruitersSource::link,
            WorkdaySource::link,
            SuccessFactorsSource::link);

    /** @throws IllegalArgumentException with a message that can be shown to the user */
    public static CareersLink parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paste the link to the company's careers page");
        }
        String trimmed = text.trim();
        URI uri = toUri(trimmed.contains("://") ? trimmed : "https://" + trimmed);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String rawPath = uri.getPath() == null ? "" : uri.getPath();
        Link link = new Link(host, Arrays.stream(rawPath.split("/")).filter(s -> !s.isBlank()).toList(),
                uri.getRawQuery(), text);
        for (Function<Link, CareersLink> board : BOARDS) {
            CareersLink found = board.apply(link);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalArgumentException("Only Workable, Greenhouse, Lever, SmartRecruiters and Workday careers"
                + " pages, and SuccessFactors job searches (a link with /search/), are supported: " + text);
    }

    /**
     * A first name for the company, before its board says better: "kaizen-gaming" becomes "Kaizen Gaming".
     * Boards whose slug is a search rather than a name derive it their own way.
     */
    public String defaultName() {
        return switch (provider) {
            case WORKDAY -> WorkdaySource.nameFor(slug);
            case SUCCESSFACTORS -> SuccessFactorsSource.nameFor(slug);
            default -> nameFromSlug(slug);
        };
    }

    /** A word of three letters or fewer is an acronym: "pwc" becomes "PWC", "ey" becomes "EY". */
    static String acronymOrName(String word) {
        return word.length() <= 3 ? word.toUpperCase(Locale.ROOT) : nameFromSlug(word);
    }

    static String nameFromSlug(String slug) {
        return Arrays.stream(slug.split("[-_.]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static URI toUri(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid link: " + text);
        }
    }
}
