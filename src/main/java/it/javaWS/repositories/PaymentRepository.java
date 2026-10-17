package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByPayerIdOrPayeeIdOrderByDateDescIdDesc(Long payerId, Long payeeId, Pageable pageable);

    Page<Payment> findByGroupIdOrderByDateDescIdDesc(Long groupId, Pageable pageable);

    // Rimborsi personali (senza gruppo) tra due utenti, in entrambe le direzioni.
    @Query("""
            SELECT p FROM Payment p
            WHERE p.group IS NULL
              AND ((p.payer.id = :userId AND p.payee.id = :friendId)
                OR (p.payer.id = :friendId AND p.payee.id = :userId))
            ORDER BY p.date DESC, p.id DESC
            """)
    Page<Payment> findPersonalPaymentsBetweenUsers(@Param("userId") Long userId, @Param("friendId") Long friendId,
            Pageable pageable);

    List<Payment> findByPayerIdAndPayeeId(Long payerId, Long payeeId);

    List<Payment> findByPayerIdAndPayeeIdAndGroupId(Long payerId, Long payeeId, Long groupId);

    List<Payment> findByPayerIdAndPayeeIdAndGroupIdIsNull(Long payerId, Long payeeId);
}
