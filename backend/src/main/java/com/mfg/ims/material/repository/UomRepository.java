package com.mfg.ims.material.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.material.domain.Uom;

public interface UomRepository extends JpaRepository<Uom, String> {

    List<Uom> findAllByOrderByKindAscCodeAsc();
}
