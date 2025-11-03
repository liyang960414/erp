package com.sambound.erp.repository;

import com.sambound.erp.entity.Material;

import java.util.List;

public interface MaterialRepositoryCustom {
    List<Material> batchInsertOrGetByCode(List<MaterialRepository.MaterialBatchData> batchData);
}

