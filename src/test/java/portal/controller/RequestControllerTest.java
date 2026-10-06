package portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import portal.config.JacksonConfig;
import portal.dto.RequestDto;
import portal.entity.RequestStatus;
import portal.entity.RequestType;
import portal.exception.BusinessConflictException;
import portal.exception.GlobalExceptionHandler;
import portal.service.RequestService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RequestController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class RequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private RequestService requestService;

    @Test
    void getAll_shouldReturnPageResponse() throws Exception {
        RequestDto.Response response = RequestDto.Response.builder()
                .id(1L).employeeId(2L).employeeName("Worker")
                .type(RequestType.CERTIFICATE).status(RequestStatus.PENDING).build();
        when(requestService.getAllPaged(any(), any())).thenReturn(
                new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/requests").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        RequestDto.Create create = RequestDto.Create.builder()
                .employeeId(2L).type(RequestType.DAY_OFF).requestData("{}").build();
        RequestDto.Response response = RequestDto.Response.builder()
                .id(4L).employeeId(2L).type(RequestType.DAY_OFF).status(RequestStatus.PENDING).build();
        when(requestService.create(any(RequestDto.Create.class))).thenReturn(response);

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void process_shouldReturn409ForBusinessConflict() throws Exception {
        RequestDto.Process process = RequestDto.Process.builder()
                .status(RequestStatus.APPROVED).resolutionComment("done").build();
        when(requestService.processRequest(eq(7L), any(RequestDto.Process.class)))
                .thenThrow(new BusinessConflictException("already processed"));

        mockMvc.perform(post("/api/requests/7/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(process)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("already processed"));
    }

    @Test
    void create_shouldReturn400WhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.employeeId").exists())
                .andExpect(jsonPath("$.validationErrors.type").exists());
    }
}
