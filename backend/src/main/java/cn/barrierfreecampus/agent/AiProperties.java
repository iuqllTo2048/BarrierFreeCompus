package cn.barrierfreecampus.agent;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String modelName = "";

    @PostConstruct
    void validate() {
        if (enabled && (baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank())) {
            throw new IllegalStateException("AI_ENABLED=true 时必须配置 AI_BASE_URL、AI_API_KEY 和 AI_MODEL_NAME");
        }
    }

    public String provider() {
        if (!enabled) return "MOCK";
        return baseUrl.toLowerCase().contains("deepseek") ? "DEEPSEEK" : "OPENAI_COMPATIBLE";
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName == null ? "" : modelName; }
    public String effectiveModelName() { return enabled ? modelName : "deterministic-mock"; }
}
