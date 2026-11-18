package gr.kastellos.jobradar.matching;

import gr.kastellos.jobradar.config.RadarProperties;
import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.MatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobScorerTest {

    private final JobScorer scorer = new JobScorer(new RadarProperties(
            new RadarProperties.Sources(null, null, null, null, null),
            new RadarProperties.Profile(
                    Map.of("java", 3, "spring boot", 3, "react", 2, "node.js", 1, "c#", 1),
                    Map.of("junior", 4, "graduate", 4),
                    Map.of("senior", -8, "german", -6),
                    List.of("greece", "athens", "thessaloniki", "heraklion"),
                    9, 5, 2)));

    private static JobPosting posting(String title, String location, boolean remote, String description) {
        return new JobPosting("test", "1", "Acme", title, location, remote, "https://example.com/1", description, null);
    }

    @Test
    void juniorJavaSpringRoleInAthensIsStrong() {
        MatchResult r = scorer.score(posting("Junior Java Developer", "Athens, Greece", false,
                "We use Java 21 and Spring Boot. 0-2 years of experience."));

        assertThat(r.score()).isEqualTo(3 + 3 + 4);
        assertThat(r.bucket()).isEqualTo(Bucket.STRONG);
        assertThat(r.reasons()).contains("+3 java", "+3 spring boot", "+4 title:junior");
    }

    @Test
    void seniorRoleIsPushedOut() {
        MatchResult r = scorer.score(posting("Senior Java Engineer", "Athens", false,
                "Java, Spring Boot, 7+ years of experience."));

        assertThat(r.bucket()).isEqualTo(Bucket.SKIP);
        assertThat(r.reasons()).contains("-8 senior", "-10 asks for 7+ years");
    }

    @Test
    void javaDoesNotMatchJavascript() {
        MatchResult r = scorer.score(posting("Frontend Developer", "Athens", false, "JavaScript and CSS."));

        assertThat(r.reasons()).noneMatch(reason -> reason.contains("java"));
    }

    @Test
    void keywordsWithPunctuationStillMatch() {
        assertThat(JobScorer.containsWord("we use node.js and c# daily", "node.js")).isTrue();
        assertThat(JobScorer.containsWord("we use node.js and c# daily", "c#")).isTrue();
        assertThat(JobScorer.containsWord("javascript", "java")).isFalse();
    }

    @Test
    void jobOutsideAcceptedLocationsIsSkippedUnlessRemote() {
        JobPosting berlin = posting("Junior Java Developer", "Berlin, Germany", false, "Java");
        JobPosting remoteBerlin = posting("Junior Java Developer", "Berlin, Germany", true, "Java");

        assertThat(scorer.score(berlin).bucket()).isEqualTo(Bucket.SKIP);
        assertThat(scorer.score(remoteBerlin).bucket()).isNotEqualTo(Bucket.SKIP);
    }

    @Test
    void unknownLocationIsKeptRatherThanSilentlyDropped() {
        MatchResult r = scorer.score(posting("Junior Java Developer", null, false, "Java"));

        assertThat(r.bucket()).isNotEqualTo(Bucket.SKIP);
    }

    @ParameterizedTest
    @CsvSource({
            "'0-2 years of experience', 0",
            "'at least 2 years', 0",
            "'3+ years of experience', -2",
            "'minimum 5 years', -6",
            "'3-5 years', -2",
            "'5 χρόνια εμπειρίας', -6"
    })
    void experienceRequirementCostsTwoPointsPerYearAboveTwo(String text, int expected) {
        MatchResult r = scorer.score(posting("Developer", "Athens", false, text));

        assertThat(r.score()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"9, STRONG", "12, STRONG", "5, GOOD", "8, GOOD", "2, STRETCH", "4, STRETCH", "1, SKIP", "-5, SKIP"})
    void bucketsFollowThresholds(int score, Bucket expected) {
        assertThat(scorer.bucketFor(score)).isEqualTo(expected);
    }
}
