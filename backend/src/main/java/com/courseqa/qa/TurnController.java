// 负责人 D。业务未实现时明确返回 501，不返回虚构业务数据。
package com.courseqa.qa;
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
public class TurnController {
    // 会话历史 按创建时间升序
    @GetMapping(value="/conversations/{conversation_id}/turns")
    public ResponseEntity<TurnList> listTurns(@PathVariable(value="conversation_id") UUID conversation_id, @RequestParam(value="page", defaultValue="1") Integer page, @RequestParam(value="page_size", defaultValue="20") Integer page_size) {
        throw new FeatureNotImplementedException("D","listTurns");
    }
    // 异步提问
    @PostMapping(value="/conversations/{conversation_id}/turns")
    public ResponseEntity<Turn> createTurn(@PathVariable(value="conversation_id") UUID conversation_id, @Valid @RequestBody TurnCreate body) {
        throw new FeatureNotImplementedException("D","createTurn");
    }
    // 问答状态与结果
    @GetMapping(value="/turns/{turn_id}")
    public ResponseEntity<Turn> getTurn(@PathVariable(value="turn_id") UUID turn_id) {
        throw new FeatureNotImplementedException("D","getTurn");
    }
    // 主动重试 创建新记录并保留原记录
    @PostMapping(value="/turns/{turn_id}/retry")
    public ResponseEntity<Turn> retryTurn(@PathVariable(value="turn_id") UUID turn_id, @Valid @RequestBody TurnRetry body) {
        throw new FeatureNotImplementedException("D","retryTurn");
    }
    // 修改 answered 回答的共享反馈
    @PutMapping(value="/turns/{turn_id}/feedback")
    public ResponseEntity<Feedback> putFeedback(@PathVariable(value="turn_id") UUID turn_id, @Valid @RequestBody FeedbackWrite body) {
        throw new FeatureNotImplementedException("D","putFeedback");
    }
}
