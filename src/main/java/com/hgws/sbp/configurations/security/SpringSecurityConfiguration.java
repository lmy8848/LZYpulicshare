package com.hgws.sbp.configurations.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hgws.sbp.commons.annotation.AccessOperation;
import com.hgws.sbp.commons.base.result.Result;
import com.hgws.sbp.commons.constant.Constant;
import com.hgws.sbp.commons.enumerate.ResultEnumerate;
import com.hgws.sbp.commons.enumerate.TypeEnumerate;
import com.hgws.sbp.commons.utils.JwtUtils;
import com.hgws.sbp.components.properties.SpringSecurityProperties;
import com.hgws.sbp.components.redis.RedisComponent;
import com.hgws.sbp.components.result.ResponseResult;
import com.hgws.sbp.modules.system.logs.service.LogsService;
import com.hgws.sbp.modules.system.user.entity.SystemUser;
import com.hgws.sbp.modules.system.user.service.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author zhouhonggang
 * @version 1.0.0
 * @project spring-boot-pro
 * @datetime 2022-07-02 16:24
 * @description: SpringSecurity
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SpringSecurityConfiguration {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LogsService logsService;

    @Autowired
    private SystemUserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisComponent redisComponent;

    @Autowired
    public PasswordEncoder passwordEncoder;

    @Autowired
    private SpringSecurityProperties springSecurityProperties;

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * UserDetailsService????????????
     * ??????AuthenticationProvider???DaoAuthenticationProvider??????????????????
     * @return UserDetailsService
     */
    public UserDetailsService userDetailsService()
    {
        return username -> {
            SystemUser user = userService.loadUserByUsername(username);
            boolean accountNonLocked = true;
            if(ObjectUtils.isEmpty(user)) {
                throw new UsernameNotFoundException(Constant.USER_NOT_FOUND);
            } else if(user.getLocked() == 1) {
                accountNonLocked = false;
            }
            //throw new LockedException("???????????????");
            List<GrantedAuthority> authorities = new ArrayList<>();
            return new org.springframework.security.core.userdetails.User(user.getName(), user.getPass(), true, true, true, accountNonLocked, authorities);
        };
    }

    /**
     * ??????????????????
     * @return UserDetailsChecker
     */
    public UserDetailsChecker userDetailsChecker()
    {
        return details -> {
            if(!details.isAccountNonLocked()) {
                throw new LockedException(Constant.USER_WAS_LOCKED);
            }
        };
    }

    /**
     * DaoAuthenticationProvider????????????
     * @return DaoAuthenticationProvider
     * @throws Exception Exception
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        //???????????????????????????
        provider.setHideUserNotFoundExceptions(false);
        //?????????????????????
        provider.setPasswordEncoder(passwordEncoder);
        //??????????????????
        provider.setUserDetailsService(userDetailsService());
        //??????????????????
        provider.setPreAuthenticationChecks(userDetailsChecker());
        provider.afterPropertiesSet();
        return provider;
    }

    /**
     * ??????SpringSecurity??????????????????
     * @param http  HttpSecurity
     * @param configuration AuthenticationConfiguration
     * @return SecurityFilterChain
     * @throws Exception Exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationConfiguration configuration) throws Exception {
        return http
            // ???????????????????????????csrf[???????????????cookie]
            .csrf().disable()
            // ?????????????????????????????????
            .cors()
                .and()
            // ??????????????????: ????????????
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
            // ?????????????????????????????????
            .authorizeRequests()
                // ???????????????????????????
                .antMatchers(getAnonymousUrls()).permitAll()
                // ???????????????
                .antMatchers(springSecurityProperties.getWhiteList()).permitAll()
                // ????????????????????????????????????
                .anyRequest().authenticated()
                .and()
            // ???????????????401 403????????????
            .exceptionHandling()
                .authenticationEntryPoint((request, response, exception) -> {
                    // ???????????????
                    ResultEnumerate enumerate = ResultEnumerate.LOGIN_NOT_LOGGED;
                    // ????????????????????????
                    ResponseResult.result(Result.failure(enumerate), HttpStatus.FORBIDDEN.value());
                    // ????????????
                    logsService.insert(0, Constant.UNLOGGED_ACCESS, TypeEnumerate.SELECT.getValue(), enumerate.getMessage(), null, null);
                })
                .accessDeniedHandler((request, response, exception) -> {
                    // ???????????????
                    ResultEnumerate enumerate = ResultEnumerate.UNAUTHORIZED_ACCESS;
                    // ????????????????????????
                    ResponseResult.result(Result.failure(enumerate), HttpStatus.UNAUTHORIZED.value());
                    // ????????????
                    logsService.insert(0, Constant.UNAUTHORIZED_ACCESS, TypeEnumerate.SELECT.getValue(), enumerate.getMessage(), null, null);
                })
                .and()
            // ??????????????????
            .authenticationProvider(daoAuthenticationProvider())
            /*
             * FilterChain ???????????? []
             * UsernamePasswordAuthenticationFilter???AbstractAuthenticationProcessingFilter?????????????????????????????????????????????????????????????????????????????????
             * ?????????????????????pattern???"/login"????????????POST???????????????????????????????????????????????????authenticationManager??????????????????
             */
            .addFilter(new UsernamePasswordAuthenticationFilter(configuration.getAuthenticationManager()){
                /**
                 * ???????????????????????????
                 * @param request   ??????
                 * @param response  ??????
                 * @return  Authentication
                 * @throws AuthenticationException AuthenticationException
                 */
                @Override
                public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
                    // ???????????????POST????????????
                    if ("POST".equals(request.getMethod())) {
                        try {
                            // ??????????????? Content-Type: application/json ??????
                            InputStream inputStream = request.getInputStream();
                            var params = objectMapper.readValue(inputStream, Map.class);
                            // ??????SpringSecurity????????????
                            UsernamePasswordAuthenticationToken authenticationToken =
                                    new UsernamePasswordAuthenticationToken(params.get("username"), params.get("password"));
                            return this.getAuthenticationManager().authenticate(authenticationToken);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    return super.attemptAuthentication(request, response);
                }
                /**
                 * ????????????
                 * @param request   ??????
                 * @param response  ??????
                 * @param exception ??????
                 */
                @Override
                protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
                    // ??????????????????
                    SecurityContextHolder.clearContext();
                    // ????????????????????????
                    ResultEnumerate enumerate = ResultEnumerate.LOGIN_OTHER_ERROR;
                    if(exception instanceof UsernameNotFoundException) {
                        //enumerate = ResultEnumerate.LOGIN_USER_NOT_EXIST;
                        enumerate = ResultEnumerate.LOGIN_USER_PASS_ERROR;
                    } else if(exception instanceof BadCredentialsException) {
                        //enumerate = ResultEnumerate.LOGIN_PASS_INPUT_ERROR;
                        enumerate = ResultEnumerate.LOGIN_USER_PASS_ERROR;
                    } else if(exception instanceof LockedException) {
                        enumerate = ResultEnumerate.LOGIN_USER_LOCKED;
                    }
                    // ????????????????????????
                    ResponseResult.result(Result.failure(enumerate));
                }
                /**
                 * ????????????
                 * @param request   ??????
                 * @param response  ??????
                 * @param chain ????????????
                 * @param authentication ????????????
                 */
                @Override
                protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) {
                    // ??????????????????
                    User user =
                            (User) authentication.getPrincipal();
                    // ??????????????????
                    String username = user.getUsername();
                    // ??????????????????????????????
                    SystemUser entity = userService.loadUserByUsername(username);

                    // ??????????????????????????????
                    List<String> authorities = userService.loadUserAuthorities(username);
                    redisComponent.set(Constant.AUTHORITIES_KEY+username, authorities);

                    String accessToken = jwtUtils.accessToken(entity.getId(), entity.getName());
                    String refreshToken = jwtUtils.refreshToken(entity.getId(), entity.getName());
                    // ????????????????????????
                    ResultEnumerate enumerate = ResultEnumerate.LOGIN_SUCCESS;
                    ResponseResult.result(Result.success(enumerate, Map.of(
                            "access_token", accessToken,
                            "refresh_token", refreshToken)));
                }
            })
            /*
             * ??????????????? ????????????
             */
            .addFilter(new BasicAuthenticationFilter(configuration.getAuthenticationManager()) {
                // ??????????????????????????????????????????
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                    // ?????????????????????token
                    String token = request.getHeader(springSecurityProperties.getJwt().getHeader());
                    // ??????token???????????????
                    String prefix = springSecurityProperties.getJwt().getPrefix();
                    if(StringUtils.hasLength(token) && token.startsWith(prefix)) {
                        // ???????????????token
                        String realToken = token.substring(prefix.length());
                        // ??????token??????????????????
                        if(jwtUtils.isExpiration(realToken)) {
                            ResultEnumerate enumerate = ResultEnumerate.TOKEN_ALREADY_EXPIRED;
                            ResponseResult.result(Result.success(enumerate), HttpStatus.FORBIDDEN.value());
                            return;
                        } else {
                            String username = jwtUtils.getUsername(realToken);
                            // ??????????????????????????????
                            Collection<SimpleGrantedAuthority> authoritiesList = new ArrayList<>();

                            List<String> authorities = (List<String>)redisComponent.get(Constant.AUTHORITIES_KEY+username);
                            if(!ObjectUtils.isEmpty(authorities))
                            {
                                authorities.forEach(code -> {
                                    authoritiesList.add(new SimpleGrantedAuthority(code));
                                });
                            }

                            // ??????????????????token??????
                            UsernamePasswordAuthenticationToken authenticationToken =
                                    new UsernamePasswordAuthenticationToken(username, null, authoritiesList);
                            // ??????SpringSecurity?????????
                            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        }
                    }
                    super.doFilterInternal(request, response, chain);
                }
            })
            // ????????????????????????
            .logout()
                .logoutSuccessHandler((request, response, authentication) -> {
                    // ??????????????????token???????????????????????????
                    // ??????security???????????????
                    SecurityContextHolder.clearContext();
                    // ????????????????????????
                    ResultEnumerate enumerate = ResultEnumerate.LOGOUT_SUCCESS;
                    ResponseResult.result(Result.success(enumerate));
                })
            .and()
            .build();
    }

    /**
     * ??????????????????????????????????????? {@link AccessOperation}
     * ??????SpringSecurityFilterChain, ????????????SpringSecurityContextHolder
     * @return WebSecurityCustomizer
     */
    private String[] getAnonymousUrls() {
        // ??????????????? RequestMapping
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        Set<String> allAnonymousAccess = new HashSet<>();
        // ?????? RequestMapping
        for (Map.Entry<RequestMappingInfo, HandlerMethod> infoEntry : handlerMethods.entrySet()) {
            HandlerMethod value = infoEntry.getValue();
            // ??????????????? AnonymousAccess ???????????????
            AccessOperation methodAnnotation = value.getMethodAnnotation(AccessOperation.class);
            // ???????????????????????? AccessOperation ?????????????????????????????????????????????
            if (methodAnnotation != null) {
                allAnonymousAccess.addAll(infoEntry.getKey().getPatternsCondition().getPatterns());
            }
        }
        return allAnonymousAccess.toArray(new String[0]);
    }

    /**
     * ?????????SpringSecurity???????????????
     * @param configuration ????????????
     * @return AuthenticationManager
     * @throws Exception Exception
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

}
