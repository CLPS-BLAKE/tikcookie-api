package com.dss.common.constant;

/**
 * 全部 RabbitMQ 名称（vhost /dss），拓扑见 docs/中间件配置.md 5.1。只有搜索同步用 MQ。
 */
public final class MqNames {

    /** 搜索同步交换机（direct）。 */
    public static final String SEARCH_EXCHANGE = "dss.search.direct";
    public static final String SHOP_CHANGED_ROUTING_KEY = "shop.changed";
    public static final String PRODUCT_CHANGED_ROUTING_KEY = "product.changed";

    /** 搜索同步队列，绑定上面两个路由键，单消费者顺序处理。 */
    public static final String SEARCH_SYNC_QUEUE = "dss.search.sync";

    /** 失败交换机（direct）和死信队列：处理失败的消息暂存在这里，供组员检查；路由键就是死信队列名。 */
    public static final String SEARCH_DLX = "dss.search.dlx";
    public static final String SEARCH_DLQ = "dss.search.dlq";

    private MqNames() {
    }
}
