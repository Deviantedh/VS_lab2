package portal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
}
