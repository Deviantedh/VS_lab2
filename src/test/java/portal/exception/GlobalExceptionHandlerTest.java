package portal.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/test");
        request = servletRequest;
    }

    @Test
    void handleNotFound_shouldReturn404AndPath() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("missing"), request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("missing", response.getBody().getMessage());
        assertEquals("/api/test", response.getBody().getPath());
    }

    @Test
    void handleConflict_shouldReturn409AndMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(
                new BusinessConflictException("duplicate"), request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("duplicate", response.getBody().getMessage());
    }

    @Test
    void handleDataIntegrity_shouldReturn409WithoutDatabaseDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("secret database detail"), request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Conflict", response.getBody().getError());
        assertFalse(response.getBody().getMessage().contains("secret database detail"));
    }

    @Test
    void handleIllegalArgument_shouldReturn400() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("bad argument"), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("bad argument", response.getBody().getMessage());
    }

    @Test
    void handleMethodNotSupported_shouldReturn405() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"), request);

        assertEquals(405, response.getStatusCode().value());
        assertEquals("Method Not Allowed", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("DELETE"));
    }

    @Test
    void handleGeneral_shouldReturn500WithoutOriginalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
                new RuntimeException("internal details"), request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertFalse(response.getBody().getMessage().contains("internal details"));
        assertEquals("/api/test", response.getBody().getPath());
    }
}
