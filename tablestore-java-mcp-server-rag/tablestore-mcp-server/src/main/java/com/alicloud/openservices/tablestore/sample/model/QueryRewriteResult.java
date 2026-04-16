package com.alicloud.openservices.tablestore.sample.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryRewriteResult {

    private String rewriteQuery = "";
    private List<String> subQueries = new ArrayList<>();

    public List<String> collectQueries(String originalQuery) {
        Set<String> queries = new LinkedHashSet<>();
        addIfNotBlank(queries, originalQuery);
        addIfNotBlank(queries, rewriteQuery);
        if (subQueries != null) {
            for (String subQuery : subQueries) {
                addIfNotBlank(queries, subQuery);
            }
        }
        return new ArrayList<>(queries);
    }

    private void addIfNotBlank(Set<String> queries, String query) {
        if (query != null && !query.isBlank()) {
            queries.add(query.trim());
        }
    }
}
