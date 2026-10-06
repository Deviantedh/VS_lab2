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
import portal.dto.PositionDto;
import portal.exception.GlobalExceptionHandler;
import portal.service.PositionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PositionController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class PositionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PositionService positionService;

    @Test
    void getAll_shouldReturnPositions() throws Exception {
        when(positionService.getAll()).thenReturn(List.of(PositionDto.Response.builder().id(1L).title("Manager").build()));
        mockMvc.perform(get("/api/positions")).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Manager"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        when(positionService.create(any())).thenReturn(PositionDto.Response.builder().id(2L).title("Cashier").build());
        mockMvc.perform(post("/api/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PositionDto.Request.builder().title("Cashier").build())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/positions/2")).andExpect(status().isNoContent());
        verify(positionService).delete(2L);
    }

    @Test
    void create_shouldReturn400ForBlankTitle() throws Exception {
        mockMvc.perform(post("/api/positions").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
