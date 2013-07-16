package com.youmag.logback.appenders.flume;

import ch.qos.logback.core.status.*;

/**
 * Created with IntelliJ IDEA.
 * User: chris
 * Date: 16/07/2013
 * Time: 15:16
 * To change this template use File | Settings | File Templates.
 */
public class StatusLogger {
    private StatusManager statusManager;
    private Object declaredOrigin;

    public StatusLogger (StatusManager statusManager, Object declaredOrigin) {
        this.statusManager = statusManager;
        this.declaredOrigin = declaredOrigin;
    }



    protected Object getDeclaredOrigin() {
        return declaredOrigin;
    }

    public void addStatus(Status status) {
        if (statusManager != null) {
            statusManager.add(status);
        }
    }

    public void addInfo(String msg) {
        addStatus(new InfoStatus(msg, getDeclaredOrigin()));
    }

    public void addInfo(String msg, Throwable ex) {
        addStatus(new InfoStatus(msg, getDeclaredOrigin(), ex));
    }

    public void addWarn(String msg) {
        addStatus(new WarnStatus(msg, getDeclaredOrigin()));
    }

    public void addWarn(String msg, Throwable ex) {
        addStatus(new WarnStatus(msg, getDeclaredOrigin(), ex));
    }

    public void addError(String msg) {
        addStatus(new ErrorStatus(msg, getDeclaredOrigin()));
    }

    public void addError(String msg, Throwable ex) {
        addStatus(new ErrorStatus(msg, getDeclaredOrigin(), ex));
    }
}
