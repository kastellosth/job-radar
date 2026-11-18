package gr.kastellos.jobradar.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingTest {

    private static JobPosting posting(String source, String company, String title, String location, boolean remote) {
        return new JobPosting(source, "x", company, title, location, remote, "https://x", "", null);
    }

    @Test
    void sameJobFromTwoBoardsHasSameFingerprint() {
        JobPosting a = posting("workable", "Acme S.A.", "Junior Java Developer", "Athens, Greece", false);
        JobPosting b = posting("linkedin", "acme s.a", "Junior  Java Developer ", "athens greece", false);

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void greekAccentsAreIgnored() {
        JobPosting a = posting("s", "Acme", "Developer", "Ηράκλειο", false);
        JobPosting b = posting("s", "Acme", "Developer", "Ηρακλειο", false);

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void differentCitiesAreDifferentJobs() {
        JobPosting athens = posting("s", "Acme", "Junior Developer", "Athens", false);
        JobPosting patras = posting("s", "Acme", "Junior Developer", "Patras", false);

        assertThat(athens.fingerprint()).isNotEqualTo(patras.fingerprint());
    }

    @Test
    void remoteJobsIgnoreTheLocationText() {
        JobPosting a = posting("s", "Acme", "Junior Developer", "Remote - Europe", true);
        JobPosting b = posting("s", "Acme", "Junior Developer", "Anywhere in EU", true);

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }
}
