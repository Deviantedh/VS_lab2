package portal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import portal.dto.ShiftDto;
import portal.entity.*;
import portal.exception.BusinessConflictException;
import portal.repository.EmployeeAbsenceRepository;
import portal.repository.ShiftEmployeeLogRepository;
import portal.repository.ShiftRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftEmployeeLogRepository logRepository;

    @Mock
    private EmployeeAbsenceRepository absenceRepository;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private ShiftService shiftService;

    @Test
    @DisplayName("Успешное назначение сотрудника на смену с созданием аудит-лога")
    void assignEmployee_Success() {
        // Arrange
        LocalDate shiftDate = LocalDate.now().plusDays(1);
        Schedule schedule = Schedule.builder().id(10L).build();

        Shift shift = Shift.builder()
                .id(1L)
                .schedule(schedule)
                .date(shiftDate)
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .employees(new ArrayList<>())
                .build();

        Employee employee = Employee.builder()
                .id(5L)
                .name("Иван Бариста")
                .status(EmployeeStatus.ACTIVE)
                .build();

        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(employeeService.findEmployeeById(5L)).thenReturn(employee);
        when(absenceRepository.findOverlappingAbsences(5L, shiftDate, shiftDate)).thenReturn(List.of());
        when(shiftRepository.findOverlappingShiftsForEmployee(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(shiftRepository.save(any(Shift.class))).thenReturn(shift);

        // Act
        ShiftDto.Response response = shiftService.assignEmployee(1L, 5L, null);

        // Assert
        assertNotNull(response);
        assertEquals(1, shift.getEmployees().size());
        assertEquals(5L, shift.getEmployees().get(0).getId());

        // Проверяем, что аудит-лог обязательно сохранился
        verify(logRepository, times(1)).save(any(ShiftEmployeeLog.class));
        verify(shiftRepository, times(1)).save(shift);
    }

    @Test
    @DisplayName("Ошибка назначения сотрудника, если у него зафиксирован отпуск на эту дату")
    void assignEmployee_WhenInVacation_ThrowsConflictException() {
        // Arrange
        LocalDate shiftDate = LocalDate.now().plusDays(2);
        Schedule schedule = Schedule.builder().id(10L).build();

        Shift shift = Shift.builder()
                .id(1L)
                .schedule(schedule)
                .date(shiftDate)
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .employees(new ArrayList<>())
                .build();

        Employee employee = Employee.builder()
                .id(5L)
                .name("Иван Бариста")
                .status(EmployeeStatus.ACTIVE)
                .build();

        EmployeeAbsence vacation = EmployeeAbsence.builder()
                .type(AbsenceType.VACATION)
                .dateFrom(shiftDate)
                .dateTo(shiftDate.plusDays(5))
                .build();

        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(employeeService.findEmployeeById(5L)).thenReturn(employee);
        when(absenceRepository.findOverlappingAbsences(5L, shiftDate, shiftDate)).thenReturn(List.of(vacation));

        // Act & Assert
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> shiftService.assignEmployee(1L, 5L, null)
        );

        assertTrue(exception.getMessage().contains("недоступен"));
        verify(logRepository, never()).save(any());
    }
}
