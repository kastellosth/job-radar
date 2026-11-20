package gr.kastellos.jobradar.domain;

import gr.kastellos.jobradar.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The V2 migration on a real PostgreSQL: the starting list of companies and the case-insensitive unique index. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class TrackedCompanyRepositoryIT {

    @Autowired
    private TrackedCompanyRepository companies;

    @Test
    void migrationStartsWithTheCompaniesThatUsedToBeInTheConfig() {
        assertThat(companies.count()).isEqualTo(16);
        assertThat(companies.findByProvider(Provider.GREENHOUSE))
                .extracting(TrackedCompany::getSlug).containsExactly("kaizengaming");
        assertThat(companies.findByProvider(Provider.SMARTRECRUITERS))
                .extracting(TrackedCompany::getSlug).containsExactly("NetcompanyIntrasoft");
    }

    @Test
    void sameCompanyWithOtherCapitalsIsFoundAndCannotBeAddedTwice() {
        assertThat(companies.existsByProviderAndSlugIgnoreCase(Provider.WORKABLE, "LearnWorlds")).isTrue();

        assertThatThrownBy(() -> companies.saveAndFlush(
                TrackedCompany.of(Provider.WORKABLE, "LearnWorlds", "LearnWorlds", Instant.EPOCH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSlugOnAnotherBoardIsADifferentCompany() {
        companies.saveAndFlush(TrackedCompany.of(Provider.LEVER, "learnworlds", "LearnWorlds", Instant.EPOCH));

        assertThat(companies.existsByProviderAndSlugIgnoreCase(Provider.LEVER, "learnworlds")).isTrue();
    }

    @Test
    void aWorkdaySearchCanBeTracked() {
        TrackedCompany saved = companies.saveAndFlush(TrackedCompany.of(Provider.WORKDAY,
                "accenture.wd103/AccentureCareers?locationCountry=566388c1eb974c42bd9e3da4c2f57d60", "Accenture",
                java.time.Instant.parse("2026-11-20T10:00:00Z")));

        assertThat(companies.findByProvider(Provider.WORKDAY)).extracting(TrackedCompany::getId)
                .containsExactly(saved.getId());
    }

    @Test
    void aSuccessFactorsSearchCanBeTracked() {
        companies.saveAndFlush(TrackedCompany.of(Provider.SUCCESSFACTORS, "careers.ey.com/ey?locationsearch=Greece",
                "EY", java.time.Instant.parse("2026-11-20T10:00:00Z")));

        assertThat(companies.findByProvider(Provider.SUCCESSFACTORS)).hasSize(1);
    }
}
