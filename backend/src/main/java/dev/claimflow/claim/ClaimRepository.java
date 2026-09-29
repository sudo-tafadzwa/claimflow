package dev.claimflow.claim;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findBySubmittedByOrderByCreatedAtDesc(String submittedBy);

    List<Claim> findByStatusInOrderByCreatedAtDesc(Collection<ClaimStatus> statuses);

    List<Claim> findAllByOrderByCreatedAtDesc();

    long countByPolicyNumberAndIdNotAndCreatedAtAfter(String policyNumber, UUID excludeId, Instant after);

    @Query(value = "SELECT nextval('claim_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    @Modifying(clearAutomatically = true)
    @Query("update Claim c set c.processInstanceKey = :key where c.id = :id")
    void setProcessInstanceKey(UUID id, long key);
}
