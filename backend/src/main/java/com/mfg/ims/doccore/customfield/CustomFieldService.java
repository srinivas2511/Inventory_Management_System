package com.mfg.ims.doccore.customfield;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class CustomFieldService {

    private final CustomFieldRepository repo;

    public CustomFieldService(CustomFieldRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldDto> list(String entityType) {
        return repo.findByCompanyIdAndEntityTypeAndActiveTrueOrderBySortOrder(1L, entityType)
                .stream().map(CustomFieldDto::from).toList();
    }

    @Transactional
    public CustomFieldDto create(SaveCustomFieldRequest req) {
        if (repo.existsByCompanyIdAndEntityTypeAndFieldKey(1L, req.entityType(), req.fieldKey())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Custom field key already exists for entity type: " + req.fieldKey());
        }
        CustomFieldDefinition f = new CustomFieldDefinition();
        apply(f, req);
        return CustomFieldDto.from(repo.save(f));
    }

    @Transactional
    public CustomFieldDto update(Long id, SaveCustomFieldRequest req) {
        CustomFieldDefinition f = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Custom field not found: " + id));
        apply(f, req);
        return CustomFieldDto.from(repo.save(f));
    }

    @Transactional
    public void deactivate(Long id) {
        CustomFieldDefinition f = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Custom field not found: " + id));
        f.setActive(false);
        repo.save(f);
    }

    private void apply(CustomFieldDefinition f, SaveCustomFieldRequest req) {
        f.setEntityType(req.entityType().toUpperCase().trim());
        f.setFieldKey(req.fieldKey().trim());
        f.setLabel(req.label().trim());
        f.setFieldType(req.fieldType().toUpperCase().trim());
        f.setOptions(req.options());
        f.setMandatory(req.mandatory());
        f.setShowInList(req.showInList());
        f.setSortOrder(req.sortOrder());
    }
}
