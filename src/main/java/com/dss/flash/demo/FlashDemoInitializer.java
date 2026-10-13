package com.dss.flash.demo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dss.flash.service.*;
import com.dss.product.mapper.ProductMapper;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductType;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="dss.flash",name="enabled",havingValue="true")
public class FlashDemoInitializer implements ApplicationRunner {
    private final FlashDemoDataRepository data;
    private final FlashCacheService cache;
    private final FlashOrderService orders;
    private final ProductMapper products;
    private final ShopService shops;
    private final FlashRuntime runtime;
    private final RabbitListenerEndpointRegistry registry;
    @Override public void run(ApplicationArguments args) {
        runtime.close();
        try {
            data.retirePrevious();
            cache.clearRoundMembership();
            orders.terminatePreviousRound();
            var ids=data.append();
            for (Product p:products.selectList(Wrappers.<Product>lambdaQuery().select(Product::getId).eq(Product::getType,ProductType.FLASH)))
                cache.registerType(p.getId());
            for (Long id:ids) {
                Product p=products.selectById(id);
                cache.initialize(p,shops.getRequiredShop(p.getShopId()));
            }
            runtime.open();
            for (String id:new String[]{"flashOrders","flashPaymentTimeouts"}) {
                var listener=registry.getListenerContainer(id);
                if (listener==null) throw new IllegalStateException("Flash listener is not registered");
                listener.start();
            }
        } catch (RuntimeException e) {
            runtime.close(); registry.stop(); throw e;
        }
    }
}
