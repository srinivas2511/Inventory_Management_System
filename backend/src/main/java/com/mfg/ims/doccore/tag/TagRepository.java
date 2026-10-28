package com.mfg.ims.doccore.tag;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByCompanyIdOrderByName(Long companyId);
    boolean existsByCompanyIdAndName(Long companyId, String name);

    @Modifying
    @Query(value = "INSERT INTO ims.entity_tags (tag_id, entity_type, entity_id, tagged_by) VALUES (:tagId, :entityType, :entityId, :userId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void addTagToEntity(Long tagId, String entityType, Long entityId, Long userId);

    @Modifying
    @Query(value = "DELETE FROM ims.entity_tags WHERE tag_id = :tagId AND entity_type = :entityType AND entity_id = :entityId", nativeQuery = true)
    void removeTagFromEntity(Long tagId, String entityType, Long entityId);

    @Query(value = "SELECT t.id, t.name, t.color FROM ims.tags t JOIN ims.entity_tags et ON t.id = et.tag_id WHERE et.entity_type = :entityType AND et.entity_id = :entityId", nativeQuery = true)
    List<Object[]> findTagsForEntityRaw(String entityType, Long entityId);
}
