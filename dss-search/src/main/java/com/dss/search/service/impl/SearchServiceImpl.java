package com.dss.search.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.product.service.ProductService;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;
import com.dss.search.repository.ProductDocRepository;
import com.dss.search.repository.ShopDocRepository;
import com.dss.search.service.SearchService;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/**
 * 搜索实现（骨架期是桩）。复杂查询用 ElasticsearchOperations + NativeQuery，简单读写用 Repository。
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDocRepository productDocRepository;
    private final ShopDocRepository shopDocRepository;
    private final ShopService shopService;
    private final ProductService productService;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public PageResult<ProductHitVO> searchProducts(ProductSearchQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public PageResult<ShopHitVO> searchShops(ShopSearchQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public void sync(SearchSyncMessage message) {
        throw new NotImplementedException();
    }
}
