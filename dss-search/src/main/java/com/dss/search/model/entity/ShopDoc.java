package com.dss.search.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺搜索文档（索引 dss_shop）。createIndex / writeTypeHint 的理由同 ProductDoc。字段来源见 docs/中间件配置.md 7.3。
 */
@Data
@Document(indexName = "dss_shop", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ShopDoc {

    /** 店铺 ID 的字符串，同时是 ES 的 _id。 */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String address;

    /** ShopCategory 名。 */
    @Field(type = FieldType.Keyword)
    private String category;

    /** 展示图 fileId，只存不搜。 */
    @Field(type = FieldType.Keyword, index = false)
    private List<String> images;

    @Field(type = FieldType.Keyword, index = false)
    private String businessHours;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
