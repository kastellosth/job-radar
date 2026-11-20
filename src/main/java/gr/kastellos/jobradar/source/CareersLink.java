package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A company's careers page link, turned into the job board and the company's id on it.
 * Examples: apply.workable.com/learnworlds, boards.greenhouse.io/kaizengaming,
 * jobs.lever.co/acme, careers.smartrecruiters.com/NetcompanyIntrasoft,
 * accenture.wd103.myworkdayjobs.com/AccentureCareers?locationCountry=566388c1eb974c42bd9e3da4c2f57d60.
 */
public record CareersLink(Provider provider, String slug) {

    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}");
    private static final Set<String> GREENHOUSE_HOSTS = Set.of("boards.greenhouse.io", "job-boards.greenhouse.io");
    private static final Set<String> SMARTRECRUITERS_HOSTS =
            Set.of("careers.smartrecruiters.com", "jobs.smartrecruiters.com");
    private static final Pattern WORKDAY_HOST = Pattern.compile("([a-z0-9][a-z0-9-]*)\\.(wd\\d{1,3})\\.myworkdayjobs\\.com");
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}-[A-Z]{2}");
    private static final Pattern WORKDAY_SITE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    /** Workday filter values are ids like 566388c1eb974c42bd9e3da4c2f57d60; anything else in the link is ignored. */
    private static final Pattern WORKDAY_FACET_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern WORKDAY_FACET_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,60}");

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
        Matcher workday = WORKDAY_HOST.matcher(host);
        if (workday.matches()) {
            return workday(workday.group(1), workday.group(2), path, uri.getRawQuery(), text);
        }
        throw new IllegalArgumentException(
                "Only Workable, Greenhouse, Lever, SmartRecruiters and Workday careers pages are supported: " + text);
    }

    /**
     * A Workday job search, with the filters he chose on the site (e.g. country Greece) kept in the slug, sorted,
     * so the same search pasted twice is the same company: "accenture.wd103/AccentureCareers?locationCountry=...".
     */
    private static CareersLink workday(String tenant, String instance, List<String> path, String rawQuery,
                                       String text) {
        List<String> rest = path.stream().dropWhile(s -> LOCALE.matcher(s).matches()).toList();
        if (rest.isEmpty() || !WORKDAY_SITE.matcher(rest.get(0)).matches()) {
            throw new IllegalArgumentException("Paste the link of the company's job search on Workday, e.g. "
                    + "https://acme.wd3.myworkdayjobs.com/AcmeCareers?locationCountry=...");
        }
        if (rest.size() > 1 && rest.get(1).equals("job")) {
            throw new IllegalArgumentException("That is a link to one job. Paste the company's job search instead");
        }
        TreeSet<String> filters = new TreeSet<>();
        String search = "";
        if (rawQuery != null) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                if (key.equals("q")) {
                    search = value.length() > 100 ? value.substring(0, 100) : value;
                } else if (WORKDAY_FACET_NAME.matcher(key).matches() && WORKDAY_FACET_ID.matcher(value).matches()) {
                    filters.add(key + "=" + value.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!search.isBlank()) {
            filters.add("q=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        String slug = tenant + "." + instance + "/" + rest.get(0)
                + (filters.isEmpty() ? "" : "?" + filters.stream().collect(Collectors.joining("&")));
        if (slug.length() > 255) {
            throw new IllegalArgumentException("That Workday link has too many filters. Keep only the country");
        }
        return new CareersLink(Provider.WORKDAY, slug);
    }

    /**
     * A first name for the company, before its board says better. "kaizen-gaming" becomes "Kaizen Gaming";
     * a Workday tenant of three letters or fewer is an acronym: "pwc" becomes "PWC".
     */
    public String defaultName() {
        if (provider == Provider.WORKDAY) {
            String tenant = slug.substring(0, slug.indexOf('.'));
            return tenant.length() <= 3 ? tenant.toUpperCase(Locale.ROOT) : nameFromSlug(tenant);
        }
        return nameFromSlug(slug);
    }

    static String nameFromSlug(String slug) {
        return Arrays.stream(slug.split("[-_.]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
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
