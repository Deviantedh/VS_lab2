package portal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.Request;
import portal.entity.RequestStatus;

import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    Page<Request> findAllByStatus(RequestStatus status, Pageable pageable);
    List<Request> findAllByEmployeeId(Long employeeId);
}
