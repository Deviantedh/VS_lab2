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
import portal.dto.ScheduleDto;
import portal.exception.GlobalExceptionHandler;
import portal.service.ScheduleService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduleController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class ScheduleControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ScheduleService scheduleService;

    @Test
    void getByBranch_shouldReturnSchedules() throws Exception {
        when(scheduleService.getByBranch(3L)).thenReturn(List.of(ScheduleDto.Response.builder().id(4L).branchId(3L).build()));
        mockMvc.perform(get("/api/schedules/branch/3")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        ScheduleDto.Request request = ScheduleDto.Request.builder().branchId(3L)
                .dateFrom(LocalDate.of(2026, 1, 1)).dateTo(LocalDate.of(2026, 1, 31)).build();
        when(scheduleService.create(any())).thenReturn(ScheduleDto.Response.builder().id(5L).branchId(3L).build());
        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/schedules/5")).andExpect(status().isNoContent());
        verify(scheduleService).delete(5L);
    }

    @Test
    void create_shouldReturn400ForMissingDates() throws Exception {
        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content("{\"branchId\":3}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
