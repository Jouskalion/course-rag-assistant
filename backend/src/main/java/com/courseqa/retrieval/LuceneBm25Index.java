package com.courseqa.retrieval;

import com.courseqa.common.ServiceException;
import com.courseqa.contracts.internal.CatalogChunk;
import com.courseqa.contracts.internal.Deadline;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/** 每份课程快照持有一个只读内存索引，由最后一个租约释放资源。 */
final class LuceneBm25Index implements AutoCloseable {
    private static final String BODY = "body";
    private static final String TECHNICAL = "technical_terms";
    private static final String DOCUMENT_ID = "document_id";
    private static final String CHUNK_ID = "chunk_id";
    private static final Sort ORDER = new Sort(SortField.FIELD_SCORE,
            new SortField("chunk_id_sort", SortField.Type.STRING));
    private final Directory directory;
    private final DirectoryReader reader;
    private final Analyzer analyzer;
    private final Analyzer bodyAnalyzer;
    private final Analyzer technicalAnalyzer;

    LuceneBm25Index(List<CatalogChunk> chunks, Deadline deadline) {
        // 2000 码点的合法查询可能超过 Lucene 默认 1024 词项限制。
        IndexSearcher.setMaxClauseCount(Math.max(IndexSearcher.getMaxClauseCount(), 8192));
        directory = new ByteBuffersDirectory();
        bodyAnalyzer = new SmartChineseAnalyzer();
        technicalAnalyzer = new WhitespaceAnalyzer();
        analyzer = new PerFieldAnalyzerWrapper(bodyAnalyzer, Map.of(TECHNICAL, technicalAnalyzer));
        DirectoryReader opened = null;
        try {
            var configuration = new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity(1.2f, 0.75f));
            try (var writer = new IndexWriter(directory, configuration)) {
                for (CatalogChunk chunk : chunks) {
                    RetrievalRules.checkDeadline(deadline);
                    Document document = new Document();
                    document.add(new StringField(CHUNK_ID, chunk.chunkId().toString(), Field.Store.YES));
                    document.add(new SortedDocValuesField("chunk_id_sort", new BytesRef(chunk.chunkId().toString())));
                    document.add(new StringField(DOCUMENT_ID, chunk.documentId().toString(), Field.Store.NO));
                    document.add(new TextField(BODY, chunk.searchText(), Field.Store.NO));
                    document.add(new TextField(TECHNICAL, TechnicalTerms.indexText(chunk.searchText()), Field.Store.NO));
                    writer.addDocument(document);
                }
                writer.commit();
            }
            RetrievalRules.checkDeadline(deadline);
            opened = DirectoryReader.open(directory);
            reader = opened;
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(opened, exception);
            if (exception instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw indexFailure(exception);
        }
    }

    List<RankedChunk> search(String query, Set<UUID> documentIds, Deadline deadline) {
        RetrievalRules.checkDeadline(deadline);
        try {
            BooleanQuery.Builder terms = new BooleanQuery.Builder();
            int count = addTerms(terms, BODY, query);
            count += addTerms(terms, TECHNICAL, TechnicalTerms.indexText(query));
            if (count == 0 || documentIds.isEmpty()) {
                return List.of();
            }
            terms.setMinimumNumberShouldMatch(1);
            var scope = new TermInSetQuery(DOCUMENT_ID,
                    documentIds.stream().map(id -> new BytesRef(id.toString())).toList());
            var scoped = new BooleanQuery.Builder()
                    .add(terms.build(), BooleanClause.Occur.MUST)
                    .add(scope, BooleanClause.Occur.FILTER).build();
            // searcher 为每次调用独立创建，期限不会被另一请求覆盖。
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
            searcher.setTimeout(() -> Thread.currentThread().isInterrupted()
                    || deadline.deadlineNanos() - System.nanoTime() <= 0);
            var found = searcher.search(scoped, RetrievalRules.CANDIDATES, ORDER, true);
            RetrievalRules.checkDeadline(deadline);
            if (searcher.timedOut()) {
                throw RetrievalRules.timeout();
            }
            var stored = searcher.storedFields();
            List<RankedChunk> result = new ArrayList<>();
            for (var hit : found.scoreDocs) {
                RetrievalRules.checkDeadline(deadline);
                UUID id = UUID.fromString(stored.document(hit.doc).get(CHUNK_ID));
                result.add(new RankedChunk(id, hit.score, result.size() + 1));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw indexFailure(exception);
        }
    }

    private int addTerms(BooleanQuery.Builder query, String field, String text) throws IOException {
        Set<String> unique = new LinkedHashSet<>();
        try (var tokens = analyzer.tokenStream(field, text)) {
            var term = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                unique.add(term.toString());
            }
            tokens.end();
        }
        for (String term : unique) {
            query.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD);
        }
        return unique.size();
    }

    private void closeAfterFailure(DirectoryReader opened, Exception original) {
        try {
            IOUtils.close(opened, analyzer, bodyAnalyzer, technicalAnalyzer, directory);
        } catch (IOException | RuntimeException failure) {
            original.addSuppressed(failure);
        }
    }

    private static ServiceException indexFailure(Exception cause) {
        var failure = new ServiceException("INTERNAL_ERROR", 500, "课程关键词索引读取或构建失败", false);
        failure.initCause(cause);
        return failure;
    }

    @Override
    public void close() throws IOException {
        // PerFieldAnalyzerWrapper.close 不负责关闭被包装的 Analyzer。
        IOUtils.close(reader, analyzer, bodyAnalyzer, technicalAnalyzer, directory);
    }
}
