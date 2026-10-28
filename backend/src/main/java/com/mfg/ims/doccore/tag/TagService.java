package com.mfg.ims.doccore.tag;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class TagService {

    private final TagRepository repo;

    public TagService(TagRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TagDto> list() {
        return repo.findByCompanyIdOrderByName(1L).stream().map(TagDto::from).toList();
    }

    @Transactional
    public TagDto create(String name, String color) {
        if (repo.existsByCompanyIdAndName(1L, name)) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Tag already exists: " + name);
        }
        Tag t = new Tag();
        t.setName(name.trim());
        t.setColor(color);
        return TagDto.from(repo.save(t));
    }

    @Transactional
    public TagDto update(Long id, String name, String color) {
        Tag t = repo.findById(id).orElseThrow(() -> new NotFoundException("Tag not found: " + id));
        t.setName(name.trim());
        t.setColor(color);
        return TagDto.from(repo.save(t));
    }

    @Transactional
    public void delete(Long id) {
        repo.findById(id).orElseThrow(() -> new NotFoundException("Tag not found: " + id));
        repo.deleteById(id);
    }

    @Transactional
    public void addToEntity(Long tagId, String entityType, Long entityId, Long userId) {
        repo.findById(tagId).orElseThrow(() -> new NotFoundException("Tag not found: " + tagId));
        repo.addTagToEntity(tagId, entityType, entityId, userId);
    }

    @Transactional
    public void removeFromEntity(Long tagId, String entityType, Long entityId) {
        repo.removeTagFromEntity(tagId, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<TagDto> getForEntity(String entityType, Long entityId) {
        return repo.findTagsForEntityRaw(entityType, entityId)
                .stream()
                .map(row -> new TagDto(((Number) row[0]).longValue(), (String) row[1], (String) row[2]))
                .toList();
    }
}
