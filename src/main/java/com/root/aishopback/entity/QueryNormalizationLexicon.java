package com.root.aishopback.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("query_normalization_lexicon")
public class QueryNormalizationLexicon {
    @TableId
    private Long id;
    private String phrase;
    private String replacement;
    private String scene;
    private Integer priority;
    private String source;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhrase() { return phrase; }
    public void setPhrase(String phrase) { this.phrase = phrase; }
    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
