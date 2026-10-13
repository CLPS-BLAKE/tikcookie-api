package com.dss.flash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dss.common.exception.BizException;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.file.FileUrlResolver;
import com.dss.flash.model.FlashReservation;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductErrorCode;
import com.dss.product.model.enums.ProductType;
import com.dss.product.model.vo.ProductVO;
import com.dss.product.model.vo.FlashProductVO;
import com.dss.shop.model.entity.Shop;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** No DB/ES fallback, no expiry on transaction records. Metadata refresh never resets counters. */
@Service
public class FlashCacheService {
    public static final String PREFIX = "dss:flash:";
    public static final String CURRENT = PREFIX + "current";
    public static final String VISIBLE = PREFIX + "visible";
    public static final String EXPIRY = PREFIX + "expiry";
    public static final String ORDER_REQUESTS = PREFIX + "orders";
    private static final String QUOTA_READY_FIELD = "_ready";
    private static final DefaultRedisScript<List> RESERVE = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> CONFIRM = new DefaultRedisScript<>();
    private static final DefaultRedisScript<String> RELEASE = new DefaultRedisScript<>();
    static {
        RESERVE.setLocation(new ClassPathResource("lua/flash-reserve.lua")); RESERVE.setResultType(List.class);
        CONFIRM.setLocation(new ClassPathResource("lua/flash-confirm.lua")); CONFIRM.setResultType(String.class);
        RELEASE.setLocation(new ClassPathResource("lua/flash-release.lua")); RELEASE.setResultType(String.class);
    }
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final FileUrlResolver urls;
    private final Clock clock;
    private final Set<Long> knownFlash = ConcurrentHashMap.newKeySet();
    private final Set<Long> paused = ConcurrentHashMap.newKeySet();

