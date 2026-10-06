package portal.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import portal.Lab1ApplicationTests;
import portal.dto.*;
import portal.entity.*;
import portal.exception.BusinessConflictException;
import portal.repository.*;
import portal.service.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(portal.TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class BusinessFlowIntegrationTest {

    @Autowired private CompanyService companyService;
    @Autowired private BranchService branchService;
    @Autowired private PositionService positionService;
    @Autowired private EmployeeService employeeService;
    @Autowired private ScheduleService scheduleService;
    @Autowired private ShiftService shiftService;
    @Autowired private RequestService requestService;
    @Autowired private AbsenceService absenceService;
    @Autowired private AttendanceService attendanceService;

    @Autowired private CompanyRepository companyRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private EmployeeAbsenceRepository absenceRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private ShiftEmployeeLogRepository shiftLogRepository;

    @Test
    @DisplayName("Real DB: company, branch, position, employee, assignment and shift assignment flow work together")
    void realDatabaseFlow_shouldCreateCompanyAndAssignEmployeeToShift() {
        CompanyDto.Response company = companyService.create(CompanyDto.Request.builder().name("Main Cafe").build());
        BranchDto.Response branch = branchService.create(BranchDto.Request.builder()
                .companyId(company.getId())
                .name("Downtown")
                .address("Main 12")
                .phone("+79998887766")
                .isActive(true)
                .build());
        PositionDto.Response position = positionService.create(PositionDto.Request.builder().title("Barista").build());
        EmployeeDto.Response employee = employeeService.create(EmployeeDto.Request.builder()
                .name("Real Employee")
                .phone("+79991110000")
                .status(EmployeeStatus.ACTIVE)
                .build());
        employeeService.assignToBranch(EmployeeAssignmentDto.Request.builder()
                .employeeId(employee.getId())
                .branchId(branch.getId())
                .positionId(position.getId())
                .startedAt(LocalDate.of(2026, 2, 1))
                .isPrimary(true)
                .build());

        ScheduleDto.Response schedule = scheduleService.create(ScheduleDto.Request.builder()
                .branchId(branch.getId())
                .dateFrom(LocalDate.of(2026, 2, 1))
                .dateTo(LocalDate.of(2026, 2, 28))
                .build());
        ShiftDto.Response shift = shiftService.create(ShiftDto.Request.builder()
                .scheduleId(schedule.getId())
                .date(LocalDate.of(2026, 2, 10))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .breakMinutes(30)
                .build());

        shiftService.assignEmployee(shift.getId(), employee.getId(), null);

        Shift savedShift = shiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(1, savedShift.getEmployees().size());
        assertEquals(1, shiftLogRepository.findAllByShiftIdOrderByCreatedAtDesc(shift.getId()).size());
    }

    @Test
    @DisplayName("Real DB: approved request creates absence and removes employee from conflicting shifts")
    void realDatabaseRequestApproval_shouldCreateAbsenceAndRemoveConflictingShiftAssignment() {
        CompanyDto.Response company = companyService.create(CompanyDto.Request.builder().name("Company 2").build());
        BranchDto.Response branch = branchService.create(BranchDto.Request.builder()
                .companyId(company.getId())
                .name("Second branch")
                .address("City 88")
                .phone("+79990001122")
                .isActive(true)
                .build());
        PositionDto.Response position = positionService.create(PositionDto.Request.builder().title("Manager").build());
        EmployeeDto.Response employee = employeeService.create(EmployeeDto.Request.builder()
                .name("Vacation Employee")
                .phone("+79991112233")
                .status(EmployeeStatus.ACTIVE)
                .build());
        employeeService.assignToBranch(EmployeeAssignmentDto.Request.builder()
                .employeeId(employee.getId())
                .branchId(branch.getId())
                .positionId(position.getId())
                .startedAt(LocalDate.of(2026, 4, 1))
                .isPrimary(true)
                .build());

        ScheduleDto.Response schedule = scheduleService.create(ScheduleDto.Request.builder()
                .branchId(branch.getId())
                .dateFrom(LocalDate.of(2026, 4, 1))
                .dateTo(LocalDate.of(2026, 4, 30))
                .build());
        ShiftDto.Response shift = shiftService.create(ShiftDto.Request.builder()
                .scheduleId(schedule.getId())
                .date(LocalDate.of(2026, 4, 15))
                .timeFrom(LocalTime.of(10, 0))
                .timeTo(LocalTime.of(15, 0))
                .build());
        shiftService.assignEmployee(shift.getId(), employee.getId(), null);

        RequestDto.Response request = requestService.create(RequestDto.Create.builder()
                .employeeId(employee.getId())
                .type(RequestType.VACATION)
                .requestData("{\"dateFrom\":\"2026-04-14\",\"dateTo\":\"2026-04-16\"}")
                .build());

        requestService.processRequest(request.getId(), RequestDto.Process.builder()
                .status(RequestStatus.APPROVED)
                .resolutionComment("approved")
                .build());

        List<EmployeeAbsence> absences = absenceRepository.findAllByEmployeeId(employee.getId());
        assertFalse(absences.isEmpty());
        assertTrue(absences.stream().anyMatch(a -> a.getType() == AbsenceType.VACATION));

        Shift updatedShift = shiftRepository.findById(shift.getId()).orElseThrow();
        assertTrue(updatedShift.getEmployees().stream().noneMatch(e -> e.getId().equals(employee.getId())));
    }

    @Test
    @DisplayName("Transaction-like business rule: assigning a duplicate employee should leave DB state unchanged")
    void duplicateAssignment_shouldNotChangeDatabaseState() {
        CompanyDto.Response company = companyService.create(CompanyDto.Request.builder().name("Rollback Cafe").build());
        BranchDto.Response branch = branchService.create(BranchDto.Request.builder()
                .companyId(company.getId())
                .name("Rollback Branch")
                .address("Rollback St")
                .phone("+79990009999")
                .build());
        PositionDto.Response position = positionService.create(PositionDto.Request.builder().title("Cook").build());
        EmployeeDto.Response employee = employeeService.create(EmployeeDto.Request.builder()
                .name("Rollback Employee")
                .phone("+79992220001")
                .status(EmployeeStatus.ACTIVE)
                .build());
        employeeService.assignToBranch(EmployeeAssignmentDto.Request.builder()
                .employeeId(employee.getId())
                .branchId(branch.getId())
                .positionId(position.getId())
                .startedAt(LocalDate.of(2026, 5, 1))
                .build());

        ScheduleDto.Response schedule = scheduleService.create(ScheduleDto.Request.builder()
                .branchId(branch.getId())
                .dateFrom(LocalDate.of(2026, 5, 1))
                .dateTo(LocalDate.of(2026, 5, 30))
                .build());
        ShiftDto.Response shift = shiftService.create(ShiftDto.Request.builder()
                .scheduleId(schedule.getId())
                .date(LocalDate.of(2026, 5, 10))
                .timeFrom(LocalTime.of(12, 0))
                .timeTo(LocalTime.of(17, 0))
                .build());
        shiftService.assignEmployee(shift.getId(), employee.getId(), null);

        assertThrows(BusinessConflictException.class, () -> shiftService.assignEmployee(shift.getId(), employee.getId(), null));
        Shift refreshed = shiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(1, refreshed.getEmployees().size());
        assertEquals(1, shiftLogRepository.findAllByShiftIdOrderByCreatedAtDesc(shift.getId()).size());
    }
}
