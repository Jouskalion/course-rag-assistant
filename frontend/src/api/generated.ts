// 从 contracts/openapi.json 生成。运行 node scripts/generate-contracts.mjs 更新。

export interface FieldError {
  /** 字段路径 */
  field: string;
  /** 原因 */
  message: string;
}

export interface ApiError {
  /** 固定错误码 */
  code: "INVALID_REQUEST" | "NOT_FOUND" | "METHOD_NOT_ALLOWED" | "UNSUPPORTED_MEDIA_TYPE" | "KNOWLEDGE_BASE_NOT_EMPTY" | "EMPTY_SCOPE" | "SCOPE_MISMATCH" | "SOURCE_UNAVAILABLE" | "FILE_TOO_LARGE" | "INVALID_PDF" | "DOCUMENT_LIMIT_EXCEEDED" | "DOCUMENT_NOT_READY" | "IDEMPOTENCY_CONFLICT" | "CONVERSATION_BUSY" | "IMPORT_NOT_RETRYABLE" | "TURN_NOT_RETRYABLE" | "MODEL_NOT_CONFIGURED" | "EMBEDDING_MODEL_MISMATCH" | "PDF_TOO_MANY_PAGES" | "PDF_ENCRYPTED" | "PDF_NO_TEXT" | "PDF_PARSE_FAILED" | "CHUNK_LIMIT_EXCEEDED" | "EMBEDDING_FAILED" | "MODEL_AUTH_FAILED" | "MODEL_RATE_LIMITED" | "MODEL_TIMEOUT" | "MODEL_UNAVAILABLE" | "MODEL_OUTPUT_INVALID" | "CITATION_INVALID" | "TASK_INTERRUPTED" | "DELETE_FAILED" | "FEATURE_NOT_IMPLEMENTED" | "INTERNAL_ERROR";
  /** 面向用户的说明 */
  message: string;
  /** 是否允许主动重试 */
  retryable: boolean;
  /** 字段错误，没有时为空数组 */
  details: Array<FieldError>;
}

export interface ErrorEnvelope {
  /**  */
  error: ApiError;
  /** 本次 HTTP 请求编号 */
  request_id: string;
}

export interface Health {
  /**  */
  status: "ok";
  /** 工程版本 */
  version: string;
  /**  */
  application_stage: "skeleton" | "application";
  /**  */
  database_ready: boolean;
  /** Base URL 与密钥均非空 */
  model_configured: boolean;
}

export interface KnowledgeBaseCreate {
  /** 去掉首尾空白后 1 至 80 码点 */
  name: string;
  /** 说明 */
  description?: string;
}

export interface KnowledgeBaseUpdate {
  /** 去掉首尾空白后 1 至 80 码点 */
  name: string;
  /** 说明 */
  description: string;
}

export interface KnowledgeBase {
  /** 业务编号 */
  id: string;
  /**  */
  name: string;
  /**  */
  description: string;
  /**  */
  embedding_model: string;
  /**  */
  embedding_dimensions: number;
  /** 未删除资料数 */
  document_count: number;
  /** ready 且 enabled 的资料数 */
  ready_document_count: number;
  /** 所有 ready 资料片段数 包括已停用 */
  ready_chunk_count: number;
  /** 导入预留片段数 */
  reserved_chunk_count: number;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  updated_at: string;
}

export interface DocumentWarning {
  /**  */
  code: "EMPTY_PAGE" | "LOW_TEXT";
  /** PDF 实际页序 */
  page_no: number;
  /**  */
  message: string;
}

export interface Document {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  knowledge_base_id: string;
  /**  */
  file_name: string;
  /**  */
  file_size_bytes: number;
  /**  */
  page_count: number | null;
  /**  */
  chunk_count: number;
  /**  */
  status: "queued" | "parsing" | "indexing" | "ready" | "failed" | "interrupted" | "deleting" | "deleted";
  /**  */
  enabled: boolean;
  /**  */
  warnings: Array<DocumentWarning>;
  /**  */
  error: ApiError | null;
  /** 业务编号 */
  active_job_id: string | null;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  updated_at: string;
  /** UTC ISO 8601 时间 */
  deleted_at: string | null;
}

