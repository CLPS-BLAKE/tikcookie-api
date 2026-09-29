package com.dss.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索同步消息，如 {"type":"PRODUCT","id":"123"}（路由键 shop.changed / product.changed），只带类型和 ID。
 * 消费者按 ID 从 MySQL 读当前记录后整条覆盖写入 ES，所以消息重复或乱序都不会写错；不能拿它当订单写入依据。
 * 放在 common 里，是因为 shop 和 product 都要发它，而按包的依赖约定，它们不能调用 search 包。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyncMessage {

    private DocType type;

    /** 店铺或商品 ID 的十进制字符串。 */
    private String id;
}
