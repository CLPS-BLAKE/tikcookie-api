package com.dss.common.result;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页契约（接口文档 1.4）：page 从 1 开始、默认 1；size 默认 10、最大 50。
 * 各模块的查询参数都继承 PageQuery，规则只在这里维护一份。
 */
class PageQueryTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("不传分页参数时默认 page=1、size=10")
    void defaults() {
        PageQuery query = new PageQuery();

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(10);
        assertThat(violations(query)).isEmpty();
    }

    @Test
    @DisplayName("size=50 是上限，允许；size=51 拒绝")
    void sizeBoundary() {
        PageQuery atLimit = new PageQuery();
        atLimit.setSize(50);
        assertThat(violations(atLimit)).isEmpty();

        PageQuery overLimit = new PageQuery();
        overLimit.setSize(51);
        assertThat(violations(overLimit)).contains("size");
    }

    @Test
    @DisplayName("page 从 1 开始：page=0 拒绝，page=1 允许")
    void pageLowerBoundary() {
        PageQuery zero = new PageQuery();
        zero.setPage(0);
        assertThat(violations(zero)).contains("page");

        PageQuery one = new PageQuery();
        one.setPage(1);
        assertThat(violations(one)).isEmpty();
    }

    @Test
    @DisplayName("size=0 也拒绝（每页至少 1 条）")
    void sizeLowerBoundary() {
        PageQuery zero = new PageQuery();
        zero.setSize(0);

        assertThat(violations(zero)).contains("size");
    }

    @Test
    @DisplayName("分页响应字段固定为 list/total/page/size")
    void resultShape() {
        PageResult<String> result = PageResult.of(java.util.List.of("a", "b"), 2L, 1, 10);

        assertThat(result.getList()).containsExactly("a", "b");
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    private Set<String> violations(PageQuery query) {
        return validator.validate(query).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
