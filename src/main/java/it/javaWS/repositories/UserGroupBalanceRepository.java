package it.javaWS.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.UserGroupBalance;

@Repository
public interface UserGroupBalanceRepository extends JpaRepository<UserGroupBalance, Long> {

    Optional<UserGroupBalance> findByUserIdAndGroupId(Long userId, Long groupId);

    List<UserGroupBalance> findByGroupId(Long groupId);

    void deleteByGroupId(Long groupId);
}
