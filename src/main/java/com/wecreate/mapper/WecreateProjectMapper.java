package com.wecreate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wecreate.entity.WecreateProject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目管理 Mapper 接口
 */
@Mapper
public interface WecreateProjectMapper extends BaseMapper<WecreateProject> {
}
