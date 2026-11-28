// 负责人 B。仅映射 V001 中的 knowledge_bases 表，不对外暴露。
package com.courseqa.documents.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;
@Data
@TableName("knowledge_bases")
public class KnowledgeBaseEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("name")
    private String name;
    @TableField("description")
    private String description;
    @TableField("embedding_model")
    private String embeddingModel;
    @TableField("embedding_dimensions")
    private Integer embeddingDimensions;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;
}
