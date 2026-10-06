package portal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import portal.entity.EmployeeAssignment;

import java.util.List;

@Repository
public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {
    List<EmployeeAssignment> findAllByEmployeeId(Long employeeId);
    List<EmployeeAssignment> findAllByBranchId(Long branchId);
}
