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
public class ImportJobController {
    // 导入进度
    @GetMapping(value="/import-jobs/{job_id}")
    public ResponseEntity<ImportJob> getImportJob(@PathVariable(value="job_id") UUID job_id) {
        throw new FeatureNotImplementedException("B","getImportJob");
    }
}
