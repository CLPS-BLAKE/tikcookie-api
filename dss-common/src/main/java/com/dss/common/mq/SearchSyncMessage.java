package com.dss.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索同步消息（路由键 shop.upsert / product.upsert），只带类型和 ID。
 * 消费者从 Mongo 读最新数据后整条覆盖写入 ES，所以消息重复或乱序都不会写错。
 * 放在 common 里，是因为 shop 和 product 都要发它，但它们不能依赖 dss-search。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyncMessage {

    private DocType docType;

    private Long id;
}
