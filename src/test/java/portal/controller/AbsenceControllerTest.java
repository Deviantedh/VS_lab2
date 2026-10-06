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
import portal.dto.EmployeeAbsenceDto;
import portal.entity.AbsenceType;
import portal.exception.GlobalExceptionHandler;
import portal.service.AbsenceService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AbsenceController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class AbsenceControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AbsenceService absenceService;

    @Test
    void getAll_shouldReturnAbsences() throws Exception {
        when(absenceService.getAll()).thenReturn(List.of(EmployeeAbsenceDto.Response.builder().id(1L).employeeId(2L).type(AbsenceType.VACATION).build()));
        mockMvc.perform(get("/api/absences")).andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("VACATION"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        EmployeeAbsenceDto.Request request = EmployeeAbsenceDto.Request.builder().employeeId(2L).type(AbsenceType.SICK_LEAVE)
                .dateFrom(LocalDate.of(2026, 2, 1)).dateTo(LocalDate.of(2026, 2, 2)).build();
        when(absenceService.create(any())).thenReturn(EmployeeAbsenceDto.Response.builder().id(3L).employeeId(2L).type(AbsenceType.SICK_LEAVE).build());
        mockMvc.perform(post("/api/absences").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void create_shouldReturn400ForMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/absences").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
