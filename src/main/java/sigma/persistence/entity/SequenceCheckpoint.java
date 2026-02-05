package sigma.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Stores checkpoint information for sequence-based pagination
 * Used to track the last processed sequence for each table/collection
 */
@Table("sequence_checkpoints")
public class SequenceCheckpoint {

    @Id
    private String id; // collectionName used as ID

    @Column("collection_name")
    private String collectionName;

    @Column("sequence")
    private long sequence;

    @Column("resume_token")
    private String resumeToken; // Stored as JSON string or timestamp

    @Column("last_updated")
    private long lastUpdated;

    public SequenceCheckpoint() {
    }

    public SequenceCheckpoint(String collectionName, long sequence, String resumeToken) {
        this.id = collectionName;
        this.collectionName = collectionName;
        this.sequence = sequence;
        this.resumeToken = resumeToken;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public void setResumeToken(String resumeToken) {
        this.resumeToken = resumeToken;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
