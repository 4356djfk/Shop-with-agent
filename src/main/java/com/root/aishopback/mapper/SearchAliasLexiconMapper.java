package com.root.aishopback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.root.aishopback.entity.SearchAliasLexicon;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchAliasLexiconMapper extends BaseMapper<SearchAliasLexicon> {

    @Select("SELECT id, alias, cluster_key, aliases, source, enabled " +
        "FROM search_alias_lexicon WHERE enabled = TRUE")
    List<SearchAliasLexicon> listEnabled();

    @Delete("DELETE FROM search_alias_lexicon WHERE source = #{source}")
    int deleteBySource(@Param("source") String source);

    @Insert("INSERT INTO search_alias_lexicon(alias, cluster_key, aliases, source, enabled) " +
        "VALUES(#{alias}, #{clusterKey}, #{aliases}, #{source}, TRUE) " +
        "ON CONFLICT(alias) DO UPDATE SET " +
        "cluster_key = EXCLUDED.cluster_key, " +
        "aliases = EXCLUDED.aliases, " +
        "source = EXCLUDED.source, " +
        "enabled = TRUE, " +
        "updated_at = NOW()")
    int upsert(
        @Param("alias") String alias,
        @Param("clusterKey") String clusterKey,
        @Param("aliases") String aliases,
        @Param("source") String source
    );
}
