package com.hgws.sbp.configurations.druid;

import com.alibaba.druid.spring.boot.autoconfigure.properties.DruidStatProperties;
import com.alibaba.druid.support.http.StatViewServlet;
import com.alibaba.druid.support.http.WebStatFilter;
import com.alibaba.druid.util.Utils;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhouhonggang
 * @version 1.0.0
 * @project spring-boot-pro
 * @datetime 2022-07-02 11:38
 * @description: Druid数据源管理
 */
@Configuration
public class DruidConfiguration {

    /**
     * 监控后台数据源Servlet
     * @return ServletRegistrationBean
     */
    @Bean
    public ServletRegistrationBean<StatViewServlet> statViewServlet(){
        ServletRegistrationBean<StatViewServlet> bean = new ServletRegistrationBean<>(new StatViewServlet(),"/druid/*");
        //初始化登陆参数
        Map<String,String> initParams = new HashMap<>();
        //访问账号
        initParams.put("loginUsername", "spring");
        //访问密码
        initParams.put("loginPassword", "boot");
        /*
         * allow: ip白名单
         *    initParams.put("allow",""); 这个值为空或没有就允许所有人访问，ip白名单
         *    initParams.put("allow","localhost");  只允许本机访问，多个ip用逗号,隔开
         * deny: ip黑名单
         *    initParams.put("deny","192.168.1.109"); 拒绝192.168.1.109访问
         *
         * 如果deny和allow同时存在优先deny
         */
        initParams.put("allow", "");
        initParams.put("deny", "");
        //禁用HTML页面的Reset按钮
        initParams.put("resetEnable", "false");
        bean.setInitParameters(initParams);
        return bean;
    }

    /**
     * 配置 Druid 监控 之  web 监控的 filter
     * WebStatFilter：用于配置Web和Druid数据源之间的管理关联监控统计
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<WebStatFilter> webStatFilter(DruidStatProperties properties){
        final String filePath = "support/http/resources/js/common.js";

        // 获取web监控页面的参数
        DruidStatProperties.StatViewServlet config = properties.getStatViewServlet();
        // 提取common.js的配置路径
        String pattern = config.getUrlPattern() != null ? config.getUrlPattern() : "/druid/*";
        String jsPattern = pattern.replaceAll("\\*", "js/common.js");

        //创建filter进行过滤
        Filter filter = (req, rep, chain) -> {
            chain.doFilter(req, rep);
            // 重置缓冲区，响应头不会被重置
            rep.resetBuffer();
            // 获取common.js
            String text = Utils.readFromResource(filePath);
            // 正则替换, 除去底部的广告信息
            text = text.replaceAll("<a.*?banner\"></a><br/>", "");
            text = text.replaceAll("powered.*?shrek.wang</a>", "");
            rep.getWriter().write(text);
        };
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns(jsPattern);
        return registrationBean;
    }

}

