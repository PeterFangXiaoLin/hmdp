package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Result createVoucherOrder(Long voucherId);

    /**
     * 处理订单信息（从阻塞中获取并处理）
     *
     * @param voucherOrder 订单信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
