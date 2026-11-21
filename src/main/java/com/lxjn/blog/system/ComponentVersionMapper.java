package com.lxjn.blog.system;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ComponentVersionMapper {

    /** 读取现有数据库登记的表结构版本，模块 0 代表数据库。 */
    @Select("SELECT current_version FROM component_version WHERE module = 0")
    Integer findDatabaseVersion();
}
