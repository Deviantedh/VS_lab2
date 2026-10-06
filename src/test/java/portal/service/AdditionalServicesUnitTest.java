package portal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import portal.dto.*;
import portal.entity.*;
import portal.exception.BusinessConflictException;
import portal.exception.ResourceNotFoundException;
import portal.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdditionalServicesUnitTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private EmployeeAbsenceRepository absenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeAssignmentRepository assignmentRepository;
    @Mock private CompanyService companyServiceCollaborator;
    @Mock private EmployeeService employeeService;
    @Mock private BranchService branchService;
    @Mock private PositionService positionService;
    @Mock private ShiftService shiftService;

    private CompanyService companyService;
    private BranchService branchServiceReal;
    private PositionService positionServiceReal;
    private ScheduleService scheduleService;
    private AttendanceService attendanceService;
    private AbsenceService absenceService;
    private UserService userService;
    private EmployeeService employeeServiceReal;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository);
        branchServiceReal = new BranchService(branchRepository, companyServiceCollaborator);
        positionServiceReal = new PositionService(positionRepository);
        scheduleService = new ScheduleService(scheduleRepository, branchService, employeeService);
        attendanceService = new AttendanceService(attendanceRepository, employeeService, shiftService);
        absenceService = new AbsenceService(absenceRepository, employeeService);
        userService = new UserService(userRepository, roleRepository, employeeService);
        employeeServiceReal = new EmployeeService(employeeRepository, assignmentRepository, branchService, positionService);
    }

    @Test
    @DisplayName("CompanyService updates trimmed name and deletes resolved entity")
    void companyServiceUpdateAndDelete_shouldPersistAndDelete() {
        Company company = Company.builder().id(1L).name("Old").build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        CompanyDto.Response response = companyService.update(1L, CompanyDto.Request.builder().name("  New  ").build());
        companyService.delete(1L);

        assertEquals("New", response.getName());
        verify(companyRepository).save(company);
        verify(companyRepository).delete(company);
    }

    @Test
    @DisplayName("BranchService paged mapping preserves page metadata")
    void branchServicePaged_shouldMapContentAndMetadata() {
        Company company = Company.builder().id(2L).name("Network").build();
        Branch branch = Branch.builder().id(3L).company(company).name("Point").address("Street").isActive(true).build();
        Pageable pageable = PageRequest.of(1, 2);
        when(branchRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(branch), pageable, 5));

        var result = branchServiceReal.getAllPaged(pageable);

        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(5, result.getTotalElements());
        assertEquals("Point", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("BranchService update changes company only when company id differs")
    void branchServiceUpdate_shouldResolveNewCompanyWhenChanged() {
        Company oldCompany = Company.builder().id(1L).name("Old").build();
        Company newCompany = Company.builder().id(2L).name("New").build();
        Branch branch = Branch.builder().id(4L).company(oldCompany).name("Old point").address("Old address").isActive(true).build();
        BranchDto.Request request = BranchDto.Request.builder().companyId(2L).name(" Point ").address(" Address ").isActive(false).build();
        when(branchRepository.findById(4L)).thenReturn(Optional.of(branch));
        when(companyServiceCollaborator.findCompanyById(2L)).thenReturn(newCompany);
        when(branchRepository.save(branch)).thenReturn(branch);

        var response = branchServiceReal.update(4L, request);

        assertEquals(2L, response.getCompanyId());
        assertEquals("Point", response.getName());
        assertFalse(response.getIsActive());
        verify(companyServiceCollaborator).findCompanyById(2L);
    }

    @Test
    @DisplayName("PositionService allows case-only title update without duplicate lookup")
    void positionServiceUpdate_shouldAllowCaseOnlyChange() {
        Position position = Position.builder().id(5L).title("Manager").build();
        when(positionRepository.findById(5L)).thenReturn(Optional.of(position));
        when(positionRepository.save(position)).thenReturn(position);

        var response = positionServiceReal.update(5L, PositionDto.Request.builder().title(" manager ").build());

        assertEquals("manager", response.getTitle());
        verify(positionRepository, never()).existsByTitle(anyString());
    }

    @Test
    @DisplayName("PositionService rejects update when another title already exists")
    void positionServiceUpdate_shouldRejectExistingDifferentTitle() {
        Position position = Position.builder().id(5L).title("Manager").build();
        when(positionRepository.findById(5L)).thenReturn(Optional.of(position));
        when(positionRepository.existsByTitle("Cashier")).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> positionServiceReal.update(5L, PositionDto.Request.builder().title("Cashier").build()));
        verify(positionRepository, never()).save(any());
    }

    @Test
    @DisplayName("ScheduleService creates schedule with optional creator")
    void scheduleServiceCreate_shouldResolveBranchAndCreator() {
        Branch branch = Branch.builder().id(10L).name("Point").build();
        Employee creator = Employee.builder().id(11L).name("Manager").build();
        Schedule schedule = Schedule.builder().id(12L).branch(branch).createdBy(creator)
                .dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build();
        when(branchService.findBranchById(10L)).thenReturn(branch);
        when(employeeService.findEmployeeById(11L)).thenReturn(creator);
        when(scheduleRepository.save(any(Schedule.class))).thenReturn(schedule);

        var response = scheduleService.create(ScheduleDto.Request.builder()
                .branchId(10L).createdById(11L)
                .dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build());

        assertEquals(12L, response.getId());
        assertEquals(11L, response.getCreatedById());
        verify(scheduleRepository).save(argThat(s -> s.getBranch() == branch && s.getCreatedBy() == creator));
    }

    @Test
    @DisplayName("ScheduleService rejects reversed period before resolving dependencies")
    void scheduleServiceCreate_shouldRejectReversedPeriod() {
        var request = ScheduleDto.Request.builder().branchId(10L)
                .dateFrom(LocalDate.of(2026, 2, 2)).dateTo(LocalDate.of(2026, 2, 1)).build();

        assertThrows(BusinessConflictException.class, () -> scheduleService.create(request));
        verifyNoInteractions(branchService, employeeService, scheduleRepository);
    }

    @Test
    @DisplayName("AttendanceService maps Slice content and hasNext")
    void attendanceServiceGetAllSliced_shouldReturnSliceMetadata() {
        Employee employee = Employee.builder().id(1L).name("Worker").build();
        AttendanceRecord record = AttendanceRecord.builder().id(2L).employee(employee)
                .plannedStart(Instant.parse("2026-01-01T09:00:00Z"))
                .plannedEnd(Instant.parse("2026-01-01T18:00:00Z"))
                .actualStart(Instant.parse("2026-01-01T09:05:00Z"))
                .build();
        Pageable pageable = PageRequest.of(2, 20);
        Slice<AttendanceRecord> slice = new SliceImpl<>(List.of(record), pageable, true);
        when(attendanceRepository.findAllBy(pageable)).thenReturn(slice);

        SliceResponse<AttendanceRecordDto.Response> response = attendanceService.getAllSliced(pageable);

        assertEquals(2, response.getPageNumber());
        assertEquals(20, response.getPageSize());
        assertTrue(response.isHasNext());
        assertEquals(5L, response.getContent().get(0).getLateMinutes());
        verify(attendanceRepository).findAllBy(pageable);
    }

    @Test
    @DisplayName("AttendanceService create defaults break minutes and supports optional shift")
    void attendanceServiceCreate_shouldPersistDefaults() {
        Employee employee = Employee.builder().id(3L).name("Worker").build();
        Shift shift = Shift.builder().id(4L).schedule(Schedule.builder().id(5L).build())
                .date(LocalDate.of(2026, 3, 1)).timeFrom(LocalTime.of(9, 0)).timeTo(LocalTime.of(18, 0)).build();
        when(employeeService.findEmployeeById(3L)).thenReturn(employee);
        when(shiftService.findShiftById(4L)).thenReturn(shift);
        when(attendanceRepository.save(any(AttendanceRecord.class))).thenAnswer(invocation -> {
            AttendanceRecord saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });
        Instant start = Instant.parse("2026-03-01T09:00:00Z");
        Instant end = Instant.parse("2026-03-01T18:00:00Z");

        var response = attendanceService.create(AttendanceRecordDto.Request.builder()
                .employeeId(3L).shiftId(4L).plannedStart(start).plannedEnd(end).build());

        assertEquals(6L, response.getId());
        assertEquals(0, response.getBreakMinutes());
        verify(attendanceRepository).save(argThat(r -> r.getEmployee() == employee && r.getShift() == shift));
    }

    @Test
    @DisplayName("AttendanceService rejects check-out for an already closed record")
    void attendanceServiceCheckOut_shouldRejectAlreadyClosedRecord() {
        AttendanceRecord record = AttendanceRecord.builder().id(9L)
                .employee(Employee.builder().id(1L).name("Worker").build())
                .actualEnd(Instant.now()).build();
        when(attendanceRepository.findById(9L)).thenReturn(Optional.of(record));

        assertThrows(BusinessConflictException.class,
                () -> attendanceService.checkOut(9L, new AttendanceRecordDto.CheckOutRequest()));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("AbsenceService creates an absence with employee mapping")
    void absenceServiceCreate_shouldPersistValidAbsence() {
        Employee employee = Employee.builder().id(7L).name("Worker").build();
        when(employeeService.findEmployeeById(7L)).thenReturn(employee);
        when(absenceRepository.save(any(EmployeeAbsence.class))).thenAnswer(invocation -> {
            EmployeeAbsence saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        var response = absenceService.create(EmployeeAbsenceDto.Request.builder()
                .employeeId(7L).type(AbsenceType.SICK_LEAVE)
                .dateFrom(LocalDate.of(2026, 4, 1)).dateTo(LocalDate.of(2026, 4, 3))
                .comment("ill").build());

        assertEquals(8L, response.getId());
        assertEquals(7L, response.getEmployeeId());
        assertEquals(AbsenceType.SICK_LEAVE, response.getType());
    }

    @Test
    @DisplayName("UserService creates a user with role and optional employee")
    void userServiceCreate_shouldTrimLoginAndMapRole() {
        Role role = Role.builder().id((short) 3).code(RoleCode.EMPLOYEE).name("Employee").build();
        Employee employee = Employee.builder().id(13L).name("Worker").build();
        User user = User.builder().id(14L).login("worker").role(role).employee(employee).isActive(true).build();
        when(userRepository.existsByLogin("worker")).thenReturn(false);
        when(roleRepository.findById((short) 3)).thenReturn(Optional.of(role));
        when(employeeService.findEmployeeById(13L)).thenReturn(employee);
        when(userRepository.save(any(User.class))).thenReturn(user);

        var response = userService.create(UserDto.Request.builder()
                .login("  worker ").roleId((short) 3).employeeId(13L).build());

        assertEquals("worker", response.getLogin());
        assertEquals(RoleCode.EMPLOYEE, response.getRoleCode());
        assertEquals(13L, response.getEmployeeId());
        verify(userRepository).save(argThat(saved -> saved.getLogin().equals("worker") && saved.getIsActive()));
    }

    @Test
    @DisplayName("UserService reports missing role and does not save user")
    void userServiceCreate_shouldRejectMissingRole() {
        when(userRepository.existsByLogin("worker")).thenReturn(false);
        when(roleRepository.findById((short) 9)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.create(UserDto.Request.builder().login("worker").roleId((short) 9).build()));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("EmployeeService update rejects a phone belonging to another employee")
    void employeeServiceUpdate_shouldRejectDuplicatePhone() {
        Employee employee = Employee.builder().id(1L).name("Old").phone("+79990000001").build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByPhone("+79990000002")).thenReturn(true);

        assertThrows(BusinessConflictException.class,
                () -> employeeServiceReal.update(1L, EmployeeDto.Request.builder()
                        .name("New").phone("+79990000002").build()));
        verify(employeeRepository, never()).save(any());
    }
}
