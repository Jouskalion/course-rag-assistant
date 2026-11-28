// 负责人 B。
package com.courseqa.documents.service;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
public class PageChunker {
    public List<ChunkDraft> split(UUID documentId, List<ParsedPage> pages) { throw new FeatureNotImplementedException("B","PageChunker"); }
}
