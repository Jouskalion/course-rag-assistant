// 负责人 B。
package com.courseqa.documents.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;
@Data
@TableName(value = "knowledge_bases", autoResultMap = true)
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
    @TableField(value = "created_at", typeHandler = InstantUtcHandler.class)
    private Instant createdAt;
    @TableField(value = "updated_at", typeHandler = InstantUtcHandler.class)
    private Instant updatedAt;
}
