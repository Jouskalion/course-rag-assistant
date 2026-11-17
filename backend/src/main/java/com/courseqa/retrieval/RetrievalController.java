// 负责人 C。业务未实现时明确返回 501，不返回虚构业务数据。
package com.courseqa.retrieval;
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
public class RetrievalController {
    // 检索调试 复用正式检索实现
    @PostMapping(value="/retrieval/search")
    public ResponseEntity<SearchResult> search(@Valid @RequestBody SearchRequest body) {
        throw new FeatureNotImplementedException("C","search");
    }
}
