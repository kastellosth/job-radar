package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CareersLinkTest {

    @ParameterizedTest
    @CsvSource({
            "https://apply.workable.com/learnworlds/, WORKABLE, learnworlds",
            "apply.workable.com/LearnWorlds?lng=en, WORKABLE, learnworlds",
            "https://apply.workable.com/learnworlds/#jobs, WORKABLE, learnworlds",
            "https://acme.workable.com, WORKABLE, acme",
            "https://boards.greenhouse.io/kaizengaming, GREENHOUSE, kaizengaming",
            "https://job-boards.greenhouse.io/kaizengaming/jobs/123, GREENHOUSE, kaizengaming",
            "https://jobs.lever.co/acme-inc, LEVER, acme-inc",
            "http://JOBS.LEVER.CO/acme-inc/, LEVER, acme-inc",
            "https://careers.smartrecruiters.com/NetcompanyIntrasoft, SMARTRECRUITERS, NetcompanyIntrasoft",
            "https://jobs.smartrecruiters.com/NetcompanyIntrasoft/744000012345, SMARTRECRUITERS, NetcompanyIntrasoft"
    })
    void recognisesEachBoardAndItsCompany(String link, Provider provider, String slug) {
        assertThat(CareersLink.parse(link)).isEqualTo(new CareersLink(provider, slug));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.linkedin.com/company/acme",
            "https://acme.com/careers",
            "https://apply.workable.com/",
            "https://jobs.lever.co",
            "https://www.workable.com/pricing",
            "mailto:jobs@acme.com",
            "not a link at all",
            " "
    })
    void rejectsLinksItCannotUse(String link) {
        assertThatThrownBy(() -> CareersLink.parse(link)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://apply.workable.com/j/8A47E3A43D", "apply.workable.com/j/8A47E3A43D/apply"})
    void explainsThatALinkToOneJobIsNotEnough(String link) {
        assertThatThrownBy(() -> CareersLink.parse(link)).hasMessageContaining("link to one job");
    }

    private static final String GREECE = "566388c1eb974c42bd9e3da4c2f57d60";

    @Test
    void aWorkdaySearchKeepsItsFiltersAndDropsTheRest() {
        CareersLink link = CareersLink.parse("https://accenture.wd103.myworkdayjobs.com/en-US/AccentureCareers"
                + "?locationCountry=" + GREECE.toUpperCase() + "&source=linkedin&q=java%20developer");

        assertThat(link).isEqualTo(new CareersLink(Provider.WORKDAY,
                "accenture.wd103/AccentureCareers?locationCountry=" + GREECE + "&q=java+developer"));
    }

    @Test
    void theSameWorkdaySearchWithFiltersInAnotherOrderIsTheSameCompany() {
        String athens = "3666ef49a96a01a002d7bf77cf003459";
        CareersLink a = CareersLink.parse("https://acme.wd3.myworkdayjobs.com/Careers?locations=" + athens
                + "&locationCountry=" + GREECE);
        CareersLink b = CareersLink.parse("https://acme.wd3.myworkdayjobs.com/Careers?locationCountry=" + GREECE
                + "&locations=" + athens);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void aWorkdayLinkWithoutFiltersIsAcceptedHereTheBoardDecidesIfItIsTooBig() {
        assertThat(CareersLink.parse("https://pwc.wd3.myworkdayjobs.com/Global_Experienced_Careers").slug())
                .isEqualTo("pwc.wd3/Global_Experienced_Careers");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://acme.wd3.myworkdayjobs.com/",
            "https://acme.wd3.myworkdayjobs.com/en-US",
            "https://myworkdayjobs.com/AcmeCareers"
    })
    void rejectsAWorkdayLinkWithoutItsJobSearch(String link) {
        assertThatThrownBy(() -> CareersLink.parse(link)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explainsThatALinkToOneWorkdayJobIsNotEnough() {
        assertThatThrownBy(() -> CareersLink.parse(
                "https://acme.wd3.myworkdayjobs.com/AcmeCareers/job/Athens/Data-Engineer_R003"))
                .hasMessageContaining("link to one job");
    }

    @ParameterizedTest
    @CsvSource({
            "https://jobs.lever.co/kaizen-gaming, Kaizen Gaming",
            "https://accenture.wd103.myworkdayjobs.com/AccentureCareers, Accenture",
            "https://pwc.wd3.myworkdayjobs.com/Global_Campus_Careers, PWC",
            "https://careers.ey.com/ey/search/?locationsearch=Greece, EY",
            "https://career.kotsovolos.gr/search/?q=&locale=el_GR, Kotsovolos",
            "https://opportunities.vodafone.com/search/?q=&locationsearch=Greece, Vodafone"
    })
    void namesACompanyFromItsLinkUntilItsBoardSaysBetter(String link, String name) {
        assertThat(CareersLink.parse(link).defaultName()).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://careers.ey.com/ey/search/?createNewAlert=false&q=&locationsearch=Greece&local=en_US | careers.ey.com/ey?locationsearch=Greece",
            "https://opportunities.vodafone.com/search/?q=&locationsearch=Greece&startrow=25 | opportunities.vodafone.com?locationsearch=Greece",
            "https://career.kotsovolos.gr/search/?q=&locale=el_GR | career.kotsovolos.gr?locale=el_GR",
            "https://careers.example.com/search/?q=java%20developer&optionsFacetsDD_country=GR | careers.example.com?optionsFacetsDD_country=GR&q=java+developer"
    })
    void aSuccessFactorsSearchKeepsTheFiltersThatChooseJobs(String link, String slug) {
        assertThat(CareersLink.parse(link)).isEqualTo(new CareersLink(Provider.SUCCESSFACTORS, slug));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/search/?q=",
            "https://localhost/search/",
            "https://careers.ey.com/ey/",
            "https://careers.ey.com/a/b/c/search/"
    })
    void aSearchLinkMustBeOnACompanysDomain(String link) {
        assertThatThrownBy(() -> CareersLink.parse(link)).isInstanceOf(IllegalArgumentException.class);
    }
}
