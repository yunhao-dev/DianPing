package com.wild.mapper;

import com.wild.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("select count(*) from tb_follow where user_id=#{userId} and follow_user_id=#{followUserId}")
    Integer queryFollowCounts(Long userId, Long followUserId);

    @Select("select * from tb_follow where follow_user_id = #{id}")
    List<Follow> queryAllFans(Long id);
}
