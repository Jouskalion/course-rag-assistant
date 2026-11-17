// 实现负责人 B；调用方 C。
package com.courseqa.contracts.port;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
public interface CatalogReadPort {
    /** 在课程锁内读取 ready 且 enabled 的完整片段和向量 */
    CatalogData load(UUID knowledgeBaseId);
}
