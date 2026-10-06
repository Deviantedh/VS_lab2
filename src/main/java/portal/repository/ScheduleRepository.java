package portal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.Schedule;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findAllByBranchId(Long branchId);
/*    List<Schedule> findAllByBranchIdAndDateFromLessThanEqualAndDateToGreaterThanEqual(
            Long branchId, LocalDate dateTo, LocalDate dateFrom
    );*/
}
