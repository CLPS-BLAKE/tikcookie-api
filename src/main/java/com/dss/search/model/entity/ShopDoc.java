package com.dss.search.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDateTime;

/**
 * 店铺搜索文档（索引 dss_shop），字段见 docs/中间件配置.md 5.2；createIndex / writeTypeHint 的理由同 ProductDoc。
 */
@Data
@Document(indexName = "dss_shop", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ShopDoc {

    /** 店铺 ID 的字符串，同时是 ES 的 _id。 */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String address;

    /** ShopCategory 名。 */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 首张展示图 fileId（OSS ObjectKey），只存不搜；返回时拼成 coverUrl。 */
    @Field(type = FieldType.Keyword, index = false)
    private String cover;

    @Field(type = FieldType.Keyword, index = false)
    private String businessHours;

    /** 关键词为空时按它倒序，也就是按分类浏览。 */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
