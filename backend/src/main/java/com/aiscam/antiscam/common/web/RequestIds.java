package com.aiscam.antiscam.common.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestIds {
    private RequestIds() {}

    public static String current() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return (String) attributes.getRequest().getAttribute("requestId");
    }
}
