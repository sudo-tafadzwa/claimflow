package dev.claimflow.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {

    List<ExtractedField> findByClaimIdOrderByName(UUID claimId);

    Optional<ExtractedField> findByClaimIdAndName(UUID claimId, String name);
}
