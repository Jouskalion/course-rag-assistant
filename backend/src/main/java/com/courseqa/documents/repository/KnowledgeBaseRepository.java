
package com.courseqa.documents.repository;
import com.courseqa.documents.entity.KnowledgeBaseEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface KnowledgeBaseRepository extends BaseMapper<KnowledgeBaseEntity> {
    @Select("SELECT COUNT(*) FROM conversations WHERE knowledge_base_id = #{id}")
    long countConversations(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM import_jobs ij JOIN documents d ON ij.document_id = d.id " +
            "WHERE d.knowledge_base_id = #{id} AND ij.status IN ('queued','running')")
    long countActiveJobs(@Param("id") String id);
}
