package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @param session session
     * @return 发送结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录
     * @param loginForm 登录表单信息
     * @param session session
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 签到
     *
     * @return
     */
    Result sign();

    /**
     * 连续签到统计
     *
     * @return 连续签到次数
     */
    Result signCount();

}
