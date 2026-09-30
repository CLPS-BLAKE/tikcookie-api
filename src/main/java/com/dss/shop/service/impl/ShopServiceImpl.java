package com.dss.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.shop.mapper.ShopMapper;
import com.dss.shop.model.dto.ShopSaveDTO;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.model.enums.ShopCategory;
import com.dss.shop.model.enums.ShopErrorCode;
import com.dss.shop.model.vo.CategoryVO;
import com.dss.shop.model.vo.ShopVO;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 店铺实现。
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final SearchSyncPublisher searchSyncPublisher;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public List<CategoryVO> listCategories() {
        // 分类只存在于枚举里，不入库；按枚举声明顺序返回
        return Arrays.stream(ShopCategory.values())
                .map(category -> new CategoryVO(category, category.getDesc()))
                .toList();
    }

    @Override
    public ShopVO getShop(Long shopId) {
        return toShopVO(getRequiredShop(shopId));
    }

    @Override
    public ShopVO createShop(ShopSaveDTO dto) {
        Shop shop = new Shop();
        shop.setName(dto.getName());
        shop.setAddress(dto.getAddress());
        shop.setCategory(dto.getCategory());
        shop.setImages(dto.getImages());
        shop.setBusinessHours(dto.getBusinessHours());
        shop.setPhone(dto.getPhone());
        shopMapper.insert(shop); // id 自增，createdAt / updatedAt 自动填充
        publishShopChanged(shop.getId());
        return toShopVO(shop);
    }

    @Override
    public ShopVO updateShop(Long shopId, ShopSaveDTO dto) {
        getRequiredShop(shopId); // 不存在：102001

        // 整体覆盖：选填字段不传时要真的清成 NULL。
        // updateById(entity) 会跳过 null 字段，所以用 wrapper 显式 SET；
        // images 是 JSON 列，wrapper 的 set 不会自动带 typeHandler（实体方式才会），要手动指定。
        shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .set(Shop::getName, dto.getName())
                .set(Shop::getAddress, dto.getAddress())
                .set(Shop::getCategory, dto.getCategory())
                .set(Shop::getImages, dto.getImages(), "typeHandler=" + JacksonTypeHandler.class.getName())
                .set(Shop::getBusinessHours, dto.getBusinessHours())
                .set(Shop::getPhone, dto.getPhone())
                .set(Shop::getUpdatedAt, LocalDateTime.now())); // MetaObjectHandler 不作用在 wrapper 更新上
        publishShopChanged(shopId);
        return toShopVO(getRequiredShop(shopId)); // 重新读取，返回覆盖后的最新值
    }

    @Override
    public Shop getRequiredShop(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BizException(ShopErrorCode.SHOP_NOT_FOUND);
        }
        return shop;
    }

    @Override
    public Map<Long, Shop> getShopsByIds(Collection<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Map.of();
        }
        return shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity()));
    }

    /**
     * 发搜索变更消息（shop.changed）。发布器内部已保证：有事务时推迟到提交后发送、发送失败只记日志不向上抛；
     * 本类两个写方法均为单条 insert / update、无 @Transactional，语句提交后立即发送。
     * 消息丢失时按中间件配置 5.1 跑一次"从 MySQL 全量重建 ES"补回。
     */
    private void publishShopChanged(Long shopId) {
        searchSyncPublisher.publishShopChanged(shopId);
    }

    private ShopVO toShopVO(Shop shop) {
        ShopVO vo = new ShopVO();
        vo.setId(String.valueOf(shop.getId()));
        vo.setName(shop.getName());
        vo.setAddress(shop.getAddress());
        vo.setCategory(shop.getCategory());
        vo.setImages(shop.getImages());
        vo.setImageUrls(fileUrlResolver.toUrls(shop.getImages()));
        vo.setBusinessHours(shop.getBusinessHours());
        vo.setPhone(shop.getPhone());
        vo.setCreateTime(shop.getCreatedAt());
        vo.setUpdateTime(shop.getUpdatedAt());
        return vo;
    }
}
