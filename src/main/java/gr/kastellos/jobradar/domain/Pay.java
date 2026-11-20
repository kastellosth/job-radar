package gr.kastellos.jobradar.domain;

/**
 * What a job pays, as its ad or board states it: never converted, so "€1,200 a month gross" stays a month.
 *
 * @param min      lowest amount, in whole units of the currency; equal to max when the ad gives one amount
 * @param max      highest amount
 * @param currency ISO code: EUR, USD, GBP
 * @param period   what the amount is for
 * @param text     the sentence it came from, so you can read it as written (gross or net, 14 salaries...)
 */
public record Pay(int min, int max, String currency, Period period, String text) {

    public enum Period {
        YEAR, MONTH, DAY, HOUR
    }

    public Pay {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        text = text == null ? "" : text.length() <= 200 ? text : text.substring(0, 199) + "…";
    }
}
