package sigma.persistence.repository;

import sigma.persistence.entity.SequenceCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing sequence checkpoints
 */
@Repository
public interface SequenceCheckpointRepository extends JpaRepository<SequenceCheckpoint, String> {

    Optional<SequenceCheckpoint> findByCollectionName(String collectionName);
}
