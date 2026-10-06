package portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCoverageUnitTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository assignmentRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftEmployeeLogRepository shiftLogRepository;
    @Mock private EmployeeAbsenceRepository absenceRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CompanyService companyService;
    @Mock private BranchService branchService;
    @Mock private PositionService positionService;
    @Mock private EmployeeService employeeService;
    @Mock private ScheduleService scheduleService;
    @Mock private ShiftService shiftService;

    private CompanyService companies;
    private BranchService branches;
    private PositionService positions;
    private EmployeeService employees;
    private ScheduleService schedules;
    private ShiftService shifts;
    private RequestService requests;
    private AttendanceService attendance;
    private AbsenceService absences;
    private UserService users;

    @BeforeEach
    void setUp() {
        companies = new CompanyService(companyRepository);
        branches = new BranchService(branchRepository, companyService);
        positions = new PositionService(positionRepository);
        employees = new EmployeeService(employeeRepository, assignmentRepository, branchService, positionService);
        schedules = new ScheduleService(scheduleRepository, branchService, employeeService);
        shifts = new ShiftService(shiftRepository, shiftLogRepository, absenceRepository, scheduleService, employeeService);
        requests = new RequestService(requestRepository, absenceRepository, shiftRepository, shiftLogRepository, employeeService, new ObjectMapper());
        attendance = new AttendanceService(attendanceRepository, employeeService, shiftService);
        absences = new AbsenceService(absenceRepository, employeeService);
        users = new UserService(userRepository, roleRepository, employeeService);
    }

    @Test
    @DisplayName("CompanyService returns all companies and maps empty result")
    void companyGetAll_shouldMapRepositoryResult() {
        when(companyRepository.findAll()).thenReturn(List.of(Company.builder().id(1L).name("One").build()));

        var result = companies.getAll();

        assertEquals(1, result.size());
        assertEquals("One", result.get(0).getName());
        verify(companyRepository).findAll();
    }

    @Test
    @DisplayName("CompanyService update and delete fail when company is missing")
    void companyUpdateAndDelete_shouldThrowWhenMissing() {
        when(companyRepository.findById(10L)).thenReturn(Optional.empty());
        CompanyDto.Request request = CompanyDto.Request.builder().name("Name").build();

        assertThrows(ResourceNotFoundException.class, () -> companies.update(10L, request));
        assertThrows(ResourceNotFoundException.class, () -> companies.delete(10L));
        verify(companyRepository, never()).save(any());
        verify(companyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("BranchService returns company branches and deletes existing branch")
    void branchGetByCompanyAndDelete_shouldDelegate() {
        Company company = Company.builder().id(1L).name("Network").build();
        Branch branch = Branch.builder().id(2L).company(company).name("Point").address("Street").isActive(true).build();
        when(branchRepository.findAllByCompanyId(1L)).thenReturn(List.of(branch));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(branch));

        var result = branches.getAllByCompany(1L);
        branches.delete(2L);

        assertEquals("Point", result.get(0).getName());
        verify(branchRepository).delete(branch);
    }

    @Test
    @DisplayName("BranchService getById reports missing branch")
    void branchGetById_shouldThrowWhenMissing() {
        when(branchRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> branches.getById(404L));
    }

    @Test
    @DisplayName("PositionService returns all positions and creates a new title")
    void positionGetAllAndCreate_shouldMapAndSave() {
        when(positionRepository.findAll()).thenReturn(List.of(Position.builder().id(1L).title("Manager").build()));
        when(positionRepository.existsByTitle("Cashier")).thenReturn(false);
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> {
            Position position = invocation.getArgument(0);
            position.setId(2L);
            return position;
        });

        assertEquals("Manager", positions.getAll().get(0).getTitle());
        var created = positions.create(PositionDto.Request.builder().title(" Cashier ").build());

        assertEquals(2L, created.getId());
        assertEquals("Cashier", created.getTitle());
        verify(positionRepository).save(argThat(p -> p.getTitle().equals("Cashier")));
    }

    @Test
    @DisplayName("PositionService getById and delete report missing position")
    void positionGetByIdAndDelete_shouldThrowWhenMissing() {
        when(positionRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> positions.getById(9L));
        assertThrows(ResourceNotFoundException.class, () -> positions.delete(9L));
    }

    @Test
    @DisplayName("EmployeeService update keeps existing hire date when request omits it")
    void employeeUpdate_shouldPersistChangedFields() {
        LocalDate hireDate = LocalDate.of(2025, 1, 1);
        Employee employee = Employee.builder().id(1L).name("Old").phone("+79990000001")
                .hireDate(hireDate).status(EmployeeStatus.ACTIVE).build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeDto.Response response = employees.update(1L, EmployeeDto.Request.builder()
            .name(" New ").phone("+79990000001").status(EmployeeStatus.VACATION).build());

        assertEquals("New", response.getName());
        assertEquals(hireDate, response.getHireDate());
        assertEquals(EmployeeStatus.VACATION, response.getStatus());
        verify(employeeRepository).save(employee);
        verify(employeeRepository, never()).existsByPhone(anyString());
    }

    @Test
    @DisplayName("EmployeeService getAllPaged maps page content")
    void employeeGetAllPaged_shouldMapPage() {
        Employee employee = Employee.builder().id(3L).name("Worker").phone("+79990000003").status(EmployeeStatus.ACTIVE).build();
        var pageable = PageRequest.of(0, 2);
        when(employeeRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(employee), pageable, 1));

        var result = employees.getAllPaged(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Worker", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("EmployeeService getAssignments validates employee and maps assignment")
    void employeeGetAssignments_shouldValidateAndMap() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        Branch branch = Branch.builder().id(2L).name("Point").build();
        Position position = Position.builder().id(3L).title("Manager").build();
        EmployeeAssignment assignment = EmployeeAssignment.builder().id(4L).employee(employee).branch(branch).position(position)
                .startedAt(LocalDate.of(2026, 1, 1)).isPrimary(true).build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(assignmentRepository.findAllByEmployeeId(1L)).thenReturn(List.of(assignment));

        var result = employees.getAssignments(1L);

        assertEquals(4L, result.get(0).getId());
        assertEquals("Manager", result.get(0).getPositionTitle());
        verify(assignmentRepository).findAllByEmployeeId(1L);
    }

    @Test
    @DisplayName("ScheduleService returns schedules and deletes existing schedule")
    void scheduleGetByBranchAndDelete_shouldDelegate() {
        Branch branch = Branch.builder().id(1L).name("Point").build();
        Schedule schedule = Schedule.builder().id(2L).branch(branch).dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build();
        when(scheduleRepository.findAllByBranchId(1L)).thenReturn(List.of(schedule));
        when(scheduleRepository.findById(2L)).thenReturn(Optional.of(schedule));

        assertEquals(2L, schedules.getByBranch(1L).get(0).getId());
        assertEquals(2L, schedules.getById(2L).getId());
        schedules.delete(2L);
        verify(scheduleRepository).delete(schedule);
    }

    @Test
    @DisplayName("ShiftService creates an in-range shift with default break")
    void shiftCreate_shouldUseDefaultBreak() {
        Schedule schedule = Schedule.builder().id(1L).dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build();
        when(scheduleService.findScheduleById(1L)).thenReturn(schedule);
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift shift = invocation.getArgument(0);
            shift.setId(2L);
            return shift;
        });

        var response = shifts.create(ShiftDto.Request.builder().scheduleId(1L).date(LocalDate.of(2026, 1, 31))
                .timeFrom(LocalTime.of(9, 0)).timeTo(LocalTime.of(18, 0)).build());

        assertEquals(2L, response.getId());
        assertEquals(0, response.getBreakMinutes());
        verify(shiftRepository).save(argThat(s -> s.getDate().equals(LocalDate.of(2026, 1, 31))));
    }

    @Test
    @DisplayName("ShiftService rejects a shift outside schedule period")
    void shiftCreate_shouldRejectDateOutsideSchedule() {
        Schedule schedule = Schedule.builder().id(1L).dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build();
        when(scheduleService.findScheduleById(1L)).thenReturn(schedule);

        assertThrows(BusinessConflictException.class, () -> shifts.create(ShiftDto.Request.builder().scheduleId(1L)
                .date(LocalDate.of(2026, 2, 1)).timeFrom(LocalTime.of(9, 0)).timeTo(LocalTime.of(18, 0)).build()));
        verify(shiftRepository, never()).save(any());
    }

    @Test
    @DisplayName("ShiftService removes assigned employee and writes removal log")
    void shiftRemoveEmployee_shouldUpdateShiftAndLog() {
        Employee employee = Employee.builder().id(3L).name("Worker").build();
        Shift shift = Shift.builder().id(4L).schedule(Schedule.builder().id(5L).build())
                .date(LocalDate.of(2026, 2, 1)).timeFrom(LocalTime.of(9, 0)).timeTo(LocalTime.of(18, 0))
                .employees(new ArrayList<>(List.of(employee))).build();
        when(shiftRepository.findById(4L)).thenReturn(Optional.of(shift));
        when(employeeService.findEmployeeById(3L)).thenReturn(employee);
        when(shiftRepository.save(shift)).thenReturn(shift);

        var response = shifts.removeEmployee(4L, 3L, null);

        assertTrue(shift.getEmployees().isEmpty());
        assertEquals(4L, response.getId());
        verify(shiftLogRepository).save(argThat(log -> log.getAction() == ShiftAction.REMOVED));
        verify(shiftRepository).save(shift);
    }

    @Test
    @DisplayName("ShiftService returns audit logs")
    void shiftGetLogs_shouldMapLog() {
        Employee employee = Employee.builder().id(3L).name("Worker").build();
        Shift shift = Shift.builder().id(4L).schedule(Schedule.builder().id(5L).build()).date(LocalDate.of(2026, 2, 1))
                .timeFrom(LocalTime.of(9, 0)).timeTo(LocalTime.of(18, 0)).employees(new ArrayList<>()).build();
        ShiftEmployeeLog log = ShiftEmployeeLog.builder().id(6L).shift(shift).employee(employee).action(ShiftAction.ASSIGNED).build();
        when(shiftRepository.findById(4L)).thenReturn(Optional.of(shift));
        when(shiftLogRepository.findAllByShiftIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(log));

        var result = shifts.getLogs(4L);

        assertEquals(6L, result.get(0).getId());
        assertEquals(ShiftAction.ASSIGNED, result.get(0).getAction());
    }

    @Test
    @DisplayName("AttendanceService checkIn creates an open attendance record")
    void attendanceCheckIn_shouldSaveCurrentRecord() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        when(employeeService.findEmployeeById(1L)).thenReturn(employee);
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> {
            AttendanceRecord record = invocation.getArgument(0);
            record.setId(2L);
            return record;
        });

        var response = attendance.checkIn(AttendanceRecordDto.CheckInRequest.builder().employeeId(1L).comment("in").build());

        assertEquals(2L, response.getId());
        assertNotNull(response.getActualStart());
        assertNull(response.getActualEnd());
        verify(attendanceRepository).save(argThat(record -> record.getEmployee() == employee && record.getComment().equals("in")));
    }

    @Test
    @DisplayName("AttendanceService getByEmployeeSliced validates employee before repository query")
    void attendanceGetByEmployeeSliced_shouldValidateEmployee() {
        var pageable = PageRequest.of(0, 20);
        when(employeeService.findEmployeeById(1L)).thenThrow(new ResourceNotFoundException("missing"));

        assertThrows(ResourceNotFoundException.class, () -> attendance.getByEmployeeSliced(1L, pageable));
        verify(attendanceRepository, never()).findAllByEmployeeId(anyLong(), any());
    }

    @Test
    @DisplayName("AbsenceService returns employee absences and reports missing absence")
    void absenceGetByEmployeeAndFind_shouldDelegate() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        EmployeeAbsence absence = EmployeeAbsence.builder().id(2L).employee(employee).type(AbsenceType.VACATION)
                .dateFrom(LocalDate.of(2026, 3, 1)).dateTo(LocalDate.of(2026, 3, 2)).build();
        when(employeeService.findEmployeeById(1L)).thenReturn(employee);
        when(absenceRepository.findAllByEmployeeId(1L)).thenReturn(List.of(absence));
        when(absenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(2L, absences.getByEmployee(1L).get(0).getId());
        assertThrows(ResourceNotFoundException.class, () -> absences.findAbsenceById(99L));
    }

    @Test
    @DisplayName("UserService gets users by login and deletes existing user")
    void userGetByLoginAndDelete_shouldMapAndDelete() {
        Role role = Role.builder().id((short) 3).code(RoleCode.EMPLOYEE).name("Employee").build();
        User user = User.builder().id(1L).login("worker").role(role).isActive(true).build();
        when(userRepository.findByLogin("worker")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = users.getByLogin("worker");
        users.delete(1L);

        assertEquals("worker", response.getLogin());
        assertEquals(RoleCode.EMPLOYEE, response.getRoleCode());
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("UserService getById and getByLogin report missing user")
    void userLookups_shouldThrowWhenMissing() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());
        when(userRepository.findByLogin("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> users.getById(10L));
        assertThrows(ResourceNotFoundException.class, () -> users.getByLogin("missing"));
    }

    @Test
    @DisplayName("RequestService rejects a non-pending request without saving")
    void requestProcess_shouldRejectNonPending() {
        Request request = Request.builder().id(1L).status(RequestStatus.REJECTED).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(BusinessConflictException.class, () -> requests.processRequest(1L,
                RequestDto.Process.builder().status(RequestStatus.APPROVED).build()));
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("RequestService rejects request and does not create absence")
    void requestProcessRejected_shouldOnlyUpdateRequest() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        Request request = Request.builder().id(2L).employee(employee).type(RequestType.VACATION).status(RequestStatus.PENDING).build();
        when(requestRepository.findById(2L)).thenReturn(Optional.of(request));
        when(requestRepository.save(request)).thenReturn(request);

        var response = requests.processRequest(2L, RequestDto.Process.builder().status(RequestStatus.REJECTED).resolutionComment("no").build());

        assertEquals(RequestStatus.REJECTED, response.getStatus());
        verify(absenceRepository, never()).save(any());
        verify(shiftRepository, never()).findShiftsForEmployeeBetweenDates(anyLong(), any(), any());
        verify(requestRepository).save(request);
    }

    @Test
    @DisplayName("RequestService creates pending request for employee")
    void requestCreate_shouldSetPendingStatus() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        when(employeeService.findEmployeeById(1L)).thenReturn(employee);
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            request.setId(3L);
            return request;
        });

        var response = requests.create(RequestDto.Create.builder().employeeId(1L).type(RequestType.CERTIFICATE).requestData("{}").build());

        assertEquals(3L, response.getId());
        assertEquals(RequestStatus.PENDING, response.getStatus());
        verify(requestRepository).save(argThat(request -> request.getStatus() == RequestStatus.PENDING));
    }
}
