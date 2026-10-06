package portal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.ShiftEmployeeLog;

import java.util.List;

@Repository
public interface ShiftEmployeeLogRepository extends JpaRepository<ShiftEmployeeLog, Long> {
    List<ShiftEmployeeLog> findAllByShiftIdOrderByCreatedAtDesc(Long shiftId);
}
