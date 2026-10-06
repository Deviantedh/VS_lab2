package portal.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import portal.dto.CompanyDto;
import portal.entity.Company;
import portal.exception.ResourceNotFoundException;
import portal.repository.CompanyRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    @Test
    @DisplayName("Успешное создание новой компании")
    void createCompany_Success() {
        // Arrange
        CompanyDto.Request request = CompanyDto.Request.builder()
                .name("Кофейня Уют")
                .build();

        Company savedCompany = Company.builder()
                .id(1L)
                .name("Кофейня Уют")
                .build();

        when(companyRepository.save(any(Company.class))).thenReturn(savedCompany);

        // Act
        CompanyDto.Response response = companyService.create(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Кофейня Уют", response.getName());
        verify(companyRepository, times(1)).save(any(Company.class));
    }

    @Test
    @DisplayName("Поиск несуществующей компании выбрасывает ResourceNotFoundException")
    void getById_NotFound_ThrowsException() {
        // Arrange
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> companyService.getById(999L)
        );

        assertTrue(exception.getMessage().contains("не найдена"));
    }
}
