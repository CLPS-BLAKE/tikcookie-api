package com.dss.search;

import com.dss.search.model.entity.ProductDoc;
import com.dss.search.model.entity.ShopDoc;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用实际 SDE 映射器验证与部署 mapping 一致的日期格式，不写入线上测试数据。 */
class ElasticsearchDocumentMappingTest {
    @Test
    void deployedDateFormatMapsToLocalDateTimeForBothDocumentTypes() {
        var converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        converter.afterPropertiesSet();
        var product = converter.read(ProductDoc.class, Document.parse("""
                {"id":"1", "type":"FLASH", "flashStartTime":"2026-10-08 12:34:56",
                 "flashEndTime":"2026-10-08 13:34:56"}
                """));
        var shop = converter.read(ShopDoc.class, Document.parse("""
                {"id":"2", "createdAt":"2026-10-08 12:34:56"}
                """));
        assertThat(product.getFlashStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 8, 12, 34, 56));
        assertThat(product.getFlashEndTime()).isEqualTo(product.getFlashStartTime().plusHours(1));
        assertThat(shop.getCreatedAt()).isEqualTo(product.getFlashStartTime());
    }
}
