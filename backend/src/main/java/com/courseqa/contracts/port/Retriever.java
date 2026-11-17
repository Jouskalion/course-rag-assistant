// 实现负责人 C；调用方 D C 调试控制器。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface Retriever {
    /** 内部在课程锁内再次校验范围并取得快照；两路共用快照；返回证据副本后释放 */
    SearchResult search(SearchRequest request, Deadline deadline);
}
