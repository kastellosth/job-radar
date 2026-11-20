package gr.kastellos.jobradar.matching;

import gr.kastellos.jobradar.domain.Pay;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the pay an ad states, in English or Greek: "€30,000 - €40,000 gross per year", "30-40k EUR",
 * "Μικτές μηνιαίες αποδοχές: 1.200 €", "$35,3k - $52k". Pure and deterministic, like the scorer.
 *
 * An amount only counts when it has a currency, a pay word sits close to it (salary, gross, μισθός, αποδοχές...),
 * no benefit word sits right next to it (bonus, meal voucher, επίδομα...), and it is plausible for its period.
 * Missing a stated salary is better than showing a relocation bonus as pay.
 */
public final class PayParser {

    private static final String CUR = "(?:€|eur(?:os?)?\\b|ευρώ|\\$|usd\\b|£|gbp\\b)";
    /** "30.000", "30,000", "1 200" (no-break space), "1.200,50", "35,3", "1200". */
    private static final String NUM = "\\d{1,3}(?:[.,\\u00a0\\u202f]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?";
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<c1>" + CUR + ")?\\s?(?<a>" + NUM + ")\\s?(?<ka>k\\b)?\\s?(?<c2>" + CUR + ")?"
                    + "(?:\\s?(?:-|–|—|to|έως|εως|μέχρι)\\s?(?<c3>" + CUR + ")?\\s?(?<b>" + NUM + ")\\s?(?<kb>k\\b)?"
                    + "\\s?(?<c4>" + CUR + ")?)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PAY_WORD = Pattern.compile(
            "salar|compensation|remuneration|wage|base pay|pay range|pay:|gross|\\bnet\\b|μισθ|αποδοχ|αμοιβ|μικτ|καθαρ|απολαβ",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NOT_PAY = Pattern.compile(
            "bonus|voucher|meal|allowance|budget|relocation|referral|gift|insurance|ticket|card|stipend|"
                    + "επίδομα|επιδομα|κουπόνι|κουπονι|δώρο|δωρο|μπόνους|ασφάλ|ασφαλ",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BIG = Pattern.compile("^\\s?(?:million|mln|m\\b|bn|billion|εκατ)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern HOUR = Pattern.compile("hour|/h\\b|/hr|ώρα|ωρα|ωριαί|ωριαι", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DAY = Pattern.compile("per day|daily|/day|ημερήσ|ημερησ|ημέρα|ημερα", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MONTH = Pattern.compile("month|/mo\\b|μήνα|μηνα|μηνιαί|μηνιαι", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern YEAR = Pattern.compile(
            "year|annual|annum|p\\.\\s?a\\.|/yr|ετήσι|ετησι|ετησίως|χρόνο|χρονο|έτος|ετος",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PayParser() {
    }

    /** The first pay stated in an ad's title or text, or null. */
    public static Pay find(String text) {
        return search(text, true);
    }

    /** A field that is about pay already (Remotive's "salary"): no pay word needed next to the amount. */
    public static Pay fromSalaryField(String text) {
        return search(text, false);
    }

    /** Lever's structured range; interval is e.g. "per-year-salary" or "per-hour-wage". */
    public static Pay of(Number min, Number max, String currency, String interval, String text) {
        if ((min == null && max == null) || currency == null || currency.isBlank()) {
            return null;
        }
        int lo = (min != null ? min : max).intValue();
        int hi = (max != null ? max : min).intValue();
        String i = interval == null ? "" : interval.toLowerCase(Locale.ROOT);
        Pay.Period period = i.contains("hour") ? Pay.Period.HOUR
                : i.contains("day") ? Pay.Period.DAY
                : i.contains("month") ? Pay.Period.MONTH
                : i.contains("year") ? Pay.Period.YEAR
                : guess(hi);
        if (period == null || !plausible(lo, hi, period)) {
            return null;
        }
        return new Pay(lo, hi, currency.toUpperCase(Locale.ROOT), period, text);
    }

    private static Pay search(String text, boolean needPayWord) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            Pay pay = payAt(m, text, needPayWord);
            if (pay != null) {
                return pay;
            }
        }
        return null;
    }

    /** The amount the matcher stands on, if it passes every check in turn; null when one fails. */
    private static Pay payAt(Matcher m, String text, boolean needPayWord) {
        // 1. Money has a currency: "10,000 customers" does not.
        String currency = firstNonNull(m.group("c1"), m.group("c2"), m.group("c3"), m.group("c4"));
        if (currency == null) {
            return null;
        }
        String before = text.substring(Math.max(0, m.start() - 100), m.start());
        String after = text.substring(m.end(), Math.min(text.length(), m.end() + 60));
        // 2. Not "€5 million".
        if (BIG.matcher(after).find()) {
            return null;
        }
        // 3. Not a benefit right next to it: "€500 relocation bonus", "meal vouchers worth €150".
        String near = text.substring(Math.max(0, m.start() - 30), Math.min(text.length(), m.end() + 30));
        if (NOT_PAY.matcher(near).find()) {
            return null;
        }
        // 4. A pay word close by: "salary", "gross", "αποδοχές"... (not needed in a field that is about pay).
        String around = before + m.group() + after.substring(0, Math.min(40, after.length()));
        if (needPayWord && !PAY_WORD.matcher(around).find()) {
            return null;
        }
        // 5. The amounts; in "30-40k" the k after the range is for both ends.
        long lo = number(m.group("a"), m.group("ka") != null);
        long hi = m.group("b") == null ? lo : number(m.group("b"), m.group("kb") != null);
        if (m.group("kb") != null && m.group("ka") == null && lo < 1000) {
            lo *= 1000;
        }
        // 6. A period, said or guessed from the size, and an amount that is plausible for it.
        Pay.Period period = period(after, before);
        if (period == null) {
            period = guess(Math.max(lo, hi));
        }
        if (period == null || !plausible(lo, hi, period)) {
            return null;
        }
        return new Pay((int) lo, (int) hi, currency(currency), period, sentence(text, m.start(), m.end()));
    }

    /** "30.000" and "30,000" are thousands; "35,3" and "12.50" are decimals; then k means thousands. */
    static long number(String raw, boolean thousands) {
        String s = raw.replace("\u00a0", ".").replace("\u202f", ".");
        double value;
        if (s.matches("\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?")) {
            // Groups of three are thousands; one or two digits after the last mark are decimals ("1.200,50").
            String decimals = "";
            String whole = s;
            if (s.matches(".*[.,]\\d{1,2}")) {
                int mark = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
                decimals = s.substring(mark + 1);
                whole = s.substring(0, mark);
            }
            value = Double.parseDouble(whole.replaceAll("[.,]", "") + (decimals.isEmpty() ? "" : "." + decimals));
        } else {
            value = Double.parseDouble(s.replace(',', '.'));
        }
        return Math.round(thousands ? value * 1000 : value);
    }

    private static Pay.Period period(String after, String before) {
        Pay.Period p = periodIn(after);
        return p != null ? p : periodIn(before.substring(Math.max(0, before.length() - 60)));
    }

    private static Pay.Period periodIn(String s) {
        if (HOUR.matcher(s).find()) return Pay.Period.HOUR;
        if (DAY.matcher(s).find()) return Pay.Period.DAY;
        if (MONTH.matcher(s).find()) return Pay.Period.MONTH;
        if (YEAR.matcher(s).find()) return Pay.Period.YEAR;
        return null;
    }

    /** No period word: a yearly salary is five or six figures, a monthly one three or four. */
    private static Pay.Period guess(long max) {
        if (max >= 10_000) return Pay.Period.YEAR;
        if (max >= 300) return Pay.Period.MONTH;
        return null;
    }

    private static boolean plausible(long lo, long hi, Pay.Period period) {
        long min = Math.min(lo, hi);
        long max = Math.max(lo, hi);
        return switch (period) {
            case YEAR -> min >= 5_000 && max <= 600_000;
            case MONTH -> min >= 300 && max <= 40_000;
            case DAY -> min >= 30 && max <= 3_000;
            case HOUR -> min >= 3 && max <= 500;
        };
    }

    private static String currency(String symbol) {
        String s = symbol.toLowerCase(Locale.ROOT);
        if (s.equals("$") || s.startsWith("usd")) return "USD";
        if (s.equals("£") || s.startsWith("gbp")) return "GBP";
        return "EUR";
    }

    /**
     * The sentence around the amount, as written. Ads turned into plain text often lose their full stops; then it
     * is cut at whole words, with "…" where it was cut.
     */
    static String sentence(String text, int start, int end) {
        int limit = Math.max(0, start - 80);
        int from = -1;
        for (int i = start - 1; i >= limit; i--) {
            char c = text.charAt(i);
            if (c == '\n' || ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1)))) {
                from = i + 1;
                break;
            }
        }
        boolean cutBefore = from < 0 && limit > 0;
        if (from < 0) {
            from = limit;
            if (cutBefore) {
                int space = text.indexOf(' ', from);
                from = space >= 0 && space < start ? space + 1 : start;
            }
        }
        int limitAfter = Math.min(text.length(), end + 60);
        int to = -1;
        for (int i = end; i < limitAfter; i++) {
            char c = text.charAt(i);
            if (c == '\n' || ((c == '.' || c == '!' || c == '?') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))))) {
                to = i + (c == '\n' ? 0 : 1);
                break;
            }
        }
        boolean cutAfter = to < 0 && limitAfter < text.length();
        if (to < 0) {
            to = limitAfter;
            if (cutAfter) {
                int space = text.lastIndexOf(' ', to);
                to = space > end ? space : to;
            }
        }
        String s = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return (cutBefore ? "…" : "") + s + (cutAfter ? "…" : "");
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
