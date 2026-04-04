package com.wecreate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wecreate.entity.WecreateLog;
import com.wecreate.mapper.WecreateLogMapper;
import com.wecreate.service.WecreateLogService;
import org.springframework.stereotype.Service;

@Service
public class WecreateLogServiceImpl extends ServiceImpl<WecreateLogMapper, WecreateLog> implements WecreateLogService {
    
    @Override
    public Page<WecreateLog> searchLogs(String keyword, Integer pageNum, Integer pageSize) {
        Page<WecreateLog> page = new Page<>(pageNum, pageSize);
        QueryWrapper<WecreateLog> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("CREATE_USER", keyword)
                    .or()
                    .like("CONTENT", keyword)
                    .or()
                    .like("TRACEID", keyword));
        }

        return this.page(page, queryWrapper);
    }
}