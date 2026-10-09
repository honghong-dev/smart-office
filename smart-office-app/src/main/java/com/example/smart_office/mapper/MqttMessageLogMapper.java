package com.example.smart_office.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.smart_office.entity.MqttMessageLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqttMessageLogMapper extends BaseMapper<MqttMessageLog> {
}