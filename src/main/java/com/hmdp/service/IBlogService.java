package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门笔记
     *
     * @param current current
     * @return 结果
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id 查询笔记
     *
     * @param id id
     * @return 笔记
     */
    Result queryBlogById(Long id);

    /**
     * 点赞笔记
     * @param id id
     * @return 点赞结果
     */
    Result likeBlog(Long id);

    /**
     * 查询笔记前5名点赞用户
     * @param id id
     * @return 点赞列表
     */
    Result queryBlogLikes(Long id);
}
