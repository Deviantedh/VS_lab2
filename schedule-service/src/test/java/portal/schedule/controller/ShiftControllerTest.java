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
import portal.schedule.exception.ResourceNotFoundException;
import portal.schedule.service.ReactiveScheduleBridgeService;
import portal.schedule.service.ShiftService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftControllerTest {

    @Mock
    private ShiftService shiftService;

    @Mock
    private ReactiveScheduleBridgeService bridgeService;

    @InjectMocks
    private ShiftController shiftController;

    @Test
    @DisplayName("assignEmployeeReactive: successfully assigns employee reactively via bridge service")
    void testAssignEmployeeReactiveSuccess() {
        ShiftDto.Response expectedResponse = ShiftDto.Response.builder()
                .id(1L)
                .build();

        when(bridgeService.assignEmployeeToShiftReactive(1L, 10L))
                .thenReturn(Mono.just(expectedResponse));

        Mono<ShiftDto.Response> result = shiftController.assignEmployeeReactive(1L, 10L);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getId()).isEqualTo(1L);
                })
                .verifyComplete();

        verify(bridgeService).assignEmployeeToShiftReactive(1L, 10L);
    }

    @Test
    @DisplayName("assignEmployeeReactive: propagates error from bridge service when employee or shift not found")
    void testAssignEmployeeReactiveError() {
        when(bridgeService.assignEmployeeToShiftReactive(1L, 999L))
                .thenReturn(Mono.error(new ResourceNotFoundException("Сотрудник с ID 999 не найден")));

        Mono<ShiftDto.Response> result = shiftController.assignEmployeeReactive(1L, 999L);

        StepVerifier.create(result)
                .expectErrorMatches(t -> t instanceof ResourceNotFoundException
                        && t.getMessage().contains("Сотрудник с ID 999 не найден"))
                .verify();

        verify(bridgeService).assignEmployeeToShiftReactive(1L, 999L);
    }

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
    @DisplayName("assignEmployee (standard) returns updated shift")
    void testAssignEmployee() {
        ShiftDto.AssignEmployeeRequest req = ShiftDto.AssignEmployeeRequest.builder().employeeId(5L).assignedById(1L).build();
        ShiftDto.Response resp = ShiftDto.Response.builder().id(2L).build();
        when(shiftService.assignEmployee(2L, 5L, 1L)).thenReturn(resp);

        ResponseEntity<ShiftDto.Response> response = shiftController.assignEmployee(2L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(resp);
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
