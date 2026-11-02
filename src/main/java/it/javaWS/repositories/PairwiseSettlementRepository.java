package it.javaWS.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import it.javaWS.models.entities.PairwiseSettlement;

@Repository
public interface PairwiseSettlementRepository extends JpaRepository<PairwiseSettlement, Long> {

    Optional<PairwiseSettlement> findByDebtorIdAndCreditorIdAndGroupId(Long debtorId, Long creditorId, Long groupId);

    Optional<PairwiseSettlement> findByDebtorIdAndCreditorIdAndGroupIdIsNull(Long debtorId, Long creditorId);

    List<PairwiseSettlement> findByDebtorIdAndGroupId(Long debtorId, Long groupId);

    List<PairwiseSettlement> findByCreditorIdAndGroupId(Long creditorId, Long groupId);

    List<PairwiseSettlement> findByDebtorIdOrCreditorId(Long debtorId, Long creditorId);

    @EntityGraph(attributePaths = { "debtor", "creditor" })
    List<PairwiseSettlement> findByGroupId(Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "debtor", "creditor" })
    @Query("SELECT ps FROM PairwiseSettlement ps WHERE ps.group.id = :groupId ORDER BY ps.id")
    List<PairwiseSettlement> findByGroupIdForUpdate(@Param("groupId") Long groupId);

    void deleteByGroupId(Long groupId);
}
