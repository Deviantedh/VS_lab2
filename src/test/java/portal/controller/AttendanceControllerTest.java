package portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import portal.config.JacksonConfig;
import portal.dto.AttendanceRecordDto;
import portal.dto.SliceResponse;
import portal.exception.BusinessConflictException;
import portal.exception.GlobalExceptionHandler;
import portal.service.AttendanceService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AttendanceController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class AttendanceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AttendanceService attendanceService;

    @Test
    void getAll_shouldReturnSliceWithoutTotal() throws Exception {
        AttendanceRecordDto.Response response = AttendanceRecordDto.Response.builder()
                .id(1L).employeeId(2L).employeeName("Worker")
                .plannedStart(Instant.parse("2026-01-01T09:00:00Z"))
                .plannedEnd(Instant.parse("2026-01-01T18:00:00Z"))
                .build();
        when(attendanceService.getAllSliced(any())).thenReturn(SliceResponse.<AttendanceRecordDto.Response>builder()
                .content(List.of(response)).pageNumber(0).pageSize(20).hasNext(true).build());

        mockMvc.perform(get("/api/attendance").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.total").doesNotExist());
    }

    @Test
    void checkIn_shouldReturn201() throws Exception {
        AttendanceRecordDto.CheckInRequest request = AttendanceRecordDto.CheckInRequest.builder()
                .employeeId(2L).comment("started").build();
        AttendanceRecordDto.Response response = AttendanceRecordDto.Response.builder()
                .id(3L).employeeId(2L).employeeName("Worker").build();
        when(attendanceService.checkIn(any(AttendanceRecordDto.CheckInRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/attendance/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void checkOut_shouldReturn409ForAlreadyClosedRecord() throws Exception {
        when(attendanceService.checkOut(any(Long.class), any(AttendanceRecordDto.CheckOutRequest.class)))
                .thenThrow(new BusinessConflictException("Смена уже была закрыта ранее"));

        mockMvc.perform(post("/api/attendance/3/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getAll_shouldRejectSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/attendance").param("page", "0").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
