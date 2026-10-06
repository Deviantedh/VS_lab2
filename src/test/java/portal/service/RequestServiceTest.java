package portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import portal.dto.RequestDto;
import portal.entity.*;
import portal.exception.BusinessConflictException;
import portal.repository.EmployeeAbsenceRepository;
import portal.repository.RequestRepository;
import portal.repository.ShiftEmployeeLogRepository;
import portal.repository.ShiftRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private EmployeeAbsenceRepository absenceRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftEmployeeLogRepository shiftLogRepository;

    @Mock
    private EmployeeService employeeService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RequestService requestService;

    @Test
    @DisplayName("Успешное одобрение отпуска: фиксация отсутствия и авто-снятие со смен")
    void processRequest_ApproveVacation_Success() {
        // Arrange
        Employee employee = Employee.builder()
                .id(1L)
                .name("Тестовый Сотрудник")
                .status(EmployeeStatus.ACTIVE)
                .build();

        Employee manager = Employee.builder()
                .id(2L)
                .name("Менеджер")
                .status(EmployeeStatus.ACTIVE)
                .build();

        Request request = Request.builder()
                .id(10L)
                .employee(employee)
                .type(RequestType.VACATION)
                .status(RequestStatus.PENDING)
                .requestData("{\"dateFrom\": \"2026-10-01\", \"dateTo\": \"2026-10-10\"}")
                .build();

        List<Employee> shiftEmployees = new ArrayList<>();
        shiftEmployees.add(employee);

        Shift conflictingShift = Shift.builder()
                .id(100L)
                .date(LocalDate.of(2026, 10, 5))
                .employees(shiftEmployees)
                .build();

        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));
        when(employeeService.findEmployeeById(2L)).thenReturn(manager);
        when(requestRepository.save(any(Request.class))).thenReturn(request);
        when(shiftRepository.findShiftsForEmployeeBetweenDates(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(conflictingShift));

        RequestDto.Process processDto = RequestDto.Process.builder()
                .status(RequestStatus.APPROVED)
                .processedById(2L)
                .resolutionComment("Отпуск одобрен")
                .build();

        // Act
        RequestDto.Response response = requestService.processRequest(10L, processDto);

        // Assert
        assertNotNull(response);
        assertEquals(RequestStatus.APPROVED, response.getStatus());

        // 1. Проверяем фиксацию отсутствия в календаре
        verify(absenceRepository, times(1)).save(any(EmployeeAbsence.class));

        // 2. Проверяем, что сотрудник снят с конфликтующей смены
        assertTrue(conflictingShift.getEmployees().isEmpty());
        verify(shiftRepository, times(1)).save(conflictingShift);

        // 3. Проверяем, что в аудит-лог смен записано удаление
        verify(shiftLogRepository, times(1)).save(any(ShiftEmployeeLog.class));
    }

    @Test
    @DisplayName("Ошибка при попытке повторной обработки уже решённой заявки")
    void processRequest_AlreadyProcessed_ThrowsConflictException() {
        // Arrange
        Request request = Request.builder()
                .id(10L)
                .status(RequestStatus.APPROVED)
                .build();

        when(requestRepository.findById(10L)).thenReturn(Optional.of(request));

        RequestDto.Process processDto = RequestDto.Process.builder()
                .status(RequestStatus.REJECTED)
                .build();

        // Act & Assert
        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> requestService.processRequest(10L, processDto)
        );

        assertTrue(ex.getMessage().contains("уже была обработана"));
        verify(requestRepository, never()).save(any());
        verify(absenceRepository, never()).save(any());
    }
}
