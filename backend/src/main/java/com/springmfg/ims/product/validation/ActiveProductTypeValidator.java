package com.springmfg.ims.product.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.springmfg.ims.product.repository.ProductTypeRepository;

public class ActiveProductTypeValidator implements ConstraintValidator<ActiveProductType, String> {

    private final ProductTypeRepository repo;

    public ActiveProductTypeValidator(ProductTypeRepository repo) {
        this.repo = repo;
    }

    /** Blank is left to @NotBlank so the error stays a single, accurate message. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || repo.existsByCodeAndActiveTrue(value);
    }
}
