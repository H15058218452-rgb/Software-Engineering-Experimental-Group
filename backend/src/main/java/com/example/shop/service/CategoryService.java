package com.example.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.shop.common.BizException;
import com.example.shop.common.ErrorCode;
import com.example.shop.dto.CategoryDtos;
import com.example.shop.entity.Category;
import com.example.shop.entity.Product;
import com.example.shop.mapper.CategoryMapper;
import com.example.shop.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品两级类别服务。
 *
 * 业务规则（对应产品订单 A.1-2 与澄清记录）：
 * 1. 固定两级，不允许三级：给二级类别建子类直接拒绝；
 * 2. 卖家自行维护，同级名称不可重复；
 * 3. 有子类的一级类别不可删；有商品（含已下架）引用的类别不可删；
 * 4. 发布商品必须挂二级类别（由 ProductService 校验）。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CategoryService(CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    /** 返回两级类别树（含空的二级列表），供买家筛选与卖家发布页使用。 */
    public List<CategoryDtos.CategoryNode> tree() {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort).orderByAsc(Category::getId));
        Map<Long, List<Category>> childrenOf = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                childrenOf.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        roots.sort(Comparator.comparing(Category::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Category::getId));
        List<CategoryDtos.CategoryNode> result = new ArrayList<>(roots.size());
        for (Category root : roots) {
            List<CategoryDtos.CategoryNode> children = new ArrayList<>();
            for (Category child : childrenOf.getOrDefault(root.getId(), List.of())) {
                children.add(toNode(child, List.of()));
            }
            result.add(toNode(root, children));
        }
        return result;
    }

    /** 全部二级类别 ID（用于按一级类别筛选商品时展开）。 */
    public List<Long> childIdsOf(Long parentId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, parentId))
                .stream().map(Category::getId).toList();
    }

    public Category requireCategory(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.CATEGORY_INVALID.getCode(), "类别不存在，请刷新后重试");
        }
        return c;
    }

    /** 校验商品挂载的类别：必须存在且必须是二级类别。 */
    public Category requireLeafCategory(Long id) {
        Category c = requireCategory(id);
        if (c.getParentId() == null) {
            throw new BizException(ErrorCode.CATEGORY_INVALID.getCode(), "商品必须挂在二级类别下");
        }
        return c;
    }

    public String nameOf(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category c = categoryMapper.selectById(categoryId);
        return c == null ? null : c.getName();
    }

    /** 一次性取出全部类别的 id→名称映射，供列表页批量转换，避免 N+1 查询。 */
    public Map<Long, String> nameMap() {
        Map<Long, String> map = new java.util.HashMap<>();
        for (Category c : categoryMapper.selectList(null)) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    @Transactional
    public CategoryDtos.CategoryNode create(CategoryDtos.SaveCategoryRequest req) {
        String name = req.name().trim();
        Long parentId = req.parentId();
        if (parentId != null) {
            Category parent = requireCategory(parentId);
            if (parent.getParentId() != null) {
                throw new BizException(ErrorCode.CATEGORY_INVALID.getCode(), "类别固定为两级，不能在二级类别下再建子类");
            }
        }
        ensureNameUnique(parentId, name, null);
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId);
        c.setSort(req.sort() == null ? 0 : req.sort());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        categoryMapper.insert(c);
        return toNode(c, List.of());
    }

    @Transactional
    public CategoryDtos.CategoryNode update(Long id, CategoryDtos.UpdateCategoryRequest req) {
        Category c = requireCategory(id);
        String name = req.name().trim();
        ensureNameUnique(c.getParentId(), name, id);
        c.setName(name);
        if (req.sort() != null) {
            c.setSort(req.sort());
        }
        c.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(c);
        List<CategoryDtos.CategoryNode> children = categoryMapper.selectList(
                        new LambdaQueryWrapper<Category>().eq(Category::getParentId, id))
                .stream().map(child -> toNode(child, List.of())).toList();
        return toNode(c, children);
    }

    @Transactional
    public void delete(Long id) {
        Category c = requireCategory(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_PRODUCTS.getCode(),
                    "该类别下还有 " + productCount + " 件商品（含已下架），不可删除");
        }
        categoryMapper.deleteById(c.getId());
    }

    private void ensureNameUnique(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<Category> q = new LambdaQueryWrapper<Category>().eq(Category::getName, name);
        if (parentId == null) {
            q.isNull(Category::getParentId);
        } else {
            q.eq(Category::getParentId, parentId);
        }
        if (excludeId != null) {
            q.ne(Category::getId, excludeId);
        }
        Long count = categoryMapper.selectCount(q);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }
    }

    private CategoryDtos.CategoryNode toNode(Category c, List<CategoryDtos.CategoryNode> children) {
        return new CategoryDtos.CategoryNode(
                String.valueOf(c.getId()), c.getName(), c.getParentId(), c.getSort(), children);
    }
}
