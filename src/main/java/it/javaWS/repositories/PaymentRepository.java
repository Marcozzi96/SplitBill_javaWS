package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByPayerIdOrPayeeIdOrderByDateDescIdDesc(Long payerId, Long payeeId, Pageable pageable);

    List<Payment> findByPayerIdAndPayeeId(Long payerId, Long payeeId);

    List<Payment> findByPayerIdAndPayeeIdAndGroupId(Long payerId, Long payeeId, Long groupId);

    List<Payment> findByPayerIdAndPayeeIdAndGroupIdIsNull(Long payerId, Long payeeId);
}
