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

    /**
     * 保存笔记，并推送给关注了该用户的粉丝
     *
     * @param blog 笔记
     * @return 保存结果
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注的人的笔记列表
     *
     * @param max    上一次的时间戳
     * @param offset 偏移量
     * @return 笔记列表
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
