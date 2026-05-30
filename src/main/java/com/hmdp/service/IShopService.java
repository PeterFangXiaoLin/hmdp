package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id id
     * @return 商铺信息
     */
    Result queryById(Long id);

    /**
     * 修改商铺信息
     *
     * @param shop 商铺信息
     * @return 修改结果
     */
    Result update(Shop shop);
}
