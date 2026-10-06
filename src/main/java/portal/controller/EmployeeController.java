package portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import portal.dto.EmployeeAssignmentDto;
import portal.dto.EmployeeDto;
import portal.service.EmployeeService;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Управление персоналом и назначениями на точки")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @Operation(summary = "Получить сотрудников с пагинацией (максимум 50 записей)")
    public ResponseEntity<Page<EmployeeDto.Response>> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return ResponseEntity.ok(employeeService.getAllPaged(pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить сотрудника по ID")
    public ResponseEntity<EmployeeDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Зарегистрировать нового сотрудника")
    public ResponseEntity<EmployeeDto.Response> create(@Valid @RequestBody EmployeeDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить личные данные сотрудника")
    public ResponseEntity<EmployeeDto.Response> update(@PathVariable Long id, @Valid @RequestBody EmployeeDto.Request request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "Уволить сотрудника (перевод в статус DISMISSED)")
    public ResponseEntity<EmployeeDto.Response> dismiss(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.dismiss(id));
    }

    @PostMapping("/{id}/rehire")
    @Operation(summary = "Принять уволенного сотрудника обратно на работу (перевод в статус ACTIVE)")
    public ResponseEntity<EmployeeDto.Response> rehire(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.rehire(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить сотрудника из системы")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments")
    @Operation(summary = "Назначить сотрудника в филиал на должность (связь Many-to-Many с доп. полями)")
    public ResponseEntity<EmployeeAssignmentDto.Response> assignToBranch(@Valid @RequestBody EmployeeAssignmentDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.assignToBranch(request));
    }

    @GetMapping("/{id}/assignments")
    @Operation(summary = "История назначений сотрудника по точкам и должностям")
    public ResponseEntity<List<EmployeeAssignmentDto.Response>> getAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getAssignments(id));
    }
}
