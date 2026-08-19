package cn.barrierfreecampus.agent;

public interface AiGateway {
    String explain(String factualContext);

    String summarizeGovernance(String factualContext);
}
