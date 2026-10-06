package portal.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import portal.attendance.entity.ReactiveAttendanceRecord;
import portal.attendance.repository.ReactiveAttendanceRepository;
import portal.dto.AttendanceRecordDto;
import portal.dto.SliceResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReactiveAttendanceService {

    private final ReactiveAttendanceRepository repository;

    public Mono<SliceResponse<AttendanceRecordDto.Response>> getSlice(Long employeeId, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        int fetchSize = limit + 1; // +1 to check for hasNext

        Flux<ReactiveAttendanceRecord> source = (employeeId != null)
                ? repository.findByEmployeeId(employeeId, PageRequest.of(page, fetchSize))
                : repository.findAllBy(PageRequest.of(page, fetchSize));

        return source.map(this::toResponseDto)
                .collectList()
                .map(list -> {
                    boolean hasNext = list.size() > limit;
                    var content = hasNext ? list.subList(0, limit) : list;
                    return new SliceResponse<>(content, page, limit, hasNext);
                });
    }

    public Mono<AttendanceRecordDto.Response> getById(Long id) {
        return repository.findById(id)
                .map(this::toResponseDto);
    }

    public Mono<AttendanceRecordDto.Response> create(AttendanceRecordDto.Request request) {
        Instant now = Instant.now();
        ReactiveAttendanceRecord record = ReactiveAttendanceRecord.builder()
                .employeeId(request.getEmployeeId())
                .shiftId(request.getShiftId())
                .plannedStart(request.getPlannedStart())
                .plannedEnd(request.getPlannedEnd())
                .actualStart(request.getActualStart())
                .actualEnd(request.getActualEnd())
                .breakMinutes(request.getBreakMinutes())
                .comment(request.getComment())
                .lateMinutes(0L)
                .overtimeMinutes(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return repository.save(record).map(this::toResponseDto);
    }

    public Mono<AttendanceRecordDto.Response> checkIn(AttendanceRecordDto.CheckInRequest request) {
        Instant now = Instant.now();
        Instant plannedEnd = now.plus(Duration.ofHours(8));

        ReactiveAttendanceRecord record = ReactiveAttendanceRecord.builder()
                .employeeId(request.getEmployeeId())
                .shiftId(request.getShiftId())
                .plannedStart(now)
                .plannedEnd(plannedEnd)
                .actualStart(now)
                .comment(request.getComment())
                .lateMinutes(0L)
                .overtimeMinutes(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return repository.save(record).map(this::toResponseDto);
    }

    public Mono<AttendanceRecordDto.Response> checkOut(Long id, AttendanceRecordDto.CheckOutRequest request) {
        Instant now = Instant.now();
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Запись о явке с ID " + id + " не найдена!")))
                .flatMap(record -> {
                    record.setActualEnd(now);
                    if (request.getBreakMinutes() != null) {
                        record.setBreakMinutes(request.getBreakMinutes());
                    }
                    if (request.getComment() != null) {
                        record.setComment(request.getComment());
                    }
                    if (record.getPlannedEnd() != null && now.isAfter(record.getPlannedEnd())) {
                        record.setOvertimeMinutes(Duration.between(record.getPlannedEnd(), now).toMinutes());
                    }
                    record.setUpdatedAt(now);
                    return repository.save(record);
                })
                .map(this::toResponseDto);
    }

    private AttendanceRecordDto.Response toResponseDto(ReactiveAttendanceRecord entity) {
        return AttendanceRecordDto.Response.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployeeId())
                .shiftId(entity.getShiftId())
                .plannedStart(entity.getPlannedStart())
                .plannedEnd(entity.getPlannedEnd())
                .actualStart(entity.getActualStart())
                .actualEnd(entity.getActualEnd())
                .breakMinutes(entity.getBreakMinutes())
                .comment(entity.getComment())
                .lateMinutes(entity.getLateMinutes() != null ? entity.getLateMinutes() : 0L)
                .overtimeMinutes(entity.getOvertimeMinutes() != null ? entity.getOvertimeMinutes() : 0L)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
