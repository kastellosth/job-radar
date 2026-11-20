package gr.kastellos.jobradar.service;

import gr.kastellos.jobradar.domain.Job;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The weekly email, as plain text and HTML. Pure: no Spring, no mail server, so it is cheap to test.
 * Job titles and company names come from outside job boards, so they are escaped and only http(s) links are kept.
 */
public record DigestMail(String subject, String text, String html) {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("Europe/Athens"));

    /** @param dashboardUrl link at the bottom of the email; blank leaves it out */
    public static DigestMail of(List<Job> jobs, String dashboardUrl) {
        String subject = jobs.size() == 1
                ? "Job Radar: 1 new strong match"
                : "Job Radar: " + jobs.size() + " new strong matches";

        StringBuilder text = new StringBuilder("New strong matches this week, best first:\n\n");
        StringBuilder html = new StringBuilder(
                "<p>New strong matches this week, best first:</p>\n<ol>\n");
        for (Job job : jobs) {
            String where = job.isRemote() ? "Remote" : nullToEmpty(job.getLocation());
            String when = DAY.format(job.getPostedAt() != null ? job.getPostedAt() : job.getFirstSeenAt());
            String pay = pay(job);
            String line = job.getCompany() + " · " + where + " · posted " + when
                    + (pay == null ? "" : " · " + pay) + " · score " + job.getScore();

            text.append("- ").append(job.getTitle()).append('\n')
                    .append("  ").append(line).append('\n');
            String link = safeLink(job.getUrl());
            if (link != null) {
                text.append("  ").append(link).append('\n');
            }
            text.append('\n');

            String title = HtmlUtils.htmlEscape(job.getTitle());
            html.append("<li><p>")
                    .append(link != null ? "<a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + title + "</a>" : title)
                    .append("<br>").append(HtmlUtils.htmlEscape(line));
            if (job.getReasons() != null && !job.getReasons().isBlank()) {
                html.append("<br><small>").append(HtmlUtils.htmlEscape(job.getReasons())).append("</small>");
            }
            html.append("</p></li>\n");
        }
        html.append("</ol>\n");

        String dashboard = safeLink(dashboardUrl);
        if (dashboard != null) {
            text.append("All jobs: ").append(dashboard).append('\n');
            html.append("<p><a href=\"").append(HtmlUtils.htmlEscape(dashboard)).append("\">Open the dashboard</a></p>\n");
        }
        return new DigestMail(subject, text.toString(), html.toString());
    }

    /** "€30,000–€40,000 a year", as on the dashboard; null when the ad gives no pay. */
    static String pay(Job job) {
        if (job.getPayMin() == null || job.getPayPeriod() == null) {
            return null;
        }
        String symbol = switch (job.getPayCurrency()) {
            case "EUR" -> "€";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> job.getPayCurrency() + " ";
        };
        String min = symbol + String.format(Locale.UK, "%,d", job.getPayMin());
        String range = job.getPayMax() == null || job.getPayMax().equals(job.getPayMin())
                ? min : min + "–" + symbol + String.format(Locale.UK, "%,d", job.getPayMax());
        String per = switch (job.getPayPeriod()) {
            case YEAR -> "a year";
            case MONTH -> "a month";
            case DAY -> "a day";
            case HOUR -> "an hour";
        };
        return range + " " + per;
    }

    /** Only http(s): a job board must not be able to put a "javascript:" link in the email. */
    static String safeLink(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String scheme = URI.create(url.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) ? url.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
