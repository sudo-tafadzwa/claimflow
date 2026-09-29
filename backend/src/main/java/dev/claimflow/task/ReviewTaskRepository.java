package dev.claimflow.task;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, UUID> {

    Optional<ReviewTask> findByJobKey(long jobKey);

    List<ReviewTask> findByStatusAndCandidateRoleInOrderBySlaDueAtAsc(String status, Collection<String> roles);

    List<ReviewTask> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    List<ReviewTask> findByStatusAndResumedAtIsNullAndCompletedAtBefore(String status, java.time.Instant before);
}
