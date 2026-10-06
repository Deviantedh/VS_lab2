package portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portal.dto.AttendanceRecordDto;
import portal.dto.SliceResponse;
import portal.entity.AttendanceRecord;
import portal.entity.Employee;
import portal.entity.EmployeeStatus;
import portal.entity.Shift;
import portal.exception.BusinessConflictException;
import portal.exception.ResourceNotFoundException;
import portal.repository.AttendanceRecordRepository;
import portal.repository.EmployeeAbsenceRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final ShiftService shiftService;
    private final EmployeeAbsenceRepository absenceRepository;

    public AttendanceService(AttendanceRecordRepository attendanceRepository,
                             EmployeeService employeeService,
                             ShiftService shiftService) {
        this(attendanceRepository, employeeService, shiftService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AttendanceService(AttendanceRecordRepository attendanceRepository,
                             EmployeeService employeeService,
                             ShiftService shiftService,
                             EmployeeAbsenceRepository absenceRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
        this.shiftService = shiftService;
        this.absenceRepository = absenceRepository;
    }

    /**
     * Прокрутка страниц данных (Slice без подсчета общего количества).
     */
    @Transactional(readOnly = true)
    public SliceResponse<AttendanceRecordDto.Response> getAllSliced(Pageable pageable) {
        Slice<AttendanceRecord> slice = attendanceRepository.findAllBy(pageable);
        return SliceResponse.<AttendanceRecordDto.Response>builder()
                .content(slice.getContent().stream().map(this::toResponse).toList())
                .pageNumber(slice.getNumber())
                .pageSize(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public SliceResponse<AttendanceRecordDto.Response> getByEmployeeSliced(Long employeeId, Pageable pageable) {
        employeeService.findEmployeeById(employeeId);
        Slice<AttendanceRecord> slice = attendanceRepository.findAllByEmployeeId(employeeId, pageable);
        return SliceResponse.<AttendanceRecordDto.Response>builder()
                .content(slice.getContent().stream().map(this::toResponse).toList())
                .pageNumber(slice.getNumber())
                .pageSize(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public AttendanceRecordDto.Response getById(Long id) {
        return toResponse(findAttendanceById(id));
    }

    @Transactional
    public AttendanceRecordDto.Response create(AttendanceRecordDto.Request request) {
        if (!request.getPlannedStart().isBefore(request.getPlannedEnd())) {
            throw new BusinessConflictException("Плановое время начала должно быть строго раньше окончания");
        }

        Employee employee = employeeService.findEmployeeById(request.getEmployeeId());
        Shift shift = request.getShiftId() != null ? shiftService.findShiftById(request.getShiftId()) : null;

        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .shift(shift)
                .plannedStart(request.getPlannedStart())
                .plannedEnd(request.getPlannedEnd())
                .actualStart(request.getActualStart())
                .actualEnd(request.getActualEnd())
                .breakMinutes(request.getBreakMinutes() != null ? request.getBreakMinutes() : 0)
                .comment(request.getComment())
                .build();

        return toResponse(attendanceRepository.save(record));
    }

    /**
     * Фиксация явки (Check-In) сотрудника на смену прямо сейчас.
     */
    @Transactional
    public AttendanceRecordDto.Response checkIn(AttendanceRecordDto.CheckInRequest request) {
        Employee employee = employeeService.findEmployeeById(request.getEmployeeId());

        if (employee.getStatus() == EmployeeStatus.DISMISSED) {
            throw new BusinessConflictException("Уволенный сотрудник не может выходить на смену");
        }

        LocalDate today = LocalDate.now();
        if (absenceRepository != null && !absenceRepository.findOverlappingAbsences(employee.getId(), today, today).isEmpty()) {
            throw new BusinessConflictException("Сотрудник находится в подтверждённом отпуске или на больничном. Выход на смену заблокирован");
        }

        if (attendanceRepository.existsByEmployeeIdAndActualStartIsNotNullAndActualEndIsNull(employee.getId())) {
            throw new BusinessConflictException("У сотрудника " + employee.getName() + " уже есть активная открытая смена. Сначала завершите её (Check-Out).");
        }

        Shift shift = null;
        Instant now = Instant.now();
        Instant plannedStart = now;
        Instant plannedEnd = now.plus(Duration.ofHours(8));

        if (request.getShiftId() != null) {
            shift = shiftService.findShiftById(request.getShiftId());
            boolean isAssigned = shift.getEmployees() != null &&
                    shift.getEmployees().stream().anyMatch(e -> e.getId().equals(employee.getId()));
            if (!isAssigned) {
                throw new BusinessConflictException("Сотрудник " + employee.getName() + " не назначен на смену #" + shift.getId());
            }

            LocalDateTime localStart = LocalDateTime.of(shift.getDate(), shift.getTimeFrom());
            plannedStart = localStart.atZone(ZoneId.systemDefault()).toInstant();

            LocalDateTime localEnd = LocalDateTime.of(shift.getDate(), shift.getTimeTo());
            if (shift.getTimeTo().isBefore(shift.getTimeFrom())) {
                localEnd = localEnd.plusDays(1);
            }
            plannedEnd = localEnd.atZone(ZoneId.systemDefault()).toInstant();
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .employee(employee)
                .shift(shift)
                .plannedStart(plannedStart)
                .plannedEnd(plannedEnd)
                .actualStart(now)
                .comment(request.getComment())
                .build();

        return toResponse(attendanceRepository.save(record));
    }

    /**
     * Фиксация ухода (Check-Out) сотрудника.
     */
    @Transactional
    public AttendanceRecordDto.Response checkOut(Long id, AttendanceRecordDto.CheckOutRequest request) {
        AttendanceRecord record = findAttendanceById(id);
        if (record.getActualEnd() != null) {
            throw new BusinessConflictException("Смена уже была закрыта ранее");
        }

        record.setActualEnd(Instant.now());
        if (request.getBreakMinutes() != null) {
            record.setBreakMinutes(request.getBreakMinutes());
        }
        if (request.getComment() != null) {
            record.setComment(request.getComment());
        }

        return toResponse(attendanceRepository.save(record));
    }

    public AttendanceRecord findAttendanceById(Long id) {
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Запись явки с ID " + id + " не найдена"));
    }

    public AttendanceRecordDto.Response toResponse(AttendanceRecord r) {
        Long lateMinutes = null;
        if (r.getActualStart() != null && r.getPlannedStart() != null) {
            long diff = Duration.between(r.getPlannedStart(), r.getActualStart()).toMinutes();
            lateMinutes = Math.max(0, diff);
        }

        Long overtimeMinutes = null;
        if (r.getActualEnd() != null && r.getPlannedEnd() != null) {
            long diff = Duration.between(r.getPlannedEnd(), r.getActualEnd()).toMinutes();
            overtimeMinutes = Math.max(0, diff);
        }

        return AttendanceRecordDto.Response.builder()
                .id(r.getId())
                .employeeId(r.getEmployee().getId())
                .employeeName(r.getEmployee().getName())
                .shiftId(r.getShift() != null ? r.getShift().getId() : null)
                .plannedStart(r.getPlannedStart())
                .plannedEnd(r.getPlannedEnd())
                .actualStart(r.getActualStart())
                .actualEnd(r.getActualEnd())
                .breakMinutes(r.getBreakMinutes())
                .comment(r.getComment())
                .lateMinutes(lateMinutes)
                .overtimeMinutes(overtimeMinutes)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
