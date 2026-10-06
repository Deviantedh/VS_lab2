package portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import portal.dto.AttendanceRecordDto;
import portal.dto.SliceResponse;
import portal.service.AttendanceService;

@Validated
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Учёт фактического рабочего времени, явка, опоздания и переработки")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * ТРЕБОВАНИЕ ТЗ:
     * Должен быть минимум один запрос, который вернет findAll в виде бесконечной прокрутки без указания общего количества записей.
     */
    @GetMapping
    @Operation(
            summary = "Получить записи явок для бесконечной ленты (Infinite Scroll / Slice)",
            description = "Возвращает порцию записей и флаг hasNext без выполнения ресурсоёмкого SELECT count(*) по базе."
    )
    public ResponseEntity<SliceResponse<AttendanceRecordDto.Response>> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "plannedStart") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return ResponseEntity.ok(attendanceService.getAllSliced(pageRequest));
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Получить явку конкретного сотрудника для бесконечной ленты")
    public ResponseEntity<SliceResponse<AttendanceRecordDto.Response>> getByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "plannedStart"));
        return ResponseEntity.ok(attendanceService.getByEmployeeSliced(employeeId, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить отметку явки по ID")
    public ResponseEntity<AttendanceRecordDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Создать отметку явки вручную (план + факт)")
    public ResponseEntity<AttendanceRecordDto.Response> create(@Valid @RequestBody AttendanceRecordDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.create(request));
    }

    @PostMapping("/check-in")
    @Operation(summary = "Отметка о приходе на смену (Check-In) прямо сейчас")
    public ResponseEntity<AttendanceRecordDto.Response> checkIn(@Valid @RequestBody AttendanceRecordDto.CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.checkIn(request));
    }

    @PostMapping("/{id}/check-out")
    @Operation(summary = "Отметка об уходе со смены (Check-Out) прямо сейчас с расчётом опоздания и переработки")
    public ResponseEntity<AttendanceRecordDto.Response> checkOut(
            @PathVariable Long id,
            @RequestBody(required = false) AttendanceRecordDto.CheckOutRequest request
    ) {
        return ResponseEntity.ok(attendanceService.checkOut(id, request != null ? request : new AttendanceRecordDto.CheckOutRequest()));
    }
}
