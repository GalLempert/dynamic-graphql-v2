package sigma.persistence.repository;

import sigma.model.DynamicDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository interface for DynamicDocument entities
 * Provides basic CRUD operations and custom queries for PostgreSQL JSONB
 */
@Repository
public interface DynamicDocumentJpaRepository extends JpaRepository<DynamicDocument, Long> {

    /**
     * Find all non-deleted documents by table name
     */
    @Query("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false")
    List<DynamicDocument> findByTableNameAndNotDeleted(@Param("tableName") String tableName);

    /**
     * Find all documents by table name (including deleted)
     */
    List<DynamicDocument> findByTableName(String tableName);

    /**
     * Find document by ID and table name
     */
    Optional<DynamicDocument> findByIdAndTableName(Long id, String tableName);

    /**
     * Find documents by IDs and table name
     */
    @Query("SELECT d FROM DynamicDocument d WHERE d.id IN :ids AND d.tableName = :tableName")
    List<DynamicDocument> findByIdInAndTableName(@Param("ids") List<Long> ids, @Param("tableName") String tableName);

    /**
     * Count non-deleted documents by table name
     */
    @Query("SELECT COUNT(d) FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false")
    long countByTableNameAndNotDeleted(@Param("tableName") String tableName);

    /**
     * Soft delete by ID and table name
     */
    @Modifying
    @Query("UPDATE DynamicDocument d SET d.isDeleted = true, d.latestRequestId = :requestId WHERE d.id = :id AND d.tableName = :tableName AND d.isDeleted = false")
    int softDeleteByIdAndTableName(@Param("id") Long id, @Param("tableName") String tableName, @Param("requestId") String requestId);
}
