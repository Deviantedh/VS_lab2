package portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portal.dto.CompanyDto;
import portal.config.JacksonConfig;
import portal.exception.GlobalExceptionHandler;
import portal.service.CompanyService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CompanyController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompanyService companyService;

    @Test
    @DisplayName("GET /api/companies returns 200 OK and list")
    void getAll_shouldReturnList() throws Exception {
        when(companyService.getAll()).thenReturn(java.util.List.of(
                CompanyDto.Response.builder().id(1L).name("Network").createdAt(Instant.now()).updatedAt(Instant.now()).build()
        ));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Network"));
    }

    @Test
    @DisplayName("POST /api/companies creates company with 201 Created")
    void create_shouldReturnCreatedCompany() throws Exception {
        CompanyDto.Request request = CompanyDto.Request.builder().name("Coffee House").build();
        CompanyDto.Response response = CompanyDto.Response.builder().id(11L).name("Coffee House").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(companyService.create(any(CompanyDto.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("Coffee House"));
    }

    @Test
    @DisplayName("GET /api/companies/{id} not found returns 404")
    void getById_shouldReturn404WhenMissing() throws Exception {
        when(companyService.getById(404L)).thenThrow(new portal.exception.ResourceNotFoundException("Компания с ID 404 не найдена"));

        mockMvc.perform(get("/api/companies/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("не найдена")));
    }

    @Test
    @DisplayName("POST /api/companies with empty name returns 400")
    void create_shouldRejectBlankName() throws Exception {
        CompanyDto.Request request = CompanyDto.Request.builder().name("   ").build();

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
