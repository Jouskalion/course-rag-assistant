// 负责人 B。业务未实现时明确返回 501，不返回虚构业务数据。
package com.courseqa.documents.controller;
import com.courseqa.contracts.dto.*;
import com.courseqa.common.FeatureNotImplementedException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1")
public class DocumentController {
    // 未删除资料列表
    @GetMapping(value="/knowledge-bases/{knowledge_base_id}/documents")
    public ResponseEntity<DocumentList> listDocuments(@PathVariable(value="knowledge_base_id") UUID knowledge_base_id, @RequestParam(value="page", defaultValue="1") Integer page, @RequestParam(value="page_size", defaultValue="20") Integer page_size) {
        throw new FeatureNotImplementedException("B","listDocuments");
    }
    // 上传单个 PDF
    @PostMapping(value="/knowledge-bases/{knowledge_base_id}/documents", consumes="multipart/form-data")
    public ResponseEntity<ImportAccepted> uploadDocument(@PathVariable(value="knowledge_base_id") UUID knowledge_base_id, @RequestPart("file") MultipartFile file, @RequestParam("client_request_id") UUID client_request_id) {
        throw new FeatureNotImplementedException("B","uploadDocument");
    }
    // 读取资料 含删除墓碑
    @GetMapping(value="/documents/{document_id}")
    public ResponseEntity<Document> getDocument(@PathVariable(value="document_id") UUID document_id) {
        throw new FeatureNotImplementedException("B","getDocument");
    }
    // 启用或停用 ready 资料
    @PatchMapping(value="/documents/{document_id}")
    public ResponseEntity<Document> updateDocument(@PathVariable(value="document_id") UUID document_id, @Valid @RequestBody DocumentUpdate body) {
        throw new FeatureNotImplementedException("B","updateDocument");
    }
    // 退出检索并异步清理
    @DeleteMapping(value="/documents/{document_id}")
    public ResponseEntity<Document> deleteDocument(@PathVariable(value="document_id") UUID document_id) {
        throw new FeatureNotImplementedException("B","deleteDocument");
    }
    // 预览已保存片段
    @GetMapping(value="/documents/{document_id}/chunks")
    public ResponseEntity<ChunkList> listChunks(@PathVariable(value="document_id") UUID document_id, @RequestParam(value="page", defaultValue="1") Integer page, @RequestParam(value="page_size", defaultValue="20") Integer page_size) {
        throw new FeatureNotImplementedException("B","listChunks");
    }
    // 读取一页提取文字
    @GetMapping(value="/documents/{document_id}/pages/{page_no}")
    public ResponseEntity<DocumentPage> getDocumentPage(@PathVariable(value="document_id") UUID document_id, @PathVariable(value="page_no") Integer page_no) {
        throw new FeatureNotImplementedException("B","getDocumentPage");
    }
    // 完整读取原 PDF 不实现 Range
    @GetMapping(value="/documents/{document_id}/file")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable(value="document_id") UUID document_id) {
        throw new FeatureNotImplementedException("B","getDocumentFile");
    }
    // 重试 failed interrupted 导入；deleting 则继续清理
    @PostMapping(value="/documents/{document_id}/retry")
    public ResponseEntity<Document> retryDocument(@PathVariable(value="document_id") UUID document_id) {
        throw new FeatureNotImplementedException("B","retryDocument");
    }
}
