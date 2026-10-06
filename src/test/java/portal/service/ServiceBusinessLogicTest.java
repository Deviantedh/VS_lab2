package portal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import portal.dto.*;
import portal.entity.*;
import portal.exception.BusinessConflictException;
import portal.exception.ResourceNotFoundException;
import portal.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceBusinessLogicTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private CompanyService companyService;
    @Mock private PositionRepository positionRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository assignmentRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftEmployeeLogRepository logRepository;
    @Mock private EmployeeAbsenceRepository absenceRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private ShiftEmployeeLogRepository shiftLogRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private BranchService branchServiceCollaborator;
    @Mock private PositionService positionServiceCollaborator;
    @Mock private EmployeeService employeeServiceCollaborator;
    @org.mockito.Spy private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks private CompanyService companyServiceReal;
    @InjectMocks private BranchService branchService;
    @InjectMocks private PositionService positionService;
    @InjectMocks private EmployeeService employeeService;
    @InjectMocks private ShiftService shiftService;
    @InjectMocks private RequestService requestService;
    @InjectMocks private AttendanceService attendanceService;
    @InjectMocks private AbsenceService absenceService;
    @InjectMocks private UserService userService;

    @Test
    @DisplayName("CompanyService trims name and persists result")
    void companyServiceCreate_shouldTrimName() {
        CompanyDto.Request request = CompanyDto.Request.builder().name("  Coffee Point  ").build();
        Company saved = Company.builder().id(7L).name("Coffee Point").build();
        when(companyRepository.save(any(Company.class))).thenReturn(saved);

        CompanyDto.Response response = companyServiceReal.create(request);

        assertEquals("Coffee Point", response.getName());
        assertEquals(7L, response.getId());
        verify(companyRepository).save(argThat(c -> c.getName().equals("Coffee Point")));
    }

    @Test
    @DisplayName("CompanyService throws when id not found")
    void companyServiceGetById_shouldThrowIfMissing() {
        when(companyRepository.findById(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> companyServiceReal.getById(404L));

        assertTrue(exception.getMessage().contains("Компания с ID 404"));
    }

    @Test
    @DisplayName("PositionService rejects duplicate title")
    void positionServiceCreate_shouldRejectDuplicateTitle() {
        PositionDto.Request request = PositionDto.Request.builder().title("Бариста").build();
        when(positionRepository.existsByTitle("Бариста")).thenReturn(true);

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> positionService.create(request));

        assertTrue(exception.getMessage().contains("уже существует"));
        verify(positionRepository, never()).save(any());
    }

    @Test
    @DisplayName("BranchService creates branch with resolved company and correct active flag")
    void branchServiceCreate_shouldCreateBranchWithCompany() {
        Company company = Company.builder().id(3L).name("Сеть").build();
        BranchDto.Request request = BranchDto.Request.builder()
                .companyId(3L)
                .name("  Main  ")
                .address("  Main St  ")
                .phone("+79990000000")
                .isActive(false)
                .build();
        when(companyService.findCompanyById(3L)).thenReturn(company);
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(11L);
            return branch;
        });

        BranchDto.Response response = branchService.create(request);

        assertEquals(11L, response.getId());
        assertEquals(3L, response.getCompanyId());
        assertEquals("Main", response.getName());
        assertFalse(response.getIsActive());
    }

    @Test
    @DisplayName("EmployeeService rejects duplicate phone")
    void employeeServiceCreate_shouldConflictOnDuplicatePhone() {
        EmployeeDto.Request request = EmployeeDto.Request.builder()
                .name("Иван")
                .phone("+79990000000")
                .build();
        when(employeeRepository.existsByPhone("+79990000000")).thenReturn(true);

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> employeeService.create(request));

        assertTrue(exception.getMessage().contains("уже числится"));
    }

    @Test
    @DisplayName("EmployeeService dismiss updates status and dismissal date")
    void employeeServiceDismiss_shouldMarkDismissed() {
        Employee employee = Employee.builder().id(2L).name("Анна").status(EmployeeStatus.ACTIVE).build();
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDto.Response response = employeeService.dismiss(2L);

        assertEquals(EmployeeStatus.DISMISSED, response.getStatus());
        assertNotNull(employee.getDismissalDate());
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("EmployeeService rehire updates status to ACTIVE and clears dismissal date")
    void employeeServiceRehire_shouldMarkActiveAndClearDismissalDate() {
        Employee employee = Employee.builder().id(2L).name("Анна").status(EmployeeStatus.DISMISSED).dismissalDate(LocalDate.now()).build();
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDto.Response response = employeeService.rehire(2L);

        assertEquals(EmployeeStatus.ACTIVE, response.getStatus());
        assertNull(employee.getDismissalDate());
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("EmployeeService assigns employee to branch and returns assignment response")
    void employeeServiceAssignToBranch_shouldPersistAssignment() {
        Employee employee = Employee.builder().id(5L).name("Тест").build();
        Branch branch = Branch.builder().id(8L).name("Филиал").company(Company.builder().id(1L).name("Ком" ).build()).build();
        Position position = Position.builder().id(9L).title("Кассир").build();

        EmployeeAssignmentDto.Request request = EmployeeAssignmentDto.Request.builder()
                .employeeId(5L)
                .branchId(8L)
                .positionId(9L)
                .startedAt(LocalDate.of(2026, 1, 10))
                .isPrimary(true)
                .build();

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(branchServiceCollaborator.findBranchById(8L)).thenReturn(branch);
        when(positionServiceCollaborator.findPositionById(9L)).thenReturn(position);
        when(assignmentRepository.save(any(EmployeeAssignment.class))).thenAnswer(invocation -> {
            EmployeeAssignment assignment = invocation.getArgument(0);
            assignment.setId(99L);
            return assignment;
        });

        EmployeeAssignmentDto.Response response = employeeService.assignToBranch(request);

        assertEquals(99L, response.getId());
        assertEquals(5L, response.getEmployeeId());
        assertEquals("Кассир", response.getPositionTitle());
    }

    @Test
    @DisplayName("ShiftService rejects time order that is not before")
    void shiftServiceCreate_shouldRejectInvalidTimeRange() {
        ShiftDto.Request request = ShiftDto.Request.builder()
                .scheduleId(1L)
                .date(LocalDate.of(2026, 11, 3))
                .timeFrom(LocalTime.of(18, 0))
                .timeTo(LocalTime.of(9, 0))
                .build();

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> shiftService.create(request));

        assertTrue(exception.getMessage().contains("должно быть строго раньше"));
    }

    @Test
    @DisplayName("ShiftService rejects assigning already assigned employee")
    void shiftServiceAssignEmployee_shouldRejectDuplicateAssignment() {
        Schedule schedule = Schedule.builder().id(2L).dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 30)).build();
        Employee employee = Employee.builder().id(8L).name("Никита").status(EmployeeStatus.ACTIVE).build();
        Shift shift = Shift.builder()
                .id(4L)
                .schedule(schedule)
                .date(LocalDate.of(2026, 1, 10))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .employees(new ArrayList<>(List.of(employee)))
                .build();

        when(shiftRepository.findById(4L)).thenReturn(Optional.of(shift));
        when(employeeServiceCollaborator.findEmployeeById(8L)).thenReturn(employee);

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> shiftService.assignEmployee(4L, 8L, null));

        assertTrue(exception.getMessage().contains("уже назначен"));
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("RequestService processes approved vacation and creates absence")
    void requestServiceProcessRequest_shouldApproveVacationAndSaveAbsence() {
        Employee employee = Employee.builder().id(12L).name("Марк").build();
        Request request = Request.builder()
                .id(40L)
                .employee(employee)
                .type(RequestType.VACATION)
                .status(RequestStatus.PENDING)
                .requestData("{\"dateFrom\":\"2026-09-10\",\"dateTo\":\"2026-09-12\"}")
                .build();
        Employee manager = Employee.builder().id(5L).name("Менеджер").build();
        when(requestRepository.findById(40L)).thenReturn(Optional.of(request));
        when(employeeServiceCollaborator.findEmployeeById(5L)).thenReturn(manager);
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(absenceRepository.save(any(EmployeeAbsence.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftRepository.findShiftsForEmployeeBetweenDates(eq(12L), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        RequestDto.Process processDto = RequestDto.Process.builder()
                .status(RequestStatus.APPROVED)
                .processedById(5L)
                .resolutionComment("Одобрено")
                .build();

        RequestDto.Response response = requestService.processRequest(40L, processDto);

        assertEquals(RequestStatus.APPROVED, response.getStatus());
        verify(absenceRepository).save(any(EmployeeAbsence.class));
    }

    @Test
    @DisplayName("AttendanceService checkOut sets actualEnd and computes overtime from planned end")
    void attendanceServiceCheckOut_shouldMarkClosedAndComputeOvertime() {
        Employee employee = Employee.builder().id(1L).name("Олег").build();
        AttendanceRecord record = AttendanceRecord.builder()
                .id(10L)
                .employee(employee)
                .plannedStart(Instant.parse("2026-10-01T09:00:00Z"))
                .plannedEnd(Instant.parse("2026-10-01T18:00:00Z"))
                .actualStart(Instant.parse("2026-10-01T09:10:00Z"))
                .comment("проверка")
                .breakMinutes(0)
                .build();
        when(attendanceRepository.findById(10L)).thenReturn(Optional.of(record));
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecordDto.Response response = attendanceService.checkOut(10L, AttendanceRecordDto.CheckOutRequest.builder()
                .comment("закрыто")
                .breakMinutes(30)
                .build());

        assertNotNull(response.getActualEnd());
        assertEquals(10L, response.getLateMinutes());
        assertTrue(response.getOvertimeMinutes() >= 0);
        assertEquals("закрыто", record.getComment());
    }

    @Test
    @DisplayName("AbsenceService rejects absence with start date after end date")
    void absenceServiceCreate_shouldRejectInvalidDateRange() {
        EmployeeAbsenceDto.Request request = EmployeeAbsenceDto.Request.builder()
                .employeeId(7L)
                .type(AbsenceType.VACATION)
                .dateFrom(LocalDate.of(2026, 10, 12))
                .dateTo(LocalDate.of(2026, 10, 10))
                .build();

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> absenceService.create(request));

        assertTrue(exception.getMessage().contains("не может быть позже"));
    }

    @Test
    @DisplayName("UserService rejects duplicate login")
    void userServiceCreate_shouldRejectDuplicateLogin() {
        UserDto.Request request = UserDto.Request.builder()
                .login("alex")
                .roleId((short) 3)
                .employeeId(10L)
                .build();
        when(userRepository.existsByLogin("alex")).thenReturn(true);

        BusinessConflictException exception = assertThrows(BusinessConflictException.class,
                () -> userService.create(request));

        assertTrue(exception.getMessage().contains("Логин 'alex'"));
    }
}
