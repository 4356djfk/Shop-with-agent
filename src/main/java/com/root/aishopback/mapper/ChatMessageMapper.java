package com.root.aishopback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.root.aishopback.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

