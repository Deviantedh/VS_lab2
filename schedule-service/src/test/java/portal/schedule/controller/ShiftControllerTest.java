package portal.schedule.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import portal.dto.ShiftDto;
import portal.dto.ShiftEmployeeLogDto;
import portal.schedule.service.ShiftService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftControllerTest {

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private ShiftController shiftController;

    @Test
    @DisplayName("getAll returns paged shifts and headers")
    void testGetAll() {
        ShiftDto.Response resp = ShiftDto.Response.builder().id(1L).build();
        Page<ShiftDto.Response> page = new PageImpl<>(List.of(resp));
        when(shiftService.getAllPaged(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<List<ShiftDto.Response>> response = shiftController.getAll(0, 20, "date", Sort.Direction.DESC);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(response.getBody()).containsExactly(resp);
    }

    @Test
    @DisplayName("getById returns shift by id")
    void testGetById() {
        ShiftDto.Response resp = ShiftDto.Response.builder().id(1L).build();
        when(shiftService.getById(1L)).thenReturn(resp);

        ResponseEntity<ShiftDto.Response> response = shiftController.getById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("getBySchedule returns shifts for schedule")
    void testGetBySchedule() {
        ShiftDto.Response resp = ShiftDto.Response.builder().id(1L).build();
        Page<ShiftDto.Response> page = new PageImpl<>(List.of(resp));
        when(shiftService.getBySchedule(eq(10L), any(PageRequest.class))).thenReturn(page);

        ResponseEntity<List<ShiftDto.Response>> response = shiftController.getBySchedule(10L, 0, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(resp);
    }

    @Test
    @DisplayName("create returns created shift with 201")
    void testCreate() {
        ShiftDto.Request req = ShiftDto.Request.builder().build();
        ShiftDto.Response resp = ShiftDto.Response.builder().id(2L).build();
        when(shiftService.create(req)).thenReturn(resp);

        ResponseEntity<ShiftDto.Response> response = shiftController.create(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("assignEmployee returns updated shift")
    void testAssignEmployee() {
        ShiftDto.AssignEmployeeRequest req = ShiftDto.AssignEmployeeRequest.builder().employeeId(5L).assignedById(1L).build();
        ShiftDto.Response resp = ShiftDto.Response.builder().id(2L).build();
        when(shiftService.assignEmployee(2L, 5L, 1L)).thenReturn(resp);

        ResponseEntity<ShiftDto.Response> response = shiftController.assignEmployee(2L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("removeEmployee returns updated shift")
    void testRemoveEmployee() {
        ShiftDto.Response resp = ShiftDto.Response.builder().id(2L).build();
        when(shiftService.removeEmployee(2L, 5L, 1L)).thenReturn(resp);

        ResponseEntity<ShiftDto.Response> response = shiftController.removeEmployee(2L, 5L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("getLogs returns shift audit logs")
    void testGetLogs() {
        ShiftEmployeeLogDto logDto = ShiftEmployeeLogDto.builder().id(100L).build();
        when(shiftService.getLogs(2L)).thenReturn(List.of(logDto));

        ResponseEntity<List<ShiftEmployeeLogDto>> response = shiftController.getLogs(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(logDto);
    }

    @Test
    @DisplayName("delete returns 204 No Content")
    void testDelete() {
        doNothing().when(shiftService).delete(1L);

        ResponseEntity<Void> response = shiftController.delete(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(shiftService).delete(1L);
    }
}
