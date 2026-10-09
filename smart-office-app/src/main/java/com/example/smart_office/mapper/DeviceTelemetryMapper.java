package com.example.smart_office.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.smart_office.entity.DeviceTelemetry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceTelemetryMapper extends BaseMapper<DeviceTelemetry> {
    int insertBatch(@Param("list") List<DeviceTelemetry> list);
}