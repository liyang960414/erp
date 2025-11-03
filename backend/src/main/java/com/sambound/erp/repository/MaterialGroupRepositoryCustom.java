package com.sambound.erp.repository;

import com.sambound.erp.entity.MaterialGroup;

import java.util.List;
import java.util.Map;

public interface MaterialGroupRepositoryCustom {
    Map<String, MaterialGroup> batchInsertOrGetByCode(List<String> codes, List<String> names);
    
    Map<String, MaterialGroup> batchInsertOrGetByCodeWithParent(
            List<MaterialGroupRepository.MaterialGroupBatchData> batchData);
}

