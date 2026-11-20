package gr.kastellos.jobradar.matching;

import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.MatchResult;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, deterministic scoring: same posting + same profile = same score.
 * No Spring or I/O in the logic, so it is cheap to unit-test.
 *
 * A job outside the accepted locations, or whose title is not a software role, is skipped with 0 points.
 * Otherwise: score = skill keywords found + title boosts + title penalties + penalties + experience penalty.
 * Every point is recorded as a human-readable reason, so the UI can show why a job ranked where it did.
 */
@Component
public class JobScorer {

    /** "3+ years", "3 years", "minimum of 4 years", "5 χρόνια" ... */
    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*(?:\\+|-\\s*\\d{1,2})?\\s*(?:years?|yrs?|χρόνια|έτη)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final RadarProperties.Profile profile;

    public JobScorer(RadarProperties props) {
        this.profile = props.profile();
    }

    public MatchResult score(JobPosting posting) {
        String title = lower(posting.title());
        String all = title + " " + lower(posting.description());
        List<String> reasons = new ArrayList<>();

        if (!locationAccepted(posting)) {
            reasons.add("location not accepted: " + posting.location());
            return new MatchResult(0, Bucket.SKIP, reasons);
        }
        // Checked before any points: "Junior Talent Acquisition Specialist" must not ride on its "junior" boost.
        if (!softwareRole(title)) {
            reasons.add("not a software role");
            return new MatchResult(0, Bucket.SKIP, reasons);
        }

        int score = 0;
        score += addMatches(profile.skills(), all, "", reasons);
        score += addMatches(profile.titleBoosts(), title, "title:", reasons);
        score += addMatches(profile.titlePenalties(), title, "", reasons);
        score += addMatches(profile.penalties(), all, "", reasons);
        score += experiencePenalty(all, reasons);

        return new MatchResult(score, bucketFor(score), reasons);
    }

    /**
     * Whether the posting can score at all: an accepted location and a software title. Sources whose list has no
     * description use it to fetch the full ad only for these.
     */
    public boolean worthReading(JobPosting posting) {
        return locationAccepted(posting) && softwareRole(lower(posting.title()));
    }

    /** Whether a place is one he accepts, e.g. to list a job's Greek office first among several. */
    public boolean acceptsPlace(String place) {
        String p = lower(place);
        return !p.isBlank() && profile.locations().stream().map(JobScorer::lower).anyMatch(p::contains);
    }

    Bucket bucketFor(int score) {
        if (score >= profile.strongThreshold()) return Bucket.STRONG;
        if (score >= profile.goodThreshold()) return Bucket.GOOD;
        if (score >= profile.stretchThreshold()) return Bucket.STRETCH;
        return Bucket.SKIP;
    }

    private boolean locationAccepted(JobPosting posting) {
        if (posting.remote() || profile.locations().isEmpty()) {
            return true;
        }
        String location = lower(posting.location());
        if (location.isBlank()) {
            return true; // unknown: let him decide rather than silently drop it
        }
        return profile.locations().stream().map(JobScorer::lower).anyMatch(location::contains);
    }

    /** Accents are ignored here: Greek titles in capitals ("ΠΡΟΓΡΑΜΜΑΤΙΣΤΗΣ") carry none. */
    private boolean softwareRole(String title) {
        if (profile.roleKeywords().isEmpty()) {
            return true;
        }
        String plainTitle = withoutAccents(title);
        return profile.roleKeywords().stream()
                .anyMatch(keyword -> containsWord(plainTitle, withoutAccents(lower(keyword))));
    }

    private static String withoutAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static int addMatches(Map<String, Integer> weights, String text, String prefix, List<String> reasons) {
        int total = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            if (containsWord(text, lower(e.getKey()))) {
                total += e.getValue();
                reasons.add(signed(e.getValue()) + " " + prefix + e.getKey());
            }
        }
        return total;
    }

    /**
     * The biggest "N years" number mentioned is a rough proxy for required experience.
     * 0-2 years is fine for him; each year beyond that costs 2 points.
     */
    private static int experiencePenalty(String text, List<String> reasons) {
        int max = 0;
        Matcher m = YEARS.matcher(text);
        while (m.find()) {
            int years = Integer.parseInt(m.group(1));
            if (years <= 15) {
                max = Math.max(max, years);
            }
        }
        if (max <= 2) {
            return 0;
        }
        int penalty = -2 * (max - 2);
        reasons.add(signed(penalty) + " asks for " + max + "+ years");
        return penalty;
    }

    /**
     * Keyword match with word boundaries, so "java" does not match "javascript"
     * and "node.js" / "c#" still work (plain \b fails on '.' and '#').
     */
    static boolean containsWord(String text, String keyword) {
        if (keyword.isBlank()) {
            return false;
        }
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}])");
        return p.matcher(text).find();
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }
}
