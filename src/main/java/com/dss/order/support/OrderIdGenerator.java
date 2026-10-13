package com.dss.order.support;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;
import java.time.Clock;

/** One generator for every new order. Fixed node (1,1); single application instance only. */
@Component
public class OrderIdGenerator {
    private final IdentifierGenerator generator;
    private final Clock clock;
    private final long startedAt;
    private long lastTime;

    public OrderIdGenerator() {
        this(new DefaultIdentifierGenerator(1L, 1L), Clock.systemUTC());
    }

    public OrderIdGenerator(IdentifierGenerator generator, Clock clock) {
        this.generator = generator;
        this.clock = clock;
        this.startedAt = clock.millis();
        this.lastTime = startedAt;
    }

    public synchronized long nextId() {
        long now = clock.millis();
        if (now < lastTime || now <= startedAt) {
            throw new IllegalStateException("Order ID clock is not advancing; refusing to issue an ID");
        }
        lastTime = now;
        Number number = generator.nextId(null);
        if (number == null || number.longValue() <= 0) {
            throw new IllegalStateException("Order ID generator failed");
        }
        return number.longValue();
    }
}
