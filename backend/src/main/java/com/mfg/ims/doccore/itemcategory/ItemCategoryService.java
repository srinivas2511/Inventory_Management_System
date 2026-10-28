package com.mfg.ims.doccore.itemcategory;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class ItemCategoryService {

    private final ItemCategoryRepository repo;

    public ItemCategoryService(ItemCategoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ItemCategoryDto> list() {
        return repo.findByCompanyIdAndActiveTrueOrderByCategoryCode(1L)
                .stream().map(ItemCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ItemCategoryDto findById(Long id) {
        return repo.findById(id).map(ItemCategoryDto::from)
                .orElseThrow(() -> new NotFoundException("Item category not found: " + id));
    }

    @Transactional
    public ItemCategoryDto create(SaveItemCategoryRequest req) {
        if (repo.existsByCompanyIdAndCategoryCode(1L, req.categoryCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Item category code already exists: " + req.categoryCode());
        }
        ItemCategory c = new ItemCategory();
        c.setCategoryCode(req.categoryCode().toUpperCase().trim());
        c.setName(req.name().trim());
        c.setParentId(req.parentId());
        c.setItemType(req.itemType() != null ? req.itemType() : "BOTH");
        return ItemCategoryDto.from(repo.save(c));
    }

    @Transactional
    public ItemCategoryDto update(Long id, SaveItemCategoryRequest req) {
        ItemCategory c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item category not found: " + id));
        c.setName(req.name().trim());
        c.setParentId(req.parentId());
        if (req.itemType() != null) c.setItemType(req.itemType());
        return ItemCategoryDto.from(repo.save(c));
    }

    @Transactional
    public void deactivate(Long id) {
        ItemCategory c = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Item category not found: " + id));
        c.setActive(false);
        repo.save(c);
    }
}
