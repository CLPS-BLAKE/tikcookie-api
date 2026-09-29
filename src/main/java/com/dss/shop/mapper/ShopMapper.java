package com.dss.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.shop.model.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/**
 * shops 表。按分类浏览店铺走 ES，这里只做按 ID 读写；批量取店铺用 selectBatchIds。
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
