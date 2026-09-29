package com.dss.common.constant;

/**
 * 全部 RabbitMQ 名称（vhost /dss），拓扑见 docs/中间件配置.md 第 6 节。
 */
public final class MqNames {

    /** 公共死信交换机（direct），路由键就是死信队列名。 */
    public static final String DLX_EXCHANGE = "dss.dlx";
    public static final String DLQ_SUFFIX = ".dlq";

    /** 订单交换机（direct）。 */
    public static final String ORDER_EXCHANGE = "dss.order.direct";
    /** 抢购落单。 */
    public static final String ORDER_FLASH_CREATE_QUEUE = "order.flash.create";
    public static final String ORDER_FLASH_CREATE_ROUTING_KEY = "order.flash.create";
    /** 超时延迟：队列 TTL 到期后死信转到 order.timeout，没有消费者。 */
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";
    /** 超时取消。 */
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";

    /** 搜索同步交换机（topic）。 */
    public static final String SEARCH_EXCHANGE = "dss.search.topic";
    public static final String SEARCH_SYNC_QUEUE = "search.sync";
    public static final String SEARCH_SHOP_BINDING = "shop.*";
    public static final String SEARCH_PRODUCT_BINDING = "product.*";
    public static final String SEARCH_SHOP_UPSERT_ROUTING_KEY = "shop.upsert";
    public static final String SEARCH_PRODUCT_UPSERT_ROUTING_KEY = "product.upsert";

    private MqNames() {
    }

    /** 某业务队列对应的死信队列名，也是它在 dss.dlx 上的路由键。 */
    public static String dlq(String queue) {
        return queue + DLQ_SUFFIX;
    }
}
