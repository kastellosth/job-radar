package gr.kastellos.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * How to score a posting lives in application.yml, not in code, so tuning needs no rebuild.
 * Which companies to read is data, not configuration: see TrackedCompany.
 */
@ConfigurationProperties(prefix = "radar")
public record RadarProperties(Sources sources, Profile profile) {

    /** @param remotive whether to read Remotive's remote software jobs (one call per run) */
    public record Sources(boolean remotive) {
    }

    /**
     * Scoring profile.
     *
     * @param skills          keyword -> points when it appears anywhere in title or description
     * @param titleBoosts     keyword -> points when it appears in the title (e.g. "junior")
     * @param titlePenalties  keyword -> points (negative) when it is in the title, e.g. "senior"; only the title,
     *                        because ads often say "you will report to the engineering manager"
     * @param penalties       keyword -> points (negative) when it appears anywhere, e.g. "german"
     * @param locations       locations you accept; a posting elsewhere (and not remote) is dropped
     * @param roleKeywords    a title must contain one of these to count as a software job; empty = no filter
     * @param strongThreshold score at or above which a job is a strong match
     * @param goodThreshold   score at or above which a job is a good match; below that it is a stretch or skipped
     */
    public record Profile(
            Map<String, Integer> skills,
            Map<String, Integer> titleBoosts,
            Map<String, Integer> titlePenalties,
            Map<String, Integer> penalties,
            List<String> locations,
            List<String> roleKeywords,
            int strongThreshold,
            int goodThreshold,
            int stretchThreshold) {

        public Profile {
            skills = skills == null ? Map.of() : skills;
            titleBoosts = titleBoosts == null ? Map.of() : titleBoosts;
            titlePenalties = titlePenalties == null ? Map.of() : titlePenalties;
            penalties = penalties == null ? Map.of() : penalties;
            locations = orEmpty(locations);
            roleKeywords = orEmpty(roleKeywords);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
