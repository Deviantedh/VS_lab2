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
import portal.dto.BranchDto;
import portal.exception.GlobalExceptionHandler;
import portal.service.BranchService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BranchController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class BranchControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean BranchService branchService;

    @Test
    void getAll_shouldReturnPage() throws Exception {
        BranchDto.Response response = BranchDto.Response.builder().id(1L).companyId(2L).name("Point").address("Street").isActive(true).build();
        when(branchService.getAllPaged(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/branches")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("Point"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        BranchDto.Request request = BranchDto.Request.builder().companyId(2L).name("Point").address("Street").build();
        when(branchService.create(any())).thenReturn(BranchDto.Response.builder().id(3L).name("Point").build());
        mockMvc.perform(post("/api/branches").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void delete_shouldReturn204AndCallService() throws Exception {
        mockMvc.perform(delete("/api/branches/3")).andExpect(status().isNoContent());
        verify(branchService).delete(3L);
    }

    @Test
    void create_shouldReturn400ForMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/branches").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
