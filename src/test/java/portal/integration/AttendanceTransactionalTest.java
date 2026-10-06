package portal.integration;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import portal.TestcontainersConfiguration;
import portal.dto.AttendanceRecordDto;
import portal.dto.SliceResponse;
import portal.entity.AttendanceRecord;
import portal.entity.Employee;
import portal.entity.EmployeeStatus;
import portal.repository.AttendanceRecordRepository;
import portal.repository.EmployeeRepository;
import portal.service.AttendanceService;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AttendanceTransactionalTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRecordRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Long savedRecordId;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        employeeRepository.deleteAll();

        Employee employee = employeeRepository.save(Employee.builder()
                .name("Алексей Смирнов")
                .phone("+79991112233")
                .hireDate(LocalDate.now())
                .status(EmployeeStatus.ACTIVE)
                .build());

        AttendanceRecord record = attendanceRepository.save(AttendanceRecord.builder()
                .employee(employee)
                .plannedStart(Instant.now().minusSeconds(3600))
                .plannedEnd(Instant.now().plusSeconds(28800))
                .actualStart(Instant.now().minusSeconds(3500))
                .comment("Тестовая явка")
                .build());

        savedRecordId = record.getId();
    }

    @Test
    @DisplayName("С аннотацией @Transactional")
    void whenCallingGetAllSlicedWithTransactional_thenLazyLoadingSucceeds() {
        // обращение к lazy-полю getEmployee().getName()
        SliceResponse<AttendanceRecordDto.Response> response = attendanceService.getAllSliced(PageRequest.of(0, 10));

        assertNotNull(response);
        assertFalse(response.getContent().isEmpty());

        AttendanceRecordDto.Response firstItem = response.getContent().get(0);
        assertEquals(savedRecordId, firstItem.getId());
        // Employee.name успешно подгружено через открытую сессию транзакции через lazy
        assertEquals("Алексей Смирнов", firstItem.getEmployeeName());
    }

    @Test
    @DisplayName("Без @Transactional")
    void whenAccessingLazyEmployeeWithoutTransaction_thenThrowsLazyInitializationException() {
        // Как только метод findAllBy завершается, сессия Hibernate закроется, и сущности пропадут
        Slice<AttendanceRecord> slice = attendanceRepository.findAllBy(PageRequest.of(0, 10));
        assertFalse(slice.getContent().isEmpty());

        AttendanceRecord detachedRecord = slice.getContent().get(0);

        // Попытка обратиться к lazy полю employee.getName() вне транзакции гарантированно приводит к LazyInitializationException:
        assertThrows(LazyInitializationException.class, () -> {
            detachedRecord.getEmployee().getName();
        }, "Без открытой транзакции/сессии догрузка lazy-поля Employee должна выбросить LazyInitializationException");

        // Аналогично вызов toResponse вне транзакции падает на маппинге имени сотрудника
        assertThrows(LazyInitializationException.class, () -> {
            attendanceService.toResponse(detachedRecord);
        }, "toResponse() без активной транзакции не может прочитать getEmployee().getName()");
    }
}
