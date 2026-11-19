package gr.kastellos.jobradar.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackedCompanyRepository extends JpaRepository<TrackedCompany, Long> {

    List<TrackedCompany> findByProvider(Provider provider);

    List<TrackedCompany> findAllByOrderByNameAsc();

    boolean existsByProviderAndSlugIgnoreCase(Provider provider, String slug);
}