export interface DocumentUpdate {
  /**  */
  enabled: boolean;
}

export interface ImportJob {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  document_id: string;
  /**  */
  status: "queued" | "running" | "completed" | "failed" | "interrupted" | "cancelled";
  /**  */
  stage: "queued" | "parsing" | "indexing" | "publishing" | "finished";
  /**  */
  pages_processed: number;
  /**  */
  pages_total: number | null;
  /**  */
  chunks_embedded: number;
  /**  */
  chunks_total: number | null;
  /**  */
  error: ApiError | null;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  started_at: string | null;
  /** UTC ISO 8601 时间 */
  finished_at: string | null;
}

export interface ImportAccepted {
  /**  */
  document: Document;
  /**  */
  job: ImportJob;
}

export interface DocumentPage {
  /** 业务编号 */
  document_id: string;
  /**  */
  page_no: number;
  /**  */
  text: string;
  /**  */
  char_count: number;
  /**  */
  extraction_status: "text" | "low_text" | "empty";
}

export interface Chunk {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  document_id: string;
  /** 业务编号 */
  knowledge_base_id: string;
  /** 资料内从 0 开始 */
  ordinal: number;
  /**  */
  page_no: number;
  /** 码点偏移 左闭 */
  char_start: number;
  /** 码点偏移 右开 */
  char_end: number;
  /** 精确对应页文本切片 */
  text: string;
}

export interface ConversationCreate {
  /** 业务编号 */
  knowledge_base_id: string;
  /**  */
  scope: "all" | "selected";
  /**  */
  document_ids?: Array<string>;
  /** 不传时固定为 新会话 */
  title?: string;
}

export interface ConversationUpdate {
  /**  */
  title: string;
}

export interface Conversation {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  knowledge_base_id: string;
  /**  */
  title: string;
  /**  */
  scope: "all" | "selected";
  /**  */
  document_ids: Array<string>;
  /**  */
  unavailable_document_ids: Array<string>;
  /** 业务编号 */
  active_turn_id: string | null;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  updated_at: string;
}

export interface TurnCreate {
  /** 客户端重传必须复用 */
  client_request_id: string;
  /** 去除首尾空白后的问题 */
  question: string;
  /** 检索策略 */
  strategy?: "bm25" | "dense" | "hybrid";
  /** 最终片段数 */
  top_k?: number;
}

export interface TurnRetry {
  /** 业务编号 */
  client_request_id: string;
}

export interface AnswerBlock {
  /**  */
  text: string;
  /**  */
  citation_ids: Array<number>;
}

export interface Citation {
  /** 本轮显示编号 从 1 开始 */
  citation_id: number;
  /** 业务编号 */
  document_id: string;
  /** 业务编号 */
  chunk_id: string | null;
  /**  */
  file_name: string;
  /**  */
  page_no: number;
  /** 完整原片段 */
  excerpt: string;
  /** 原 PDF 当前能否打开 */
  source_available: boolean;
}

export interface Timings {
  /**  */
  queue_ms: number | null;
  /**  */
  rewrite_ms: number | null;
  /**  */
  query_embedding_ms: number | null;
  /**  */
  retrieval_ms: number | null;
  /**  */
  generation_ms: number | null;
  /**  */
  validation_ms: number | null;
  /**  */
  total_ms: number | null;
}

export interface TokenUsage {
  /**  */
  rewrite_input_tokens: number | null;
  /**  */
  rewrite_output_tokens: number | null;
  /**  */
  query_embedding_input_tokens: number | null;
  /**  */
  generation_input_tokens: number | null;
  /**  */
  generation_output_tokens: number | null;
  /**  */
  total_tokens: number | null;
}

export interface ModelSettings {
  /**  */
  generation_model: string;
  /**  */
  actual_generation_model: string | null;
  /**  */
  embedding_model: string;
  /**  */
  actual_embedding_model: string | null;
  /**  */
  embedding_dimensions: number;
  /**  */
  temperature: number;
  /**  */
  max_output_tokens: number;
  /**  */
  enable_thinking: boolean;
}

