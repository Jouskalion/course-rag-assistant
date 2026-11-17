// 负责人 B。
package com.courseqa.documents;
import com.courseqa.contracts.dto.*;
import com.courseqa.contracts.internal.*;
import com.courseqa.common.FeatureNotImplementedException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
public class PdfPageExtractor {
    public List<ParsedPage> extract(UUID documentId, Path file) { throw new FeatureNotImplementedException("B","PdfPageExtractor"); }
}
