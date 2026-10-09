package portal.attendance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import portal.attendance.entity.ReactiveAttendanceRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveAttendanceRepository extends R2dbcRepository<ReactiveAttendanceRecord, Long> {  //Разобраться

    Flux<ReactiveAttendanceRecord> findByEmployeeId(Long employeeId, Pageable pageable);

    Flux<ReactiveAttendanceRecord> findByShiftId(Long shiftId);

    Flux<ReactiveAttendanceRecord> findAllBy(Pageable pageable);

    Mono<ReactiveAttendanceRecord> findByShiftIdAndEmployeeId(Long shiftId, Long employeeId);
}
