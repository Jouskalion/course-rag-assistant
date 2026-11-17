// 实现负责人 D；调用方 D。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface ChatProvider {
    /** 最多最近两轮 3000 码点；第一轮不调用 */
    RewriteResult rewrite(String question, List<HistoryTurn> history, Deadline deadline);
    /** JSON Object 输出；temperature=0.1；max_tokens=2048；enable_thinking=false */
    ChatCall generate(String question, List<SearchHit> evidence, Deadline deadline);
    /** 整轮最多一次结构修复；不新增来源不延长期限 */
    ChatCall repair(String question, List<SearchHit> evidence, String invalidOutput, String validationError, Deadline deadline);
}
