package gr.kastellos.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Everything that changes per person lives in application.yml, not in code:
 * which company boards to poll, and how to score a posting.
 */
@ConfigurationProperties(prefix = "radar")
public record RadarProperties(Sources sources, Profile profile) {

    /**
     * Company identifiers ("slugs") per job-board provider.
     *
     * @param remotive whether to read Remotive's remote software jobs (one call per run)
     */
    public record Sources(
            List<String> workable,
            List<String> smartrecruiters,
            List<String> greenhouse,
            List<String> lever,
            boolean remotive) {

        public Sources {
            workable = orEmpty(workable);
            smartrecruiters = orEmpty(smartrecruiters);
            greenhouse = orEmpty(greenhouse);
            lever = orEmpty(lever);
        }
    }

    /**
     * Scoring profile.
     *
     * @param skills          keyword -> points when it appears anywhere in title or description
     * @param titleBoosts     keyword -> points when it appears in the title (e.g. "junior")
     * @param penalties       keyword -> points (negative) when it appears, e.g. "senior", "5+ years"
     * @param locations       locations he accepts; a posting elsewhere (and not remote) is dropped
     * @param roleKeywords    a title must contain one of these to count as a software job; empty = no filter
     * @param strongThreshold score at or above which a job is a strong match
     * @param goodThreshold   score at or above which a job is a good match; below that it is a stretch or skipped
     */
    public record Profile(
            Map<String, Integer> skills,
            Map<String, Integer> titleBoosts,
            Map<String, Integer> penalties,
            List<String> locations,
            List<String> roleKeywords,
            int strongThreshold,
            int goodThreshold,
            int stretchThreshold) {

        public Profile {
            skills = skills == null ? Map.of() : skills;
            titleBoosts = titleBoosts == null ? Map.of() : titleBoosts;
            penalties = penalties == null ? Map.of() : penalties;
            locations = orEmpty(locations);
            roleKeywords = orEmpty(roleKeywords);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
