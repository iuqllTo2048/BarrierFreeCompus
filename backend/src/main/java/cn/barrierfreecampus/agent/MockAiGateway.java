package cn.barrierfreecampus.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockAiGateway implements AiGateway {
    @Override
    public String explain(String factualContext) {
        return factualContext;
    }

    @Override
    public String summarizeGovernance(String factualContext) {
        return factualContext;
    }
}
