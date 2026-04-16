package com.alicloud.openservices.tablestore.sample.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.CrossEncoderTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.util.StringPair;
import com.alicloud.openservices.tablestore.sample.config.EnvironmentSettings;
import com.alicloud.openservices.tablestore.sample.model.KnowledgeContent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class RerankService implements Closeable {

    /**
     * Debug 日志里最多预览前几个候选，避免刷屏。
     */
    private static final int DEBUG_PREVIEW_LIMIT = 5;
    /**
     * Debug 日志中的文本预览最大长度。
     */
    private static final int PREVIEW_MAX_LENGTH = 120;

    /**
     * 本地 rerank 模型目录，例如：D:/models/BAAI/bge-reranker-v2-m3。
     */
    private final Path modelPath;
    /**
     * 使用 tokenizer.json 对 query/document 做双塔输入编码。
     */
    private final HuggingFaceTokenizer tokenizer;
    /**
     * 当前本地模型是 HuggingFace 格式的 cross-encoder，使用 DJL Rust engine 直接从目录加载。
     */
    private final ZooModel<StringPair, float[]> model;

    public RerankService() throws Exception {
        this.modelPath = Path.of(EnvironmentSettings.getLocalRerankModelPath());
        this.tokenizer = HuggingFaceTokenizer.newInstance(modelPath.resolve("tokenizer.json"));
        CrossEncoderTranslator translator = CrossEncoderTranslator.builder(tokenizer)
                // 当前 bge-reranker-v2-m3 需要 token_type_ids，一开始关闭后会报 input size mismatch。
                .optIncludeTokenTypes(true)
                // 检索排序直接使用原始 logits，排序效果更直接。
                .optSigmoid(false)
                .build();
        Criteria<StringPair, float[]> criteria = Criteria.builder()
                .setTypes(StringPair.class, float[].class)
                .optEngine(EnvironmentSettings.getLocalRerankEngine())
                .optModelPath(modelPath)
                .optTranslator(translator)
                .build();
        this.model = criteria.loadModel();
        log.info("loaded local rerank model, engine:{}, path:{}", EnvironmentSettings.getLocalRerankEngine(), modelPath);
        log.debug("[RAG-RERANK] model ready, engine:{}, path:{}", EnvironmentSettings.getLocalRerankEngine(), modelPath);
    }

    /**
     * 直接在 Java 进程内调用本地 rerank 模型，对融合后的候选文档重新打分排序。
     */
    public List<KnowledgeContent> rerank(String query, List<KnowledgeContent> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            log.debug("[RAG-RERANK] skip rerank because candidates are empty");
            return List.of();
        }
        int limit = Math.min(topN, candidates.size());
        log.debug("[RAG-RERANK] start query:{}, candidates:{}, topN:{}", preview(query), candidates.size(), limit);
        List<ScoredKnowledge> scored = new ArrayList<>(candidates.size());
        long startedAt = System.nanoTime();
        try (var predictor = model.newPredictor()) {
            for (int i = 0; i < candidates.size(); i++) {
                KnowledgeContent candidate = candidates.get(i);
                long singleStartedAt = System.nanoTime();
                float[] scoreArray = predictor.predict(new StringPair(query, candidate.getContent()));
                double score = scoreArray.length == 0 ? 0.0d : scoreArray[0];
                scored.add(new ScoredKnowledge(candidate, score));
                if (log.isDebugEnabled() && i < DEBUG_PREVIEW_LIMIT) {
                    log.debug("[RAG-RERANK] candidate #{} score:{}, costMs:{}, headingPath:{}, chars:{}, preview:{}",
                            i + 1, score, elapsedMs(singleStartedAt), metadataValue(candidate, "heading_path"),
                            candidate.getContent() == null ? 0 : candidate.getContent().length(), preview(candidate.getContent()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("local rerank failed at path: " + modelPath, e);
        }
        List<ScoredKnowledge> sorted = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredKnowledge::score).reversed())
                .toList();
        if (log.isDebugEnabled()) {
            for (int i = 0; i < Math.min(DEBUG_PREVIEW_LIMIT, sorted.size()); i++) {
                ScoredKnowledge item = sorted.get(i);
                log.debug("[RAG-RERANK] sorted #{} score:{}, headingPath:{}, preview:{}",
                        i + 1, item.score(), metadataValue(item.knowledgeContent(), "heading_path"), preview(item.knowledgeContent().getContent()));
            }
        }
        List<KnowledgeContent> result = sorted.stream()
                .limit(limit)
                .map(ScoredKnowledge::knowledgeContent)
                .toList();
        log.debug("[RAG-RERANK] finished output:{}, totalCostMs:{}", result.size(), elapsedMs(startedAt));
        return result;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        log.info("closing local rerank model:{}", modelPath);
        if (model != null) {
            model.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }

    private String metadataValue(KnowledgeContent item, String key) {
        if (item == null || item.getMetaData() == null) {
            return "";
        }
        Object value = item.getMetaData().get(key);
        return value == null ? "" : value.toString();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
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

    private record ScoredKnowledge(KnowledgeContent knowledgeContent, double score) {
    }
}
