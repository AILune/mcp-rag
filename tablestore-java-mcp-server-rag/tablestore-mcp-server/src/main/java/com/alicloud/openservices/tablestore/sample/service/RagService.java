package com.alicloud.openservices.tablestore.sample.service;

import com.alicloud.openservices.tablestore.sample.config.EnvironmentSettings;
import com.alicloud.openservices.tablestore.sample.model.FAQContent;
import com.alicloud.openservices.tablestore.sample.model.KnowledgeContent;
import com.alicloud.openservices.tablestore.sample.model.MarkdownNoteInput;
import com.alicloud.openservices.tablestore.sample.model.SearchKnowledgeQuery;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    /**
     * 识别 Markdown 标题，用于先按标题切 section。
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    /**
     * 融合检索结果时用于去重的元数据字段。
     */
    private static final String CHUNK_ID_KEY = "_chunk_id";
    /**
     * Debug 日志里最多预览前几个候选，避免刷屏。
     */
    private static final int DEBUG_PREVIEW_LIMIT = 5;
    /**
     * Debug 日志中的文本预览最大长度。
     */
    private static final int PREVIEW_MAX_LENGTH = 120;
    /**
     * 递归字符切分时优先使用的分隔符，优先保留段落、换行和中文语义边界。
     */
    private static final List<String> SPLIT_SEPARATORS = List.of("\n\n", "\n", "。", "！", "？", ".", "!", "?", "，", "、", " ");

    private final TablestoreService tablestoreService;
    private final RerankService rerankService;

    /**
     * 启动时打印当前 RAG 关键参数，方便在终端确认简历里提到的链路配置是否生效。
     */
    @PostConstruct
    public void logRagConfig() {
        log.debug("[RAG-CONFIG] chunkSize:{}, chunkOverlap:{}, vectorRecallSize:{}, bm25RecallSize:{}, rrfK:{}, rrfFusedSize:{}, rerankSize:{}",
                EnvironmentSettings.getChunkSize(),
                EnvironmentSettings.getChunkOverlap(),
                EnvironmentSettings.getVectorRecallSize(),
                EnvironmentSettings.getBm25RecallSize(),
                EnvironmentSettings.getRrfK(),
                EnvironmentSettings.getRrfFusedSize(),
                EnvironmentSettings.getRerankSize());
    }

    /**
     * 导入一篇 Markdown 笔记：先分块入知识库，再把外部通过提示词提取出的 FAQ 一并入 FAQ 库。
     */
    public Void ingestMarkdownNote(MarkdownNoteInput input) {
        if (input == null || input.getMarkdown() == null || input.getMarkdown().isBlank()) {
            throw new IllegalArgumentException("markdown note is empty");
        }
        String title = pickTitle(input);
        String docId = UUID.randomUUID().toString();
        int inputFaqCount = input.getFaqs() == null ? 0 : input.getFaqs().size();
        log.debug("[RAG-INGEST] start source:{}, title:{}, markdownChars:{}, inputFaqs:{}",
                safeString(input.getSource()), title, input.getMarkdown().length(), inputFaqCount);

        List<MarkdownChunk> chunks = chunkMarkdown(input.getMarkdown(), title);
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("doc_id", docId);
            metadata.put("source", safeString(input.getSource()));
            metadata.put("title", title);
            metadata.put("heading_path", chunk.headingPath());
            metadata.put("chunk_index", i);
            metadata.put("chunk_size", chunk.content().length());
            log.debug("[RAG-INGEST] store chunk {}/{}, headingPath:{}, chars:{}, preview:{}",
                    i + 1, chunks.size(), chunk.headingPath(), chunk.content().length(), preview(chunk.content()));
            // 每个 chunk 单独入库，方便后续做向量检索、全文检索和 rerank。
            tablestoreService.storeKnowledge(new KnowledgeContent(chunk.content(), metadata));
        }

        int storedFaqCount = 0;
        if (input.getFaqs() != null) {
            for (FAQContent faq : input.getFaqs()) {
                if (faq != null && faq.getQuestion() != null && !faq.getQuestion().isBlank() && faq.getAnswer() != null && !faq.getAnswer().isBlank()) {
                    storedFaqCount++;
                    log.debug("[RAG-INGEST] store faq {}/{}, question:{}, answerChars:{}",
                            storedFaqCount, inputFaqCount, preview(faq.getQuestion()), faq.getAnswer().length());
                    tablestoreService.storeFAQ(faq);
                }
            }
        }

        log.debug("[RAG-INGEST] finished docId:{}, title:{}, chunkCount:{}, storedFaqs:{}",
                docId, title, chunks.size(), storedFaqCount);
        log.info("ingested markdown note, title:{}, chunks:{}, faqs:{}", title, chunks.size(), storedFaqCount);
        return null;
    }

    /**
     * 检索流程：由 MCP Client 侧 LLM 先完成 query rewrite / 拆解，
     * 这里仅负责执行混合召回、RRF 融合和本地 rerank。
     */
    public List<KnowledgeContent> searchKnowledge(SearchKnowledgeQuery request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return List.of();
        }
        long startedAt = System.nanoTime();
        String query = request.getQuery().trim();
        int requestSize = Math.max(1, request.getSize());
        int rerankTopN = Math.min(requestSize, EnvironmentSettings.getRerankSize());
        log.debug("[RAG-SEARCH] start query:{}, requestSize:{}, vectorRecall:{}, bm25Recall:{}, rrfFusedSize:{}, rerankTopN:{}。说明：query 可能已由 MCP Client 侧 LLM 完成 rewrite/decomposition",
                preview(query), request.getSize(), EnvironmentSettings.getVectorRecallSize(),
                EnvironmentSettings.getBm25RecallSize(), EnvironmentSettings.getRrfFusedSize(), rerankTopN);

        List<KnowledgeContent> vectorResults = tablestoreService.searchKnowledgeByVector(query, EnvironmentSettings.getVectorRecallSize());
        log.debug("[RAG-SEARCH] vector recall finished, hits:{}", vectorResults.size());
        debugLogKnowledgeList("vector recall", vectorResults, DEBUG_PREVIEW_LIMIT);

        List<KnowledgeContent> textResults = tablestoreService.searchKnowledgeByText(query, EnvironmentSettings.getBm25RecallSize());
        log.debug("[RAG-SEARCH] bm25 recall finished, hits:{}", textResults.size());
        debugLogKnowledgeList("bm25 recall", textResults, DEBUG_PREVIEW_LIMIT);

        Map<String, RankedKnowledge> fused = new LinkedHashMap<>();
        fuseByRrf(fused, vectorResults, "vector");
        fuseByRrf(fused, textResults, "bm25");

        List<RankedKnowledge> fusedRanked = fused.values().stream()
                .sorted(Comparator.comparingDouble(RankedKnowledge::score).reversed())
                .toList();
        log.debug("[RAG-SEARCH] rrf fused finished, uniqueHits:{}, selectedTopN:{}",
                fusedRanked.size(), EnvironmentSettings.getRrfFusedSize());
        debugLogRankedKnowledgeList("rrf fused", fusedRanked, DEBUG_PREVIEW_LIMIT);

        List<KnowledgeContent> fusedTopN = fusedRanked.stream()
                .limit(EnvironmentSettings.getRrfFusedSize())
                .map(RankedKnowledge::knowledgeContent)
                .toList();
        debugLogKnowledgeList("rrf selected", fusedTopN, DEBUG_PREVIEW_LIMIT);
        if (fusedTopN.isEmpty()) {
            log.debug("[RAG-SEARCH] no candidates after hybrid retrieval, totalCostMs:{}", elapsedMs(startedAt));
            return List.of();
        }

        List<KnowledgeContent> reranked = rerankService.rerank(query, fusedTopN, rerankTopN);
        log.debug("[RAG-SEARCH] finished finalHits:{}, totalCostMs:{}", reranked.size(), elapsedMs(startedAt));
        debugLogKnowledgeList("rerank final", reranked, DEBUG_PREVIEW_LIMIT);
        return reranked;
    }

    /**
     * 使用 RRF 对多路召回结果做融合，同一个 chunk 的分数会累加。
     */
    private void fuseByRrf(Map<String, RankedKnowledge> fused, List<KnowledgeContent> rankedResults, String source) {
        for (int i = 0; i < rankedResults.size(); i++) {
            KnowledgeContent item = rankedResults.get(i);
            String chunkId = extractChunkId(item, i);
            double score = 1.0d / (EnvironmentSettings.getRrfK() + i + 1);
            RankedKnowledge existing = fused.get(chunkId);
            if (existing == null) {
                fused.put(chunkId, new RankedKnowledge(item, score));
            } else {
                existing.addScore(score);
            }
            if (log.isDebugEnabled() && i < DEBUG_PREVIEW_LIMIT) {
                double cumulativeScore = fused.get(chunkId).score();
                log.debug("[RAG-RRF] source:{}, rank:{}, chunkId:{}, addScore:{}, cumulativeScore:{}, headingPath:{}, preview:{}",
                        source, i + 1, chunkId, score, cumulativeScore,
                        metadataValue(item, "heading_path"), preview(item.getContent()));
            }
        }
    }

    private String extractChunkId(KnowledgeContent item, int fallbackIndex) {
        if (item.getMetaData() != null) {
            Object chunkId = item.getMetaData().get(CHUNK_ID_KEY);
            if (chunkId != null && !chunkId.toString().isBlank()) {
                return chunkId.toString();
            }
        }
        return item.getContent() + "#" + fallbackIndex;
    }

    /**
     * Markdown 分块策略：先按标题切 section，再在 section 内做递归字符切分，
     * 并把标题路径拼接到内容前面，增强检索语义。
     */
    private List<MarkdownChunk> chunkMarkdown(String markdown, String title) {
        List<MarkdownSection> sections = splitSections(markdown, title);
        log.debug("[RAG-INGEST] markdown split into {} sections, fallbackTitle:{}", sections.size(), title);
        debugLogSections(sections, DEBUG_PREVIEW_LIMIT);

        List<MarkdownChunk> chunks = new ArrayList<>();
        for (MarkdownSection section : sections) {
            String prefix = section.headingPath().isBlank() ? "" : "标题路径：" + section.headingPath().trim() + "\n\n";
            int bodyLimit = Math.max(200, EnvironmentSettings.getChunkSize() - prefix.length());
            List<String> bodies = splitRecursively(section.content().trim(), bodyLimit);
            log.debug("[RAG-INGEST] section headingPath:{}, sectionChars:{}, bodyLimit:{}, splitChunks:{}",
                    section.headingPath(), section.content().length(), bodyLimit, bodies.size());
            for (String body : bodies) {
                String content = (prefix + body).trim();
                if (!content.isBlank()) {
                    chunks.add(new MarkdownChunk(section.headingPath(), content));
                }
            }
        }
        List<MarkdownChunk> overlapped = applyOverlap(chunks, EnvironmentSettings.getChunkOverlap());
        log.debug("[RAG-INGEST] chunking finished sections:{}, chunksBeforeOverlap:{}, chunksAfterOverlap:{}, overlap:{}",
                sections.size(), chunks.size(), overlapped.size(), EnvironmentSettings.getChunkOverlap());
        debugLogChunks(overlapped, DEBUG_PREVIEW_LIMIT);
        return overlapped;
    }

    /**
     * 按 Markdown 标题层级切分 section；代码块中的 # 不参与标题识别。
     */
    private List<MarkdownSection> splitSections(String markdown, String fallbackTitle) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentPath = fallbackTitle;
        boolean inCodeBlock = false;

        String[] lines = markdown.split("\\r?\\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            }
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (!inCodeBlock && matcher.matches()) {
                addSection(sections, currentPath, current);
                int level = matcher.group(1).length();
                String heading = matcher.group(2).trim();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading);
                currentPath = String.join(" > ", headingStack);
                current = new StringBuilder();
                current.append(line).append("\n");
                continue;
            }
            current.append(line).append("\n");
        }
        addSection(sections, currentPath, current);
        if (sections.isEmpty()) {
            sections.add(new MarkdownSection(fallbackTitle, markdown));
        }
        return sections;
    }

    private void addSection(List<MarkdownSection> sections, String headingPath, StringBuilder current) {
        String text = current.toString().trim();
        if (!text.isBlank()) {
            sections.add(new MarkdownSection(safeString(headingPath), text));
        }
    }

    /**
     * 递归字符切分：优先按段落、换行和中文标点切，切不动时再退化为固定窗口切分。
     */
    private List<String> splitRecursively(String text, int limit) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }
        if (trimmed.length() <= limit) {
            return List.of(trimmed);
        }
        for (String separator : SPLIT_SEPARATORS) {
            List<String> pieces = splitWithSeparator(trimmed, separator);
            if (pieces.size() <= 1) {
                continue;
            }
            List<String> merged = mergePieces(pieces, limit);
            boolean allWithinLimit = merged.stream().allMatch(piece -> piece.length() <= limit);
            if (allWithinLimit) {
                log.debug("[RAG-INGEST] recursive split resolved by separator:{}, inputChars:{}, outputPieces:{}, limit:{}",
                        printableSeparator(separator), trimmed.length(), merged.size(), limit);
                return merged;
            }
            List<String> recursive = new ArrayList<>();
            for (String piece : merged) {
                recursive.addAll(splitRecursively(piece, limit));
            }
            log.debug("[RAG-INGEST] recursive split continued by separator:{}, inputChars:{}, intermediatePieces:{}, finalPieces:{}, limit:{}",
                    printableSeparator(separator), trimmed.length(), merged.size(), recursive.size(), limit);
            return recursive;
        }
        List<String> fixedWindowChunks = fixedWindowSplit(trimmed, limit);
        log.debug("[RAG-INGEST] recursive split fallback to fixed window, inputChars:{}, outputPieces:{}, limit:{}",
                trimmed.length(), fixedWindowChunks.size(), limit);
        return fixedWindowChunks;
    }

    private List<String> splitWithSeparator(String text, String separator) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (true) {
            int index = text.indexOf(separator, start);
            if (index < 0) {
                String piece = text.substring(start).trim();
                if (!piece.isBlank()) {
                    pieces.add(piece);
                }
                return pieces;
            }
            int end = index + separator.length();
            String piece = text.substring(start, end).trim();
            if (!piece.isBlank()) {
                pieces.add(piece);
            }
            start = end;
        }
    }

    private List<String> mergePieces(List<String> pieces, int limit) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (piece.length() > limit) {
                if (current.length() > 0) {
                    merged.add(current.toString().trim());
                    current = new StringBuilder();
                }
                merged.add(piece.trim());
                continue;
            }
            if (current.length() == 0) {
                current.append(piece);
                continue;
            }
            if (current.length() + 1 + piece.length() <= limit) {
                current.append("\n").append(piece);
            } else {
                merged.add(current.toString().trim());
                current = new StringBuilder(piece);
            }
        }
        if (current.length() > 0) {
            merged.add(current.toString().trim());
        }
        return merged;
    }

    private List<String> fixedWindowSplit(String text, int limit) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private List<MarkdownChunk> applyOverlap(List<MarkdownChunk> chunks, int overlap) {
        if (chunks.isEmpty() || overlap <= 0) {
            return chunks;
        }
        List<MarkdownChunk> result = new ArrayList<>(chunks.size());
        String previousBody = "";
        for (MarkdownChunk chunk : chunks) {
            String content = chunk.content();
            if (!previousBody.isBlank()) {
                String prefix = previousBody.substring(Math.max(0, previousBody.length() - overlap));
                content = (prefix + "\n" + content).trim();
            }
            result.add(new MarkdownChunk(chunk.headingPath(), content));
            previousBody = chunk.content();
        }
        return result;
    }

    private void debugLogSections(List<MarkdownSection> sections, int limit) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (int i = 0; i < Math.min(limit, sections.size()); i++) {
            MarkdownSection section = sections.get(i);
            log.debug("[RAG-INGEST] section #{} headingPath:{}, chars:{}, preview:{}",
                    i + 1, section.headingPath(), section.content().length(), preview(section.content()));
        }
    }

    private void debugLogChunks(List<MarkdownChunk> chunks, int limit) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (int i = 0; i < Math.min(limit, chunks.size()); i++) {
            MarkdownChunk chunk = chunks.get(i);
            log.debug("[RAG-INGEST] chunk preview #{} headingPath:{}, chars:{}, preview:{}",
                    i + 1, chunk.headingPath(), chunk.content().length(), preview(chunk.content()));
        }
    }

    private void debugLogKnowledgeList(String stage, List<KnowledgeContent> items, int limit) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (int i = 0; i < Math.min(limit, items.size()); i++) {
            KnowledgeContent item = items.get(i);
            log.debug("[RAG-SEARCH] {} #{} chunkId:{}, headingPath:{}, chars:{}, preview:{}",
                    stage, i + 1, extractChunkId(item, i), metadataValue(item, "heading_path"),
                    item.getContent() == null ? 0 : item.getContent().length(), preview(item.getContent()));
        }
    }

    private void debugLogRankedKnowledgeList(String stage, List<RankedKnowledge> items, int limit) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (int i = 0; i < Math.min(limit, items.size()); i++) {
            RankedKnowledge item = items.get(i);
            log.debug("[RAG-SEARCH] {} #{} score:{}, chunkId:{}, headingPath:{}, preview:{}",
                    stage, i + 1, item.score(), extractChunkId(item.knowledgeContent(), i),
                    metadataValue(item.knowledgeContent(), "heading_path"), preview(item.knowledgeContent().getContent()));
        }
    }

    private String metadataValue(KnowledgeContent item, String key) {
        if (item == null || item.getMetaData() == null) {
            return "";
        }
        Object value = item.getMetaData().get(key);
        return value == null ? "" : value.toString();
    }

    private String printableSeparator(String separator) {
        return separator
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String pickTitle(MarkdownNoteInput input) {
        if (input.getTitle() != null && !input.getTitle().isBlank()) {
            return input.getTitle().trim();
        }
        if (input.getSource() != null && !input.getSource().isBlank()) {
            return input.getSource().trim();
        }
        return "markdown-note";
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }

    private static class RankedKnowledge {
        private final KnowledgeContent knowledgeContent;
        private double score;

        private RankedKnowledge(KnowledgeContent knowledgeContent, double score) {
            this.knowledgeContent = knowledgeContent;
            this.score = score;
        }

        public KnowledgeContent knowledgeContent() {
            return knowledgeContent;
        }

        public double score() {
            return score;
        }

        public void addScore(double score) {
            this.score += score;
        }
    }

    private record MarkdownSection(String headingPath, String content) {
    }

    private record MarkdownChunk(String headingPath, String content) {
    }
}
