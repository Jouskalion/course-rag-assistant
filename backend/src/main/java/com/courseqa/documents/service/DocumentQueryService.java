// 负责人 B。
package com.courseqa.documents.service;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.courseqa.documents.entity.DocumentEntity;
import com.courseqa.documents.repository.DocumentRepository;
import org.springframework.stereotype.Service;
@Service
public class DocumentQueryService extends ServiceImpl<DocumentRepository, DocumentEntity> {
}
