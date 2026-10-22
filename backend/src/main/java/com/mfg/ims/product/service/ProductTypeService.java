package com.mfg.ims.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.product.dto.ProductTypeDto;
import com.mfg.ims.product.repository.ProductTypeRepository;

@Service
public class ProductTypeService {

    private final ProductTypeRepository repo;

    public ProductTypeService(ProductTypeRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ProductTypeDto> listActive() {
        return repo.findByActiveTrueOrderByDisplayOrderAscCodeAsc().stream().map(ProductTypeDto::from).toList();
    }
}
