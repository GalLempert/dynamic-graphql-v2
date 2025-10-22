package sigma.persistence.repository;

import sigma.persistence.entity.SequenceCheckpoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing sequence checkpoints
 */
@Repository
public interface SequenceCheckpointRepository extends MongoRepository<SequenceCheckpoint, String> {

    Optional<SequenceCheckpoint> findByCollectionName(String collectionName);
}
