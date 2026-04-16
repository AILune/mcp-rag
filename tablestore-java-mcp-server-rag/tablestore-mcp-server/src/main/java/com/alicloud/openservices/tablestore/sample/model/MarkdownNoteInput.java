package com.alicloud.openservices.tablestore.sample.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonClassDescription("A markdown note that will be chunked and stored into the knowledge store")
public class MarkdownNoteInput {

    /**
     * source/title/markdown 用于知识块导入；faqs 用于接收外部基于提示词提取好的 FAQ。
     */

    @JsonProperty(required = false, value = "source")
    @JsonPropertyDescription("the source filename or url of this markdown note")
    private String source = "";

    @JsonProperty(required = false, value = "title")
    @JsonPropertyDescription("the title of this markdown note")
    private String title = "";

    @JsonProperty(required = true, value = "markdown")
    @JsonPropertyDescription("the raw markdown note content")
    private String markdown = "";

    @JsonProperty(required = false, value = "faqs")
    @JsonPropertyDescription("optional FAQs extracted by prompt engineering before ingestion")
    private List<FAQContent> faqs = new ArrayList<>();
}
