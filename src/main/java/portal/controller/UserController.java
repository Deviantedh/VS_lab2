package portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import portal.dto.UserDto;
import portal.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Учётные записи и роли пользователей (0: ADMIN, 1: HR, 2: MANAGER, 3: EMPLOYEE)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Получить список всех пользователей")
    public ResponseEntity<List<UserDto.Response>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить пользователя по ID")
    public ResponseEntity<UserDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/by-login/{login}")
    @Operation(summary = "Получить пользователя по логину")
    public ResponseEntity<UserDto.Response> getByLogin(@PathVariable String login) {
        return ResponseEntity.ok(userService.getByLogin(login));
    }

    @GetMapping("/roles")
    @Operation(summary = "Получить справочник системных ролей (0: ADMIN, 1: HR, 2: MANAGER, 3: EMPLOYEE)")
    public ResponseEntity<List<UserDto.RoleResponse>> getRoles() {
        return ResponseEntity.ok(userService.getRoles());
    }

    @GetMapping("/by-employee/{employeeId}")
    @Operation(summary = "Получить пользователя по ID сотрудника")
    public ResponseEntity<UserDto.Response> getByEmployeeId(@PathVariable Long employeeId) {
        return ResponseEntity.ok(userService.getByEmployeeId(employeeId));
    }

    @PostMapping
    @Operation(summary = "Создать пользователя с указанием роли (0..3)")
    public ResponseEntity<UserDto.Response> create(@Valid @RequestBody UserDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить пользователя (роль 0..3, логин, привязку к сотруднику)")
    public ResponseEntity<UserDto.Response> update(@PathVariable Long id, @Valid @RequestBody UserDto.Request request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить пользователя")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
