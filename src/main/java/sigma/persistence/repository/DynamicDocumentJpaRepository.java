package sigma.persistence.repository;

import sigma.model.DynamicDocument;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JDBC Repository interface for DynamicDocument entities
 * Provides basic CRUD operations and custom queries for PostgreSQL JSONB
 */
@Repository
public interface DynamicDocumentJpaRepository extends CrudRepository<DynamicDocument, Long> {

    /**
     * Find all non-deleted documents by table name
     */
    @Query("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false")
    List<DynamicDocument> findByTableNameAndNotDeleted(@Param("tableName") String tableName);

    /**
     * Find all documents by table name (including deleted)
     */
    List<DynamicDocument> findByTableName(String tableName);

    /**
     * Find document by ID and table name
     */
    @Query("SELECT * FROM dynamic_documents WHERE id = :id AND table_name = :tableName")
    Optional<DynamicDocument> findByIdAndTableName(@Param("id") Long id, @Param("tableName") String tableName);

    /**
     * Find documents by IDs and table name
     */
    @Query("SELECT * FROM dynamic_documents WHERE id IN (:ids) AND table_name = :tableName")
    List<DynamicDocument> findByIdInAndTableName(@Param("ids") List<Long> ids, @Param("tableName") String tableName);

    /**
     * Count non-deleted documents by table name
     */
    @Query("SELECT COUNT(*) FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false")
    long countByTableNameAndNotDeleted(@Param("tableName") String tableName);

    /**
     * Soft delete by ID and table name
     */
    @Modifying
    @Query("UPDATE dynamic_documents SET is_deleted = true, latest_request_id = :requestId WHERE id = :id AND table_name = :tableName AND is_deleted = false")
    int softDeleteByIdAndTableName(@Param("id") Long id, @Param("tableName") String tableName, @Param("requestId") String requestId);
}
