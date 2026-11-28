package com.courseqa.documents.repository;
import com.courseqa.documents.entity.DocumentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface DocumentRepository extends BaseMapper<DocumentEntity> {
}
