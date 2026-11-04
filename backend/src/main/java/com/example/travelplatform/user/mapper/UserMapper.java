package com.example.travelplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.travelplatform.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * users 表的唯一数据访问入口。
 *
 * <p>只提供 MyBatis-Plus 的通用 CRUD；业务规则（规范化、唯一冲突兜底、状态校验）
 * 全部留在 Service，不在 Mapper 里用字符串拼 SQL。逻辑删除由 {@code is_deleted} 与
 * {@link com.baomidou.mybatisplus.annotation.TableLogic} 统一处理。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
