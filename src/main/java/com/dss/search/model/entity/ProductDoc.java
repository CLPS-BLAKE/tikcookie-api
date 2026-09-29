package com.dss.search.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDateTime;

/**
 * 商品搜索文档（索引 dss_product），字段见 docs/中间件配置.md 5.2；中文字段用 ES 自带的 standard 分析器，不装 IK。
 * createIndex = false：应用启动时不连 ES、不建索引；索引由全量重建（SearchService.rebuildAll）按本类的注解删掉重建。
 * writeTypeHint = FALSE：不往文档里写 _class。
 */
@Data
@Document(indexName = "dss_product", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ProductDoc {

    /** 商品 ID 的字符串，同时是 ES 的 _id。 */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String shopId;

    @Field(type = FieldType.Text)
    private String name;

    /** 套餐内容里所有项目名，用空格拼接（从 products.contents 抽取）。 */
    @Field(type = FieldType.Text)
    private String contentsText;

    /** 店名冗余：店铺改名时，搜索同步会刷新本店所有商品文档。 */
    @Field(type = FieldType.Text)
    private String shopName;

    /** 所属店铺的分类（ShopCategory 名）；店铺改分类时同样刷新。 */
    @Field(type = FieldType.Keyword)
    private String shopCategory;

    /** ProductType 名。 */
    @Field(type = FieldType.Keyword)
    private String type;

    /** ProductStatus 名；下架不删文档，搜索时过滤掉 OFF_SHELF。 */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Integer)
    private Integer soldCount;

    /** 展示图 fileId（OSS ObjectKey），只存不搜；返回时用 FileUrlResolver 拼成 imageUrl。 */
    @Field(type = FieldType.Keyword, index = false)
    private String image;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flashStartTime;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flashEndTime;
}
