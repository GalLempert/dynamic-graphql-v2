package sigma.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Abstract class providing mandatory audit fields for all database entities.
 * These fields are automatically managed by Spring Data JPA Auditing.
 *
 * Features:
 * - Optimistic locking via @Version
 * - Automatic timestamp management via @CreatedDate and @LastModifiedDate
 * - Audit trail via @CreatedBy and @LastModifiedBy
 *
 * All concrete entity classes should extend this base class to ensure
 * consistent audit metadata across all tables.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableBaseDocument {

    /**
     * JPA's optimistic locking field
     * Automatically incremented on each update to prevent lost updates
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Creation timestamp
     * Automatically set when entity is first created
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Last modification timestamp
     * Automatically updated on each save/update
     */
    @LastModifiedDate
    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    /**
     * User/System that created the entity
     * Populated by AuditorAware implementation
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    /**
     * User/System that last modified the entity
     * Populated by AuditorAware implementation on each update
     */
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    /**
     * Request ID that last modified this entity
     * Tracks which API request made the last change for debugging/auditing
     * Must be manually set in service layer (not managed by Spring Data)
     */
    @Column(name = "latest_request_id")
    private String latestRequestId;

    /**
     * Logical deletion flag
     * When true, entity should be treated as deleted without removing it physically
     */
    @Column(name = "is_deleted")
    private boolean isDeleted = false;
}
