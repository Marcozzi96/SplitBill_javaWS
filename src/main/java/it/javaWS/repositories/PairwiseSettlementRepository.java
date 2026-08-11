package it.javaWS.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.PairwiseSettlement;

@Repository
public interface PairwiseSettlementRepository extends JpaRepository<PairwiseSettlement, Long> {

    Optional<PairwiseSettlement> findByDebtorIdAndCreditorIdAndGroupId(Long debtorId, Long creditorId, Long groupId);

    List<PairwiseSettlement> findByDebtorIdAndGroupId(Long debtorId, Long groupId);

    List<PairwiseSettlement> findByCreditorIdAndGroupId(Long creditorId, Long groupId);

    List<PairwiseSettlement> findByDebtorIdOrCreditorId(Long debtorId, Long creditorId);

    List<PairwiseSettlement> findByGroupId(Long groupId);

    void deleteByGroupId(Long groupId);
}
