package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 判断是否点赞
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 加到redis
//                stringRedisTemplate.opsForSet().add(key, userId.toString());
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 从 redis 中移除
//                stringRedisTemplate.opsForSet().remove(key, userId.toString());
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询点赞 top5
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (CollectionUtils.isEmpty(top5)) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
//        String idStr = StrUtil.join(",", ids);
//        // WHERE id IN (5,1) ORDER BY FIELD(id,5,1) 保证按点赞时间排序
//        List<UserDTO> userDTOS = userService.lambdaQuery()
//                .in(User::getId, ids)
//                .last("ORDER BY FIELD(id," + idStr + ")")
//                .list()
//                .stream()
//                .map(user -> {
//                    UserDTO dto = new UserDTO();
//                    dto.setId(user.getId());
//                    dto.setNickName(user.getNickName());
//                    dto.setIcon(user.getIcon());
//                    return dto;
//                })
//                .collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        List<UserDTO> userDTOS = ids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败！");
        }
        // 推送到粉丝的收件箱
        // 查询粉丝列表
        List<Follow> followList = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, user.getId())
                .list();
        for (Follow follow : followList) {
            // 粉丝id
            Long userId = follow.getUserId();
            String key = RedisConstants.FEED_KEY + userId;
            // 推送
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前登录用户的推送箱
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (CollectionUtils.isEmpty(typedTuples)) {
            return Result.ok();
        }

        // 解析出里面blog
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int minTimeCount = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String idStr = typedTuple.getValue();
            if (StrUtil.isBlank(idStr)) {
                continue;
            }
            ids.add(Long.valueOf(idStr));
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                minTimeCount++;
            } else {
                minTime = time;
                minTimeCount = 1;
            }
        }

        // 根据blog ids 查找blog, 注意in 查找返回是无序的
        List<Blog> blogList = listByIds(ids);
        Map<Long, Blog> blogMap = blogList.stream().collect(Collectors.toMap(Blog::getId, blog -> blog));
        List<Blog> blogs = ids.stream().map(blogMap::get).collect(Collectors.toList());
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        // 封装返回结果
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(minTimeCount);

        return Result.ok(result);
    }
}
