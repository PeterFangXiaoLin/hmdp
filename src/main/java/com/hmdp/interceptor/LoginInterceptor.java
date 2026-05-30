package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * preHandle — Controller 方法执行之前
 * postHandle — Controller 方法执行之后，视图渲染之前
 * afterCompletion — 视图渲染之后（整个请求处理完毕）
 *
 * 请求处理完毕之后，必须删除 ThreadLocal 中的用户信息，防止内存泄露
 * 因为 Tomcat 内置线程池，线程会服用
 */
public class LoginInterceptor implements HandlerInterceptor {

//    private final StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
//        HttpSession session = request.getSession();
        // 2. 从session 中获取用户信息
//        Object user = session.getAttribute("user");
//        // 3. user 为null 直接拦截调
//        if (user == null) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return false;
//        }
        // 从 请求头 当中拿到 token, 再 利用 token 从 redis 中拿到用户信息
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return false;
//        }
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        if (userMap.isEmpty()) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return false;
//        }
//        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
//
//        // 4. 存在 保存用户信息
//        UserHolder.saveUser(user);
        // 5. 放行

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 放行
        return true;
    }
}
