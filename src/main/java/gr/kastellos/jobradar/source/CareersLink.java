package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A company's careers page link, turned into the job board and the company's id on it.
 * Examples: apply.workable.com/learnworlds, boards.greenhouse.io/kaizengaming,
 * jobs.lever.co/acme, careers.smartrecruiters.com/NetcompanyIntrasoft.
 */
public record CareersLink(Provider provider, String slug) {

    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}");
    private static final Set<String> GREENHOUSE_HOSTS = Set.of("boards.greenhouse.io", "job-boards.greenhouse.io");
    private static final Set<String> SMARTRECRUITERS_HOSTS =
            Set.of("careers.smartrecruiters.com", "jobs.smartrecruiters.com");

    /** @throws IllegalArgumentException with a message that can be shown to the user */
    public static CareersLink parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paste the link to the company's careers page");
        }
        String trimmed = text.trim();
        URI uri = toUri(trimmed.contains("://") ? trimmed : "https://" + trimmed);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String rawPath = uri.getPath() == null ? "" : uri.getPath();
        List<String> path = Arrays.stream(rawPath.split("/")).filter(s -> !s.isBlank()).toList();
        String first = path.isEmpty() ? null : path.get(0);

        if (host.equals("apply.workable.com")) {
            if ("j".equals(first)) {
                throw new IllegalArgumentException("That is a link to one job. Paste the company's careers page instead");
            }
            return of(Provider.WORKABLE, first, text);
        }
        if (host.endsWith(".workable.com") && !host.equals("www.workable.com")) {
            return of(Provider.WORKABLE, host.substring(0, host.indexOf('.')), text);
        }
        if (GREENHOUSE_HOSTS.contains(host)) {
            return of(Provider.GREENHOUSE, first, text);
        }
        if (host.equals("jobs.lever.co")) {
            return of(Provider.LEVER, first, text);
        }
        if (SMARTRECRUITERS_HOSTS.contains(host)) {
            return of(Provider.SMARTRECRUITERS, first, text);
        }
        throw new IllegalArgumentException(
                "Only Workable, Greenhouse, Lever and SmartRecruiters careers pages are supported: " + text);
    }

    private static CareersLink of(Provider provider, String slug, String text) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("No company name found in the link: " + text);
        }
        // SmartRecruiters ids are case-sensitive (NetcompanyIntrasoft); the others are lower-case.
        return new CareersLink(provider,
                provider == Provider.SMARTRECRUITERS ? slug : slug.toLowerCase(Locale.ROOT));
    }

    private static URI toUri(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid link: " + text);
        }
    }
}
