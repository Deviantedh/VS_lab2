package portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portal.dto.EmployeeDto;
import portal.dto.ShiftDto;
import portal.entity.EmployeeStatus;
import portal.config.JacksonConfig;
import portal.exception.GlobalExceptionHandler;
import portal.service.ShiftService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ShiftController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class ShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

        @MockitoBean
    private ShiftService shiftService;

    @Test
    @DisplayName("GET /api/shifts returns pagination headers and body")
    void getAll_shouldReturnPaginatedResultWithHeaders() throws Exception {
        ShiftDto.Response response = ShiftDto.Response.builder()
                .id(1L)
                .scheduleId(2L)
                .date(LocalDate.of(2026, 10, 20))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .breakMinutes(30)
                .assignedEmployees(List.of(
                        EmployeeDto.Response.builder().id(1L).name("John").phone("+79990000000").status(EmployeeStatus.ACTIVE).build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(shiftService.getAllPaged(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 42));

        mockMvc.perform(get("/api/shifts").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "42"))
                .andExpect(header().string("X-Total-Pages", "3"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("GET /api/shifts/schedule/{scheduleId} returns shifts for schedule")
    void getBySchedule_shouldReturnList() throws Exception {
        ShiftDto.Response response = ShiftDto.Response.builder()
                .id(1L)
                .scheduleId(2L)
                .date(LocalDate.of(2026, 10, 20))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .breakMinutes(30)
                .assignedEmployees(List.of())
                .build();
        when(shiftService.getBySchedule(eq(2L), any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/shifts/schedule/2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/shifts creates shift with 201 Created")
    void create_shouldReturnCreatedShift() throws Exception {
        ShiftDto.Request request = ShiftDto.Request.builder()
                .scheduleId(2L)
                .date(LocalDate.of(2026, 10, 20))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .breakMinutes(30)
                .build();
        ShiftDto.Response response = ShiftDto.Response.builder()
                .id(9L)
                .scheduleId(2L)
                .date(LocalDate.of(2026, 10, 20))
                .timeFrom(LocalTime.of(9, 0))
                .timeTo(LocalTime.of(18, 0))
                .breakMinutes(30)
                .assignedEmployees(List.of())
                .build();
        when(shiftService.create(any(ShiftDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/shifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @DisplayName("Invalid page size in /api/shifts returns 400")
    void getAll_shouldRejectSizeAboveMax() throws Exception {
        mockMvc.perform(get("/api/shifts").param("page", "0").param("size", "60"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/shifts/{id}/employees invalid payload returns 400")
    void assignEmployee_shouldRejectMissingEmployeeId() throws Exception {
        ShiftDto.AssignEmployeeRequest request = ShiftDto.AssignEmployeeRequest.builder().build();

        mockMvc.perform(post("/api/shifts/1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
