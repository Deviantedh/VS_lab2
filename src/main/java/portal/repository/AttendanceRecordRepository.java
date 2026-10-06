package portal.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.AttendanceRecord;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Slice<AttendanceRecord> findAllBy(Pageable pageable);
    Slice<AttendanceRecord> findAllByEmployeeId(Long employeeId, Pageable pageable);
    boolean existsByEmployeeIdAndActualStartIsNotNullAndActualEndIsNull(Long employeeId);
}
