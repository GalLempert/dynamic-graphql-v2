package iaf.ofek.sigma.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;

import java.time.Instant;

/**
 * Abstract class providing mandatory audit fields for all MongoDB documents.
 * These fields are automatically managed by Spring Data Auditing.
 *
 * Features:
 * - Optimistic locking via @Version
 * - Automatic timestamp management via @CreatedDate and @LastModifiedDate
 * - Audit trail via @CreatedBy and @LastModifiedBy
 *
 * All concrete document classes should extend this base class to ensure
 * consistent audit metadata across all collections.
 */
@Getter
@Setter
public abstract class AuditableBaseDocument {

    /**
     * Spring Data's optimistic locking field
     * Automatically incremented on each update to prevent lost updates
     */
    @Version
    private Long version;

    /**
     * Creation timestamp
     * Automatically set when document is first created
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Last modification timestamp
     * Automatically updated on each save/update
     */
    @LastModifiedDate
    private Instant lastModifiedAt;

    /**
     * User/System that created the document
     * Populated by AuditorAware implementation
     */
    @CreatedBy
    private String createdBy;

    /**
     * User/System that last modified the document
     * Populated by AuditorAware implementation on each update
     */
    @LastModifiedBy
    private String lastModifiedBy;

    /**
     * Request ID that last modified this document
     * Tracks which API request made the last change for debugging/auditing
     * Must be manually set in service layer (not managed by Spring Data)
     */
    private String latestRequestId;

    /**
     * Logical delete flag for soft-delete support
     * Documents are never physically removed - instead we mark them as deleted
     */
    private boolean isDeleted = false;
}
