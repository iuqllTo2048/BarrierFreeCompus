package cn.barrierfreecampus.agent;

public final class AgentExecutionContext {
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private AgentExecutionContext() {}

    public static void setUsername(String username) { USERNAME.set(username); }
    public static String requireUsername() {
        String username = USERNAME.get();
        if (username == null) throw new IllegalStateException("智能体执行上下文缺少认证用户");
        return username;
    }
    public static void clear() { USERNAME.remove(); }
}
