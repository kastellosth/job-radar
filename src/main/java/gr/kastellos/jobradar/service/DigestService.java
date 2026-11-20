package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Bucket;
import gr.kastellos.jobradar.domain.Job;
import gr.kastellos.jobradar.domain.JobRepository;
import gr.kastellos.jobradar.domain.JobStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Once a week, an email with the STRONG matches first seen in the last 7 days that you have not acted on yet
 * (still NEW or SEEN) and that a board still lists. No email in a week without any.
 * Off until radar.digest.to is set.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);
    private static final Duration WEEK = Duration.ofDays(7);

    private final JobRepository jobs;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String to;
    private final String from;
    private final String dashboardUrl;
    private final Duration hideUnlistedAfter;

    public DigestService(JobRepository jobs, JavaMailSender mail, Clock clock,
                         @Value("${radar.digest.to:}") String to,
                         @Value("${spring.mail.username:}") String from,
                         @Value("${radar.digest.dashboard-url:}") String dashboardUrl,
                         @Value("${radar.hide-unlisted-after:3d}") Duration hideUnlistedAfter) {
        this.jobs = jobs;
        this.mail = mail;
        this.clock = clock;
        this.to = to;
        this.from = from;
        this.dashboardUrl = dashboardUrl;
        this.hideUnlistedAfter = hideUnlistedAfter;
    }

    /** How many jobs the email listed; 0 means no email was sent. */
    public record DigestReport(int jobs) {
    }

    /** Thrown when the digest is asked for but no recipient is configured. */
    public static class NotConfiguredException extends RuntimeException {
        public NotConfiguredException() {
            super("The weekly email is off: set DIGEST_TO, MAIL_USERNAME and MAIL_PASSWORD");
        }
    }

    @Scheduled(cron = "${radar.digest.schedule:0 30 8 * * MON}", zone = "Europe/Athens")
    public void scheduledSend() {
        if (to.isBlank()) {
            return;
        }
        try {
            log.info("Weekly digest: {}", send());
        } catch (MailException e) {
            log.error("Weekly digest could not be sent: {}", e.getMessage());
        }
    }

    /**
     * @throws NotConfiguredException no recipient is set
     * @throws MailException          the mail server refused or could not be reached
     */
    @Transactional(readOnly = true)
    public DigestReport send() {
        if (to.isBlank()) {
            throw new NotConfiguredException();
        }
        List<Job> matches = newStrongMatches();
        if (matches.isEmpty()) {
            return new DigestReport(0);
        }
        DigestMail digest = DigestMail.of(matches, dashboardUrl);
        mail.send(message(digest));
        return new DigestReport(matches.size());
    }

    List<Job> newStrongMatches() {
        Instant now = clock.instant();
        return jobs.newMatches(Bucket.STRONG, EnumSet.of(JobStatus.NEW, JobStatus.SEEN),
                now.minus(WEEK), now.minus(hideUnlistedAfter));
    }

    private MimeMessage message(DigestMail digest) {
        MimeMessage message = mail.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            if (!from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setSubject(digest.subject());
            helper.setText(digest.text(), digest.html());
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the digest email", e);
        }
        return message;
    }
}
