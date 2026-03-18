package com.root.aishopback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.root.aishopback.entity.QueryNormalizationLexicon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QueryNormalizationLexiconMapper extends BaseMapper<QueryNormalizationLexicon> {

    @Select("SELECT id, phrase, replacement, scene, priority, source, enabled " +
        "FROM query_normalization_lexicon " +
        "WHERE enabled = TRUE AND (scene = 'PROMPT' OR scene IS NULL OR scene = '') " +
        "ORDER BY COALESCE(priority, 0) DESC, LENGTH(phrase) DESC, id ASC")
    List<QueryNormalizationLexicon> listEnabledPromptRules();
}
