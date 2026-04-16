package com.alicloud.openservices.tablestore.sample.mcp;

import com.alicloud.openservices.tablestore.sample.model.FAQContent;
import com.alicloud.openservices.tablestore.sample.model.KnowledgeContent;
import com.alicloud.openservices.tablestore.sample.model.MarkdownNoteInput;
import com.alicloud.openservices.tablestore.sample.model.SearchFAQQuery;
import com.alicloud.openservices.tablestore.sample.model.SearchKnowledgeQuery;
import com.alicloud.openservices.tablestore.sample.service.RagService;
import com.alicloud.openservices.tablestore.sample.service.TablestoreService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TablestoreMcp {

    /**
     * 注册 MCP tools。
     * 其中 searchKnowledge 走增强后的 RAG 检索流程，ingestMarkdownNote 走 Markdown 导入流程。
     */
    @Bean
    public ToolCallbackProvider myTools(TablestoreService tablestoreService, RagService ragService) {

        FunctionToolCallback<KnowledgeContent, Void> storeKnowledgeTool = FunctionToolCallback.builder("storeKnowledge", tablestoreService::storeKnowledge)
                .description("Store document into knowledge store for later retrieval.")
                .inputType(KnowledgeContent.class)
                .build();

        FunctionToolCallback<MarkdownNoteInput, Void> ingestMarkdownTool = FunctionToolCallback.builder("ingestMarkdownNote", ragService::ingestMarkdownNote)
                .description("Ingest a markdown note with heading-aware chunking and optional FAQs into the knowledge store.")
                .inputType(MarkdownNoteInput.class)
                .build();

        FunctionToolCallback<SearchKnowledgeQuery, List<KnowledgeContent>> searchKnowledgeTools = FunctionToolCallback.builder("searchKnowledge", ragService::searchKnowledge)
                .description("Search for similar documents from knowledge store with hybrid retrieval, RRF fusion, and rerank.")
                .inputType(SearchKnowledgeQuery.class)
                .build();

        FunctionToolCallback<FAQContent, Void> storeFAQTool = FunctionToolCallback.builder("storeFAQ", tablestoreService::storeFAQ)
                .description("Store document into FAQ store for later retrieval.")
                .inputType(FAQContent.class)
                .build();

        FunctionToolCallback<SearchFAQQuery, List<FAQContent>> searchFAQTools = FunctionToolCallback.builder("searchFAQ", tablestoreService::searchFAQ)
                .description("Search for similar documents on natural language descriptions from FAQ store.")
                .inputType(SearchFAQQuery.class)
                .build();

        return ToolCallbackProvider.from(List.of(
                storeKnowledgeTool,
                ingestMarkdownTool,
                searchKnowledgeTools,
                storeFAQTool,
                searchFAQTools
        ));
    }
}
