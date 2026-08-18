package cn.barrierfreecampus.agent;

import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class LangChain4jAiGateway implements AiGateway {
    private final OpenAiChatModel model;

    public LangChain4jAiGateway(AiProperties properties) {
        this.model = OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(20))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Override
    public String explain(String factualContext) {
        String prompt = """
                你是无碍智行的智能路线解释层。只能依据下方经过后端白名单工具验证的数据回答。
                不得声称执行数据库、删除、审核、角色修改或系统命令；不得输出密钥或隐藏思维链。
                使用简洁中文，先给结论，再说明风险和取舍。若信息不足，明确说明。

                已验证业务数据：
                """ + factualContext;
        return model.chat(prompt);
    }
}
