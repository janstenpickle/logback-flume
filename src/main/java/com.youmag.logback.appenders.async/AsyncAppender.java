package com.youmag.logback.appenders.async;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Created with IntelliJ IDEA.
 * User: chris
 * Date: 16/07/2013
 * Time: 15:57
 * To change this template use File | Settings | File Templates.
 */
public class AsyncAppender extends ch.qos.logback.classic.AsyncAppender {
    protected boolean isDiscardable(ILoggingEvent event) {
        return true;
    }
}
