package portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import portal.config.JacksonConfig;
import portal.dto.EmployeeDto;
import portal.entity.EmployeeStatus;
import portal.exception.GlobalExceptionHandler;
import portal.service.EmployeeService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class EmployeeControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean EmployeeService employeeService;

    @Test
    void getAll_shouldReturnPage() throws Exception {
        EmployeeDto.Response response = EmployeeDto.Response.builder().id(1L).name("Worker").phone("+79990000000").status(EmployeeStatus.ACTIVE).build();
        when(employeeService.getAllPaged(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        EmployeeDto.Request request = EmployeeDto.Request.builder().name("Worker").phone("+79990000000").build();
        when(employeeService.create(any())).thenReturn(EmployeeDto.Response.builder().id(4L).name("Worker").status(EmployeeStatus.ACTIVE).build());
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void dismiss_shouldReturn200() throws Exception {
        when(employeeService.dismiss(4L)).thenReturn(EmployeeDto.Response.builder().id(4L).status(EmployeeStatus.DISMISSED).build());
        mockMvc.perform(post("/api/employees/4/dismiss")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void rehire_shouldReturn200() throws Exception {
        when(employeeService.rehire(4L)).thenReturn(EmployeeDto.Response.builder().id(4L).status(EmployeeStatus.ACTIVE).build());
        mockMvc.perform(post("/api/employees/4/rehire")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(employeeService).delete(4L);
        mockMvc.perform(delete("/api/employees/4")).andExpect(status().isNoContent());
        verify(employeeService).delete(4L);
    }

    @Test
    void create_shouldReturn400ForInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Worker\",\"phone\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
