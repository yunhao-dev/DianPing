package com.wild.service;

import com.wild.dto.Result;
import com.wild.entity.Blog;
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

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);


}
