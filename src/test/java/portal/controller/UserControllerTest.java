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
import portal.dto.UserDto;
import portal.entity.RoleCode;
import portal.exception.GlobalExceptionHandler;
import portal.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class UserControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UserService userService;

    @Test
    void getAll_shouldReturnUsers() throws Exception {
        when(userService.getAll()).thenReturn(List.of(UserDto.Response.builder().id(1L).login("worker").roleCode(RoleCode.EMPLOYEE).build()));
        mockMvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].login").value("worker"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        UserDto.Request request = UserDto.Request.builder().login("worker").roleId((short) 3).build();
        when(userService.create(any())).thenReturn(UserDto.Response.builder().id(2L).login("worker").roleCode(RoleCode.EMPLOYEE).build());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/2")).andExpect(status().isNoContent());
        verify(userService).delete(2L);
    }

    @Test
    void create_shouldReturn400ForShortLogin() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"ab\",\"roleId\":3}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
