package cn.barrierfreecampus.agent;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentSafetyPolicy {
    public boolean isDisallowed(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("忽略规则") || lower.contains("删除道路") || lower.contains("冒充管理员")
                || lower.contains("执行sql") || lower.contains("执行 sql") || lower.contains("select *")
                || lower.contains("输出key") || lower.contains("输出 key") || lower.contains("api_key")
                || lower.contains("shell") || lower.contains("系统提示词");
    }
}
