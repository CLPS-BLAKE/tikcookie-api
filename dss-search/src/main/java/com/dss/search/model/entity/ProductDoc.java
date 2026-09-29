package com.dss.search.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDateTime;

/**
 * 商品搜索文档（索引 dss_product）。
 * 索引结构以 docs/中间件配置.md 7.2 的 mapping 为准，由部署方按 7.4 创建，所以 createIndex = false；
 * mapping 是 strict 的，所以 writeTypeHint = FALSE（不写 _class）。字段来源见 docs/中间件配置.md 7.2。
 */
@Data
@Document(indexName = "dss_product", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ProductDoc {

    /** 商品 ID 的字符串，同时是 ES 的 _id。 */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /** 套餐内容里所有项目名，用空格拼接。 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String contentsText;

    @Field(type = FieldType.Keyword)
    private String shopId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String shopName;

    /** 所属店铺的分类（ShopCategory 名）。 */
    @Field(type = FieldType.Keyword)
    private String shopCategory;

    /** ProductType 名。 */
    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Integer)
    private Integer soldCount;

    /** ProductStatus 名；搜索时只返回 ON_SHELF。 */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flashStartTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flashEndTime;

    /** 展示图 fileId，只存不搜。 */
    @Field(type = FieldType.Keyword, index = false)
    private String image;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
