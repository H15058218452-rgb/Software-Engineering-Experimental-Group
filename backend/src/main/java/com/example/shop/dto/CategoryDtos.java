package com.example.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public interface CategoryDtos {

    /** 类别树节点（一级类别带 children，二级类别 children 为空列表）。 */
    record CategoryNode(String id, String name, Long parentId, Integer sort, List<CategoryNode> children) {
    }

    /** 新建类别：parentId 为空表示建一级类别；非空表示在该一级类别下建二级类别。 */
    record SaveCategoryRequest(
            @NotBlank(message = "类别名称不能为空") @Size(max = 50, message = "类别名称不能超过50字") String name,
            Long parentId,
            Integer sort,
            String requestId) {
    }

    /** 修改类别名称。 */
    record UpdateCategoryRequest(
            @NotBlank(message = "类别名称不能为空") @Size(max = 50, message = "类别名称不能超过50字") String name,
            Integer sort,
            String requestId) {
    }
}
