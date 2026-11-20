package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.MatchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DigestMailTest {

    private static Job job(String title, String company, String url, Instant postedAt) {
        JobPosting posting = new JobPosting("workable", "1", company, title, "Athens, Greece", false, url, "",
                postedAt);
        return Job.from(posting, new MatchResult(12, Bucket.STRONG, List.of("+3 java", "+4 title:junior")),
                Instant.parse("2026-11-18T08:00:00Z"));
    }

    @Test
    void listsEachJobWithCompanyPlaceDateScoreAndLink() {
        DigestMail mail = DigestMail.of(List.of(
                job("Junior Java Developer", "Acme", "https://acme.example/jobs/1",
                        Instant.parse("2026-11-15T22:30:00Z"))),
                "https://radar.example");

        assertThat(mail.subject()).isEqualTo("Job Radar: 1 new strong match");
        // Posted 00:30 on the 16th in Athens: the email uses the Athens date, like the dashboard.
        assertThat(mail.text()).contains("- Junior Java Developer",
                "Acme · Athens, Greece · posted 16 Nov 2026 · score 12",
                "https://acme.example/jobs/1", "All jobs: https://radar.example");
        assertThat(mail.html()).contains("<a href=\"https://acme.example/jobs/1\">Junior Java Developer</a>",
                "+3 java, +4 title:junior", "Open the dashboard");
    }

    @Test
    void countsSeveralJobsInTheSubject() {
        DigestMail mail = DigestMail.of(List.of(
                job("A Developer", "Acme", "https://a.example", null),
                job("B Developer", "Acme", "https://b.example", null)), "");

        assertThat(mail.subject()).isEqualTo("Job Radar: 2 new strong matches");
        assertThat(mail.text()).doesNotContain("All jobs:");
    }

    @Test
    void escapesTextFromJobBoardsAndDropsLinksThatAreNotHttp() {
        DigestMail mail = DigestMail.of(List.of(
                job("<script>alert(1)</script> Developer", "A&B", "javascript:alert(1)", null)), "");

        assertThat(mail.html()).doesNotContain("<script>", "javascript:")
                .contains("&lt;script&gt;", "A&amp;B");
        assertThat(mail.text()).doesNotContain("javascript:");
    }

    @Test
    void showsThePayWhenTheAdGivesIt() {
        Job paid = job("Junior Java Developer", "Acme", "https://acme.example/1", null);
        paid.paid(new gr.kastellos.jobradar.domain.Pay(30000, 40000, "EUR",
                gr.kastellos.jobradar.domain.Pay.Period.YEAR, "gross"));
        Job monthly = job("Junior QA Engineer", "Acme", "https://acme.example/2", null);
        monthly.paid(new gr.kastellos.jobradar.domain.Pay(1200, 1200, "EUR",
                gr.kastellos.jobradar.domain.Pay.Period.MONTH, "gross"));

        DigestMail mail = DigestMail.of(List.of(paid, monthly, job("Data Engineer", "Acme", "https://a.example/3", null)), "");

        assertThat(mail.text()).contains("· €30,000–€40,000 a year ·", "· €1,200 a month ·");
        assertThat(mail.text().lines().filter(l -> l.contains("Acme ·") && l.contains("a year")).count()).isEqualTo(1);
    }
}