    public FlashCacheService(StringRedisTemplate redis, ObjectMapper json, FileUrlResolver urls, Clock clock) {
        this.redis=redis; this.json=json; this.urls=urls; this.clock=clock;
    }
    public void registerType(long productId) { knownFlash.add(productId); }
    public boolean knowsFlash(long id) { return knownFlash.contains(id); }
    public static String meta(long id) { return PREFIX+"product:"+id; }
    public static String stock(long id) { return PREFIX+"stock:"+id; }
    public static String quota(long id) { return PREFIX+"quota:"+id; }
    public static String request(String key) {
        try { return PREFIX+"request:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private long epoch(java.time.LocalDateTime t) { return t.atZone(clock.getZone()).toInstant().toEpochMilli(); }
    public long now() { return clock.millis(); }

    public void pause(long id) {
        paused.add(id);
        try { redis.opsForHash().put(meta(id),"paused","1"); redis.opsForZSet().remove(VISIBLE,String.valueOf(id)); }
        catch (RuntimeException ignored) { /* local barrier stays shut while Redis is down */ }
    }
    public boolean isCurrent(long id) {
        return !paused.contains(id) && Boolean.TRUE.equals(redis.opsForSet().isMember(CURRENT,String.valueOf(id)));
    }
    public Set<String> currentIds() {
        Set<String> ids=redis.opsForSet().members(CURRENT); return ids==null?Set.of():ids;
    }
    public void clearRoundMembership() {
        redis.delete(CURRENT); redis.delete(VISIBLE); // only round indexes, never stock/quota/requests
    }
    public void initialize(Product p, Shop s) {
        registerType(p.getId());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(stock(p.getId()),String.valueOf(p.getStock())))) {
            throw new IllegalStateException("Refusing to reinitialize existing flash stock");
        }
        if (!Boolean.TRUE.equals(redis.opsForHash().putIfAbsent(quota(p.getId()),QUOTA_READY_FIELD,"1"))) {
            throw new IllegalStateException("Refusing to reinitialize existing flash quota");
        }
        redis.opsForHash().putAll(meta(p.getId()),Map.of("auto","0","paused","0","ready","0"));
        refresh(p,s);
        redis.opsForSet().add(CURRENT,String.valueOf(p.getId()));
    }
    public void refresh(Product p, Shop s) {
        registerType(p.getId());
        ProductVO vo=new ProductVO();
        vo.setId(String.valueOf(p.getId())); vo.setShopId(String.valueOf(p.getShopId())); vo.setShopName(s.getName()); vo.setShopAddress(s.getAddress());
        vo.setName(p.getName()); vo.setContents(p.getContents()); vo.setPrice(p.getPrice()); vo.setType(ProductType.FLASH);
        vo.setImage(p.getImage()); vo.setImageUrl(urls.toUrl(p.getImage())); vo.setStatus(p.getStatus()); vo.setSoldCount(p.getSoldCount());
        vo.setValidDays(p.getValidDays()); vo.setUseRules(p.getUseRules()); vo.setFlashStartTime(p.getFlashStartTime());
        vo.setFlashEndTime(p.getFlashEndTime()); vo.setLimitPerUser(p.getLimitPerUser()); vo.setCreateTime(p.getCreatedAt()); vo.setUpdateTime(p.getUpdatedAt());
        try {
            if (redis.opsForValue().get(stock(p.getId()))==null || !quotaReady(p.getId())
                    || !validAuto(redis.opsForHash().get(meta(p.getId()),"auto"))) throw unavailable();
            redis.opsForHash().putAll(meta(p.getId()),Map.of("data",json.writeValueAsString(vo),"status",p.getStatus().name(),
                    "start",String.valueOf(epoch(p.getFlashStartTime())),"end",String.valueOf(epoch(p.getFlashEndTime())),"limit",String.valueOf(p.getLimitPerUser()),"ready","1"));
            synchronizeVisibility(p.getId());
        } catch (Exception e) { pause(p.getId()); throw unavailable(); }
    }
    public boolean cacheReady(long id) {
        Map<Object,Object> m=redis.opsForHash().entries(meta(id));
        return !paused.contains(id) && "1".equals(m.get("ready")) && "0".equals(m.get("paused"))
                && validAuto(m.get("auto")) && quotaReady(id) && m.get("data")!=null && redis.opsForValue().get(stock(id))!=null;
    }
    private boolean quotaReady(long id) { return "1".equals(redis.opsForHash().get(quota(id),QUOTA_READY_FIELD)); }
    private static boolean validAuto(Object value) { return "0".equals(value) || "1".equals(value); }
    public void configureBeforeStartStock(long id, int value) {
        // Must be called under coordinator and only before start, after DB commit.
        if (!cacheReady(id)) throw unavailable();
        redis.opsForValue().set(stock(id),String.valueOf(value));
    }
    public void synchronizeVisibility(long id) {
        String raw=redis.opsForValue().get(stock(id)); Map<Object,Object> m=redis.opsForHash().entries(meta(id));
        boolean show=isCurrent(id) && raw!=null && Integer.parseInt(raw)>0 && "1".equals(m.get("ready")) && "0".equals(m.get("paused"))
                && validAuto(m.get("auto")) && quotaReady(id)
                && "ON_SHELF".equals(m.get("status")) && m.get("start")!=null && m.get("end")!=null
                && now()>=Long.parseLong(m.get("start").toString()) && now()<Long.parseLong(m.get("end").toString());
        if (show) redis.opsForZSet().add(VISIBLE,String.valueOf(id),Long.parseLong(m.get("start").toString()));
        else redis.opsForZSet().remove(VISIBLE,String.valueOf(id));
    }
    public boolean autoShelfDone(long id) {
        Object auto=redis.opsForHash().get(meta(id),"auto");
        if (!validAuto(auto)) { pause(id); throw unavailable(); }
        return "1".equals(auto);
    }
    public void markAutoShelfDone(long id) { redis.opsForHash().put(meta(id),"auto","1"); }

