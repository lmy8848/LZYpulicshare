package com.hgws.sbp.commons.base.controller;

import com.hgws.sbp.commons.enumerate.ResultEnumerate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author zhouhonggang
 * @version 1.0.0
 * @project spring-boot-pro
 * @datetime 2022-07-02 11:39
 * @description: 404接口不存在
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class BaseErrorController extends BasicErrorController {

    @Value("${server.error.path:${error.path:/error}}")
    private String path;

    public BaseErrorController(ServerProperties serverProperties) {
        super(new DefaultErrorAttributes(), serverProperties.getError());
    }

    /**
     * 覆盖默认的JSON响应
     * @return ResponseEntity
     */
    @Override
    public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        // 获取错误信息
        Map<String, Object> originalMsgMap = getErrorAttributes(request, getErrorAttributeOptions(request, MediaType.ALL));
        // 封装错误信息
        Map<String, Object> errors = new HashMap<>();
        errors.put("path", originalMsgMap.get("path"));
        errors.put("error", originalMsgMap.get("error"));
        errors.put("message", originalMsgMap.get("message"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ResultEnumerate.INTERFACE_NOT_FOUND.getCode());
        result.put("message", ResultEnumerate.INTERFACE_NOT_FOUND.getMessage());
        result.put("data", errors);
        // 返回错误信息
        return new ResponseEntity<>(result, getStatus(request));
    }

}
