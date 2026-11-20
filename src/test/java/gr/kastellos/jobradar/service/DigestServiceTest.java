package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobPosting;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import gr.kastellos.jobradar.domain.MatchResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T06:30:00Z");

    private final JobRepository repo = mock(JobRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);

    DigestServiceTest() {
        when(mail.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private DigestService service(String to) {
        return new DigestService(repo, mail, Clock.fixed(NOW, ZoneOffset.UTC), to, "radar@example.com",
                "", Duration.ofDays(3));
    }

    private static Job strong(String title) {
        JobPosting posting = new JobPosting("workable", "1", "Acme", title, "Athens", false, "https://x/" + title,
                "", null);
        return Job.from(posting, new MatchResult(12, Bucket.STRONG, List.of()), NOW.minus(Duration.ofDays(2)));
    }

    @Test
    void asksForStrongJobsOfTheLastWeekThatAreStillListedAndNotActedOn() {
        when(repo.newMatches(any(), any(), any(), any())).thenReturn(List.of());

        service("me@example.com").send();

        verify(repo).newMatches(Bucket.STRONG, EnumSet.of(JobStatus.NEW, JobStatus.SEEN),
                NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofDays(3)));
    }

    @Test
    void sendsOneEmailListingTheMatches() throws Exception {
        when(repo.newMatches(any(), any(), any(), any()))
                .thenReturn(List.of(strong("Junior Java Developer"), strong("Graduate Engineer")));

        DigestService.DigestReport report = service("me@example.com").send();

        assertThat(report.jobs()).isEqualTo(2);
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("Job Radar: 2 new strong matches");
        assertThat(message.getAllRecipients()[0]).hasToString("me@example.com");
        assertThat(message.getFrom()[0]).hasToString("radar@example.com");
    }

    @Test
    void sendsNothingInAWeekWithoutNewMatches() {
        when(repo.newMatches(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(service("me@example.com").send().jobs()).isZero();
        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void withoutARecipientTheScheduleDoesNothingAndASendIsRefused() {
        DigestService off = service("");

        off.scheduledSend();
        verify(repo, never()).newMatches(any(), any(), any(), any());
        assertThatThrownBy(off::send).isInstanceOf(DigestService.NotConfiguredException.class);
    }

    @Test
    void aMailServerFailureOnTheScheduleIsLoggedNotThrown() {
        when(repo.newMatches(any(), any(), any(), any())).thenReturn(List.of(strong("Junior Java Developer")));
        doThrow(new MailSendException("535 Username and Password not accepted"))
                .when(mail).send(any(MimeMessage.class));

        service("me@example.com").scheduledSend();

        verify(mail).send(any(MimeMessage.class));
        verify(repo).newMatches(eq(Bucket.STRONG), any(), any(), any());
    }
}