    public ProductVO detail(long id) {
        return detail(id, null);
    }
    /** User quota is a read-only live snapshot, never part of shared product metadata. */
    public ProductVO detail(long id, Long userId) {
        try {
            if (!isCurrent(id) || !cacheReady(id)) throw unavailable();
            Map<Object,Object> m=redis.opsForHash().entries(meta(id));
            if (!"ON_SHELF".equals(m.get("status")) || now()<Long.parseLong(m.get("start").toString()) || now()>=Long.parseLong(m.get("end").toString()))
                throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
            ProductVO vo=json.readValue(m.get("data").toString(),ProductVO.class);
            vo.setRemainingStock(Integer.valueOf(redis.opsForValue().get(stock(id))));
            // Always clear personal fields, even if an old cache payload contained them.
            vo.setPurchaseCount(null); vo.setRemainingQuota(null); vo.setPurchaseLimitReached(null);
            if (userId != null) {
                Object raw=redis.opsForHash().get(quota(id),String.valueOf(userId));
                String count=raw==null?"0":raw.toString();
                if (!count.matches("0|[1-9][0-9]*")) throw new IllegalStateException("Invalid flash quota");
                int used=Integer.parseInt(count);
                Integer limit=vo.getLimitPerUser();
                if (limit==null || limit<1) throw new IllegalStateException("Invalid flash purchase limit");
                vo.setPurchaseCount(used); vo.setRemainingQuota(Math.max(0,limit-used));
                vo.setPurchaseLimitReached(used>=limit);
            }
            vo.setStatus(ProductStatus.ON_SHELF); return vo;
        } catch (BizException e) { throw e; }
        catch (Exception e) { pause(id); throw unavailable(); }
    }
    public List<FlashProductVO> list() {
        try {
            Set<String> ids=redis.opsForZSet().range(VISIBLE,0,-1); List<FlashProductVO> result=new ArrayList<>();
            if (ids==null) return result;
            for (String raw:ids) {
                long id=Long.parseLong(raw);
                try {
                    ProductVO p=detail(id); if (p.getRemainingStock()<=0) continue;
                    FlashProductVO v=new FlashProductVO(); v.setId(p.getId()); v.setShopId(p.getShopId()); v.setShopName(p.getShopName());
                    v.setName(p.getName()); v.setPrice(p.getPrice()); v.setImageUrl(p.getImageUrl()); v.setRemainingStock(p.getRemainingStock());
                    v.setFlashStartTime(p.getFlashStartTime()); v.setFlashEndTime(p.getFlashEndTime()); v.setLimitPerUser(p.getLimitPerUser()); v.setOngoing(true); result.add(v);
                } catch (BizException e) { redis.opsForZSet().remove(VISIBLE,raw); }
            }
            return result;
        } catch (RuntimeException e) { throw unavailable(); }
    }
    public FlashReservation reserve(long user, long product, String key, String order) {
        try {
            String req=request(key);
            if (paused.contains(product) && findRequest(req)==null) throw unavailable();
            List<?> r=redis.execute(RESERVE,List.of(CURRENT,VISIBLE,meta(product),stock(product),quota(product),req,EXPIRY,ORDER_REQUESTS),
                    String.valueOf(user),String.valueOf(product),order,String.valueOf(now()),"30000");
            if (r==null || r.isEmpty()) throw unavailable();
            String code=r.get(0).toString();
            if (!"OK".equals(code)) throw switch(code) {
                case "SOLD_OUT" -> new BizException(ProductErrorCode.FLASH_SOLD_OUT);
                case "LIMIT" -> new BizException(ProductErrorCode.FLASH_LIMIT_EXCEEDED);
                case "NOT_STARTED" -> new BizException(ProductErrorCode.FLASH_NOT_STARTED);
                case "ENDED","OLD_ROUND" -> new BizException(ProductErrorCode.FLASH_ENDED);
                case "OFF_SHELF" -> new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
                case "CONFLICT" -> new BizException(CommonErrorCode.BAD_REQUEST,"requestKey cannot be rebound");
                default -> { pause(product); yield unavailable(); }
            };
            return new FlashReservation(req,user,product,r.get(1).toString(),Long.parseLong(r.get(2).toString()),Long.parseLong(r.get(3).toString()),"PENDING","1".equals(r.get(4).toString()));
        } catch (BizException e) { throw e; }
        catch (RuntimeException e) { pause(product); throw unavailable(); }
    }
    public FlashReservation findRequest(String req) {
        Map<Object,Object> r=redis.opsForHash().entries(req); if (r.isEmpty()) return null;
        return new FlashReservation(req,Long.parseLong(r.get("user").toString()),Long.parseLong(r.get("product").toString()),r.get("order").toString(),
                Long.parseLong(r.get("accepted").toString()),Long.parseLong(r.get("deadline").toString()),r.get("state").toString(),false);
    }
    public String requestForOrder(String order) { Object r=redis.opsForHash().get(ORDER_REQUESTS,order); return r==null?null:r.toString(); }
    public Set<String> expiredRequests(int count) {
        return expiredRequests(0,count);
    }
    /** Read one score-range page without changing request deadlines or index membership. */
    public Set<String> expiredRequests(long offset,int count) {
        Set<String> r=redis.opsForZSet().rangeByScore(EXPIRY,0,now(),offset,count); return r==null?Set.of():r;
    }
    public Set<String> pendingRequests(int count) {
        Set<String> r=redis.opsForZSet().range(EXPIRY,0,count-1); return r==null?Set.of():r;
    }
    public void confirm(FlashReservation r) {
        String status=redis.execute(CONFIRM,List.of(r.requestId(),EXPIRY),r.orderId());
        if (!"CREATED".equals(status)) { pause(r.productId()); throw unavailable(); }
    }
    public void release(FlashReservation r, boolean cancelled) {
        String status=redis.execute(RELEASE,List.of(CURRENT,VISIBLE,meta(r.productId()),stock(r.productId()),quota(r.productId()),r.requestId(),EXPIRY),
                String.valueOf(r.userId()),String.valueOf(r.productId()),String.valueOf(now()),cancelled?"CANCEL":"TERMINATE");
        if (!Set.of("RELEASED","ALREADY","CREATED").contains(status==null?"":status)) { pause(r.productId()); throw unavailable(); }
    }
    public static BizException unavailable() { return new BizException(CommonErrorCode.SYSTEM_ERROR,"Flash service unavailable; no synchronous fallback"); }
}
