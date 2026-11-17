-- B 维护结构；D 提供会话字段。无种子数据。迁移由应用事务执行。

-- 课程资料集合
CREATE TABLE knowledge_bases (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 80),
    description TEXT NOT NULL DEFAULT '' CHECK(length(description)<=500),
    embedding_model TEXT NOT NULL DEFAULT 'text-embedding-v4',
    embedding_dimensions INTEGER NOT NULL DEFAULT 1024 CHECK(embedding_dimensions=1024),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- 原文件与导入状态 保留删除墓碑
CREATE TABLE documents (
    id TEXT PRIMARY KEY NOT NULL,
    knowledge_base_id TEXT NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT,
    upload_client_request_id TEXT NOT NULL,
    file_name TEXT NOT NULL CHECK(length(file_name) BETWEEN 1 AND 255),
    storage_relpath TEXT NOT NULL UNIQUE,
    file_size_bytes INTEGER NOT NULL CHECK(file_size_bytes BETWEEN 1 AND 20971520),
    page_count INTEGER CHECK(page_count>=0),
    chunk_count INTEGER NOT NULL DEFAULT 0 CHECK(chunk_count>=0),
    reserved_chunk_count INTEGER NOT NULL DEFAULT 0 CHECK(reserved_chunk_count>=0),
    status TEXT NOT NULL CHECK(status IN ('queued','parsing','indexing','ready','failed','interrupted','deleting','deleted')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
    warnings_json TEXT NOT NULL DEFAULT '[]' CHECK(json_valid(warnings_json) AND json_type(warnings_json)='array'),
    error_json TEXT CHECK(error_json IS NULL OR json_valid(error_json)),
    active_job_id TEXT REFERENCES import_jobs(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT ,
    UNIQUE(knowledge_base_id,upload_client_request_id),
    UNIQUE(id,knowledge_base_id),
    CHECK(status NOT IN ('deleting','deleted') OR enabled=0)
);

-- 页内文字和片段位置基准
CREATE TABLE document_pages (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page_no INTEGER NOT NULL CHECK(page_no>=1),
    text TEXT NOT NULL,
    char_count INTEGER NOT NULL CHECK(char_count>=0),
    extraction_status TEXT NOT NULL CHECK(extraction_status IN ('text','low_text','empty')) ,
    UNIQUE(document_id,page_no)
);

-- 页内片段与检索文本
CREATE TABLE chunks (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL,
    ordinal INTEGER NOT NULL CHECK(ordinal>=0),
    page_no INTEGER NOT NULL CHECK(page_no>=1),
    char_start INTEGER NOT NULL CHECK(char_start>=0),
    char_end INTEGER NOT NULL CHECK(char_end>char_start),
    text TEXT NOT NULL CHECK(length(text) BETWEEN 1 AND 1000),
    search_text TEXT NOT NULL,
    created_at TEXT NOT NULL ,
    UNIQUE(document_id,ordinal),
    UNIQUE(id,document_id),
    FOREIGN KEY(document_id,page_no) REFERENCES document_pages(document_id,page_no) ON DELETE CASCADE
);

-- 片段向量
CREATE TABLE chunk_embeddings (
    chunk_id TEXT PRIMARY KEY NOT NULL REFERENCES chunks(id) ON DELETE CASCADE,
    model TEXT NOT NULL,
    dimensions INTEGER NOT NULL CHECK(dimensions=1024),
    vector BLOB NOT NULL CHECK(length(vector)=4096),
    created_at TEXT NOT NULL
);

-- 持久化导入尝试
CREATE TABLE import_jobs (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK(status IN ('queued','running','completed','failed','interrupted','cancelled')),
    stage TEXT NOT NULL CHECK(stage IN ('queued','parsing','indexing','publishing','finished')),
    pages_processed INTEGER NOT NULL DEFAULT 0 CHECK(pages_processed>=0),
    pages_total INTEGER CHECK(pages_total>=0),
    chunks_embedded INTEGER NOT NULL DEFAULT 0 CHECK(chunks_embedded>=0),
    chunks_total INTEGER CHECK(chunks_total>=0),
    error_json TEXT CHECK(error_json IS NULL OR json_valid(error_json)),
    created_at TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT
);

-- 固定资料范围的会话
CREATE TABLE conversations (
    id TEXT PRIMARY KEY NOT NULL,
    knowledge_base_id TEXT NOT NULL REFERENCES knowledge_bases(id) ON DELETE RESTRICT,
    title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 80),
    scope TEXT NOT NULL CHECK(scope IN ('all','selected')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL ,
    UNIQUE(id,knowledge_base_id)
);

-- 会话资料范围 以关联表约束课程归属
CREATE TABLE conversation_documents (
    conversation_id TEXT NOT NULL,
    document_id TEXT NOT NULL,
    knowledge_base_id TEXT NOT NULL ,
    PRIMARY KEY(conversation_id,document_id),
    FOREIGN KEY(conversation_id,knowledge_base_id) REFERENCES conversations(id,knowledge_base_id) ON DELETE CASCADE,
    FOREIGN KEY(document_id,knowledge_base_id) REFERENCES documents(id,knowledge_base_id) ON DELETE RESTRICT
);

-- 一次异步问答及其证据快照
CREATE TABLE turns (
    id TEXT PRIMARY KEY NOT NULL,
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    client_request_id TEXT NOT NULL,
    retry_of_turn_id TEXT REFERENCES turns(id) ON DELETE SET NULL,
    question TEXT NOT NULL CHECK(length(trim(question)) BETWEEN 1 AND 2000),
    standalone_question TEXT CHECK(standalone_question IS NULL OR length(standalone_question) BETWEEN 1 AND 2000),
    strategy TEXT NOT NULL CHECK(strategy IN ('bm25','dense','hybrid')),
    top_k INTEGER NOT NULL CHECK(top_k BETWEEN 1 AND 10),
    scope_document_ids_json TEXT NOT NULL CHECK(json_valid(scope_document_ids_json) AND json_type(scope_document_ids_json)='array' AND json_array_length(scope_document_ids_json)>0),
    rewrite_history_json TEXT NOT NULL CHECK(json_valid(rewrite_history_json) AND json_type(rewrite_history_json)='array'),
    status TEXT NOT NULL CHECK(status IN ('queued','rewriting','retrieving','generating','validating','answered','insufficient_evidence','needs_clarification','failed','interrupted')),
    answer_blocks_json TEXT NOT NULL CHECK(json_valid(answer_blocks_json) AND json_type(answer_blocks_json)='array'),
    message TEXT,
    error_json TEXT CHECK(error_json IS NULL OR json_valid(error_json)),
    retrieval_snapshot_json TEXT CHECK(retrieval_snapshot_json IS NULL OR json_valid(retrieval_snapshot_json)),
    timings_json TEXT NOT NULL CHECK(json_valid(timings_json)),
    token_usage_json TEXT NOT NULL CHECK(json_valid(token_usage_json)),
    model_settings_json TEXT NOT NULL CHECK(json_valid(model_settings_json)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    finished_at TEXT ,
    UNIQUE(conversation_id,client_request_id),
    CHECK(status<>'answered' OR json_array_length(answer_blocks_json)>0),
    CHECK(status NOT IN ('insufficient_evidence','needs_clarification','failed','interrupted') OR json_array_length(answer_blocks_json)=0)
);

-- 引用内容快照 保留可追溯历史
CREATE TABLE citations (
    id TEXT PRIMARY KEY NOT NULL,
    turn_id TEXT NOT NULL REFERENCES turns(id) ON DELETE CASCADE,
    citation_id INTEGER NOT NULL CHECK(citation_id>=1),
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
    chunk_id TEXT REFERENCES chunks(id) ON DELETE SET NULL,
    file_name TEXT NOT NULL,
    page_no INTEGER NOT NULL CHECK(page_no>=1),
    excerpt TEXT NOT NULL CHECK(length(excerpt) BETWEEN 1 AND 1000) ,
    UNIQUE(turn_id,citation_id)
);

-- 一个回答一份可修改反馈
CREATE TABLE feedback (
    id TEXT PRIMARY KEY NOT NULL,
    turn_id TEXT NOT NULL UNIQUE REFERENCES turns(id) ON DELETE CASCADE,
    rating TEXT NOT NULL CHECK(rating IN ('up','down')),
    reason TEXT CHECK(reason IS NULL OR reason IN ('helpful','incorrect','unsupported','missing_information','other')),
    comment TEXT NOT NULL DEFAULT '' CHECK(length(comment)<=1000),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX ix_documents_scope ON documents(knowledge_base_id,status,enabled);

CREATE INDEX ix_documents_created ON documents(knowledge_base_id,created_at,id);

CREATE INDEX ix_chunks_page ON chunks(document_id,page_no,ordinal);

CREATE INDEX ix_import_queue ON import_jobs(status,created_at,id);

CREATE UNIQUE INDEX ux_import_active ON import_jobs(document_id) WHERE status IN ('queued','running');

CREATE INDEX ix_conversations_recent ON conversations(knowledge_base_id,updated_at,id);

CREATE INDEX ix_turns_history ON turns(conversation_id,created_at,id);

CREATE UNIQUE INDEX ux_turn_active ON turns(conversation_id) WHERE status IN ('queued','rewriting','retrieving','generating','validating');

CREATE INDEX ix_citations_document ON citations(document_id);
