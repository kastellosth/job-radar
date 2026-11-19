package gr.kastellos.jobradar.source;

import gr.kastellos.jobradar.domain.Provider;
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
}
