package sigma.persistence.repository;

import sigma.persistence.entity.SequenceCheckpoint;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing sequence checkpoints
 */
@Repository
public interface SequenceCheckpointRepository extends CrudRepository<SequenceCheckpoint, String> {

    Optional<SequenceCheckpoint> findByCollectionName(String collectionName);
}
