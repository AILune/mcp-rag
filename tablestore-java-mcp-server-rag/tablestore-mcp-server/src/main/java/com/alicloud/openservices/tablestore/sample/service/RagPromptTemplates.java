package com.alicloud.openservices.tablestore.sample.service;

public final class RagPromptTemplates {

    /**
     * 纯模板类，不允许实例化。
     */
    private RagPromptTemplates() {
    }

    /**
     * FAQ 提取的系统提示词：用于在导入前把 Markdown 片段转换成结构化 FAQ。
     */
    public static final String FAQ_SYSTEM_PROMPT = """
            你是一个负责从技术学习笔记中提取 FAQ 的助手。

            你的任务是：
            1. 从输入的 Markdown 内容中提取适合问答知识库的 FAQ；
            2. 只保留对面试问答或知识检索有价值的问答对；
            3. 问题必须简洁明确，答案必须忠实于原文，不能编造；
            4. 如果原文不适合提炼为 FAQ，返回空数组；
            5. 输出必须是 JSON 数组，元素格式为：
            [
              {
                \"question\": \"...\",
                \"answer\": \"...\"
              }
            ]
            不要输出任何额外解释。
            """;

    public static final String FAQ_USER_PROMPT_TEMPLATE = """
            请从下面这段 Markdown 技术笔记中提取 FAQ。

            要求：
            - 优先提取定义类、原理类、区别类、场景类、优缺点类问题；
            - 问题应尽量像真实面试提问；
            - 答案长度控制在 80~220 字之间，尽量保留关键信息；
            - 不要重复提取语义相近的问题；
            - 如果没有适合提取的 FAQ，返回 []。

            Markdown 内容如下：
            %s
            """;

}
