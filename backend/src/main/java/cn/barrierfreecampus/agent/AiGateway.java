package cn.barrierfreecampus.agent;

public interface AiGateway {
    String routeAssistant(String runtimeContextAndUserMessage);

    String summarizeGovernance(String factualContext);
}
