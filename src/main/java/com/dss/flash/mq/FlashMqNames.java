package com.dss.flash.mq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configured namespace permits isolated testing in a shared broker without touching its queues. */
@Component("flashMqNames")
public class FlashMqNames {
    private final String prefix;
    public FlashMqNames(@Value("${dss.flash.mq-prefix:dss.flash}") String prefix) {
        if (prefix==null || !prefix.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("Invalid flash MQ namespace");
        this.prefix=prefix;
    }
    public String exchange() { return prefix+".direct"; }
    public String createQueue() { return prefix+".orders"; }
    public String waitQueue() { return prefix+".payment.wait"; }
    public String timeoutQueue() { return prefix+".payment.timeout"; }
}
