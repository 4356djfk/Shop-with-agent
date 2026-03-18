package com.root.aishopback.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("search_alias_lexicon")
public class SearchAliasLexicon {
    @TableId
    private Long id;
    private String alias;
    private String clusterKey;
    private String aliases;
    private String source;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getClusterKey() { return clusterKey; }
    public void setClusterKey(String clusterKey) { this.clusterKey = clusterKey; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
