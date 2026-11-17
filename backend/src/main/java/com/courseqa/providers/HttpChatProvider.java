// 负责人 D。实现此类并通过 Spring Bean 装配接入。
package com.courseqa.providers;
import com.courseqa.contracts.port.*;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public class HttpChatProvider implements ChatProvider {
    @Override public RewriteResult rewrite(String question, List<HistoryTurn> history, Deadline deadline) { throw new FeatureNotImplementedException("D","rewrite"); }
    @Override public ChatCall generate(String question, List<SearchHit> evidence, Deadline deadline) { throw new FeatureNotImplementedException("D","generate"); }
    @Override public ChatCall repair(String question, List<SearchHit> evidence, String invalidOutput, String validationError, Deadline deadline) { throw new FeatureNotImplementedException("D","repair"); }
}