export interface FeedbackWrite {
  /**  */
  rating: "up" | "down";
  /**  */
  reason: "helpful" | "incorrect" | "unsupported" | "missing_information" | "other" | null;
  /**  */
  comment: string;
}

export interface Feedback {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  turn_id: string;
  /**  */
  rating: "up" | "down";
  /**  */
  reason: "helpful" | "incorrect" | "unsupported" | "missing_information" | "other" | null;
  /**  */
  comment: string;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  updated_at: string;
}

export interface SearchHit {
  /** 仅本次请求有效 S1 S2 等 */
  source_id: string;
  /** 业务编号 */
  chunk_id: string;
  /** 业务编号 */
  document_id: string;
  /**  */
  file_name: string;
  /**  */
  page_no: number;
  /**  */
  char_start: number;
  /**  */
  char_end: number;
  /**  */
  text: string;
  /**  */
  bm25_rank: number | null;
  /**  */
  dense_rank: number | null;
  /**  */
  bm25_score: number | null;
  /**  */
  dense_score: number | null;
  /**  */
  rrf_score: number | null;
}

export interface SearchRequest {
  /** 业务编号 */
  knowledge_base_id: string;
  /**  */
  document_ids: Array<string>;
  /**  */
  query: string;
  /** 检索策略 */
  strategy: "bm25" | "dense" | "hybrid";
  /**  */
  top_k?: number;
}

export interface SearchResult {
  /**  */
  query: string;
  /** 检索策略 */
  strategy: "bm25" | "dense" | "hybrid";
  /**  */
  document_ids: Array<string>;
  /**  */
  top_k: number;
  /**  */
  hits: Array<SearchHit>;
  /**  */
  query_embedding_ms: number | null;
  /**  */
  retrieval_ms: number;
  /**  */
  query_embedding_input_tokens: number | null;
  /**  */
  actual_embedding_model: string | null;
}

export interface Turn {
  /** 业务编号 */
  id: string;
  /** 业务编号 */
  conversation_id: string;
  /** 业务编号 */
  client_request_id: string;
  /** 业务编号 */
  retry_of_turn_id: string | null;
  /**  */
  question: string;
  /**  */
  standalone_question: string | null;
  /** 检索策略 */
  strategy: "bm25" | "dense" | "hybrid";
  /**  */
  top_k: number;
  /**  */
  status: "queued" | "rewriting" | "retrieving" | "generating" | "validating" | "answered" | "insufficient_evidence" | "needs_clarification" | "failed" | "interrupted";
  /**  */
  answer_blocks: Array<AnswerBlock>;
  /**  */
  citations: Array<Citation>;
  /**  */
  message: string | null;
  /**  */
  error: ApiError | null;
  /**  */
  retrieval: SearchResult | null;
  /**  */
  timings: Timings;
  /**  */
  token_usage: TokenUsage;
  /**  */
  model_settings: ModelSettings;
  /**  */
  feedback: Feedback | null;
  /** UTC ISO 8601 时间 */
  created_at: string;
  /** UTC ISO 8601 时间 */
  updated_at: string;
  /** UTC ISO 8601 时间 */
  finished_at: string | null;
}

export interface KnowledgeBaseList {
  /**  */
  items: Array<KnowledgeBase>;
  /**  */
  page: number;
  /**  */
  page_size: number;
  /**  */
  total: number;
}

export interface DocumentList {
  /**  */
  items: Array<Document>;
  /**  */
  page: number;
  /**  */
  page_size: number;
  /**  */
  total: number;
}

export interface ChunkList {
  /**  */
  items: Array<Chunk>;
  /**  */
  page: number;
  /**  */
  page_size: number;
  /**  */
  total: number;
}

export interface ConversationList {
  /**  */
  items: Array<Conversation>;
  /**  */
  page: number;
  /**  */
  page_size: number;
  /**  */
  total: number;
}

export interface TurnList {
  /**  */
  items: Array<Turn>;
  /**  */
  page: number;
  /**  */
  page_size: number;
  /**  */
  total: number;
}
