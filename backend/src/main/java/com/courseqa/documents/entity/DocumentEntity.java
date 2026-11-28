// 负责人 B。
package com.courseqa.documents.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;
@Data
@TableName(value = "documents", autoResultMap = true)
public class DocumentEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("knowledge_base_id")
    private String knowledgeBaseId;
    @TableField("upload_client_request_id")
    private String uploadClientRequestId;
    @TableField("file_name")
    private String fileName;
    @TableField("storage_relpath")
    private String storageRelpath;
    @TableField("file_size_bytes")
    private Long fileSizeBytes;
    @TableField("page_count")
    private Integer pageCount;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("reserved_chunk_count")
    private Integer reservedChunkCount;
    @TableField("status")
    private String status;
    @TableField("enabled")
    private Integer enabled;
    @TableField("warnings_json")
    private String warningsJson;
    @TableField("error_json")
    private String errorJson;
    @TableField("active_job_id")
    private String activeJobId;
    @TableField(value = "created_at", typeHandler = InstantUtcHandler.class)
    private Instant createdAt;
    @TableField(value = "updated_at", typeHandler = InstantUtcHandler.class)
    private Instant updatedAt;
    @TableField(value = "deleted_at", typeHandler = InstantUtcHandler.class)
    private Instant deletedAt;
}
