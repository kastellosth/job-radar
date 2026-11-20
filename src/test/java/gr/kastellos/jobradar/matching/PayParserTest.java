package gr.kastellos.jobradar.matching;

import gr.kastellos.jobradar.domain.Pay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static gr.kastellos.jobradar.domain.Pay.Period.HOUR;
import static gr.kastellos.jobradar.domain.Pay.Period.MONTH;
import static gr.kastellos.jobradar.domain.Pay.Period.YEAR;
import static org.assertj.core.api.Assertions.assertThat;

class PayParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "We offer a gross annual salary of €30,000 - €40,000. | 30000 | 40000 | EUR | YEAR",
            "Salary: 30-40k EUR per year | 30000 | 40000 | EUR | YEAR",
            "Salary range 30.000 – 40.000 € gross | 30000 | 40000 | EUR | YEAR",
            "Μικτές μηνιαίες αποδοχές: 1.200 - 1.500 € | 1200 | 1500 | EUR | MONTH",
            "Μισθός 1.400€ μικτά | 1400 | 1400 | EUR | MONTH",
            "Ετήσιες μικτές αποδοχές έως 28.000 ευρώ | 28000 | 28000 | EUR | YEAR",
            "The annual base salary range for this role is $126,000—$148,000 USD. | 126000 | 148000 | USD | YEAR",
            "Compensation: £45k | 45000 | 45000 | GBP | YEAR",
            "Hourly wage: €15/hour | 15 | 15 | EUR | HOUR",
            "Net salary from 1.100 EUR per month, 14 salaries | 1100 | 1100 | EUR | MONTH"
    })
    void findsThePayAnAdStates(String text, int min, int max, String currency, Pay.Period period) {
        Pay pay = PayParser.find(text);

        assertThat(pay).isNotNull();
        assertThat(pay.min()).isEqualTo(min);
        assertThat(pay.max()).isEqualTo(max);
        assertThat(pay.currency()).isEqualTo(currency);
        assertThat(pay.period()).isEqualTo(period);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Competitive salary plus a €500 relocation bonus.",
            "Meal vouchers worth €150 per month and a competitive salary.",
            "We raised €5 million and pay a competitive salary.",
            "Java 21, 3+ years, a platform used by 10,000 customers.",
            "Revenue of €40,000 per client in our first year.",
            "Salary review every year since 2026.",
            "Private health insurance (€2,000 per year) on top of your gross salary.",
            "Competitive salary"
    })
    void leavesOutAmountsThatAreNotPay(String text) {
        assertThat(PayParser.find(text)).isNull();
    }

    @Test
    void keepsTheSentenceItCameFromSoGrossOrNetCanBeRead() {
        Pay pay = PayParser.find("About the role. We offer a gross annual salary of €30,000 - €40,000. Hybrid work.");

        assertThat(pay.text()).isEqualTo("We offer a gross annual salary of €30,000 - €40,000.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "$170k - $200k | 170000 | 200000 | YEAR",
            "$35,3k- $52k | 35300 | 52000 | YEAR",
            "$14/hour | 14 | 14 | HOUR"
    })
    void readsRemotivesSalaryFieldWithoutNeedingAPayWord(String field, int min, int max, Pay.Period period) {
        Pay pay = PayParser.fromSalaryField(field);

        assertThat(pay.min()).isEqualTo(min);
        assertThat(pay.max()).isEqualTo(max);
        assertThat(pay.currency()).isEqualTo("USD");
        assertThat(pay.period()).isEqualTo(period);
    }

    @Test
    void anEmptySalaryFieldIsNoPay() {
        assertThat(PayParser.fromSalaryField("")).isNull();
        assertThat(PayParser.fromSalaryField(null)).isNull();
    }

    @Test
    void takesLeversStructuredRange() {
        assertThat(PayParser.of(126000, 148000, "usd", "per-year-salary", ""))
                .isEqualTo(new Pay(126000, 148000, "USD", YEAR, ""));
        assertThat(PayParser.of(20, 25, "EUR", "per-hour-wage", "").period()).isEqualTo(HOUR);
        assertThat(PayParser.of(1500, null, "EUR", "per-month-salary", "")).isEqualTo(new Pay(1500, 1500, "EUR", MONTH, ""));
        assertThat(PayParser.of(null, null, "EUR", "per-year-salary", "")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "30.000, false, 30000",
            "'30,000', false, 30000",
            "'1.200,50', false, 1201",
            "'35,3', true, 35300",
            "1200, false, 1200",
            "45, true, 45000"
    })
    void readsNumbersWrittenEitherWay(String raw, boolean thousands, long expected) {
        assertThat(PayParser.number(raw, thousands)).isEqualTo(expected);
    }

    @Test
    void cutsAnAdWithoutFullStopsAtWholeWords() {
        Pay pay = PayParser.find("Benefits What's it like to work at Skroutz Last Mile Competitive full-time salary"
                + " (€30,800-€42,000 gross annually) Working with top-notch and fun people every single day of the week");

        assertThat(pay.min()).isEqualTo(30800);
        assertThat(pay.text()).startsWith("…").doesNotContain("efits What").contains("(€30,800-€42,000 gross annually)")
                .endsWith("…");
        assertThat(pay.text().substring(1, 2)).as("starts at a whole word").isNotBlank();
    }
}
