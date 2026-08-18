package cn.barrierfreecampus.agent;

import static cn.barrierfreecampus.agent.AgentDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class AgentRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AgentRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public long userId(String username) {
        return jdbc.query("SELECT id FROM app_user WHERE username=? AND enabled=TRUE",
                        (rs, row) -> rs.getLong(1), username)
                .stream().findFirst().orElseThrow(() -> notFound("用户不存在或已停用"));
    }

    public ConversationView createConversation(String username, String title, AiProperties properties) {
        UUID id = UUID.randomUUID();
        long userId = userId(username);
        jdbc.update("""
                INSERT INTO ai_conversation(id,user_id,title,provider,model_name)
                VALUES (?,?,?,?,?)
                """, id, userId, title, properties.provider(), properties.effectiveModelName());
        return conversation(username, id);
    }

    public List<ConversationView> conversations(String username) {
        return jdbc.query("""
                SELECT c.* FROM ai_conversation c JOIN app_user u ON u.id=c.user_id
                WHERE u.username=? ORDER BY c.updated_at DESC LIMIT 50
                """, this::conversationRow, username);
    }

    public ConversationView conversation(String username, UUID id) {
        return jdbc.query("""
                SELECT c.* FROM ai_conversation c JOIN app_user u ON u.id=c.user_id
                WHERE c.id=? AND u.username=?
                """, this::conversationRow, id, username)
                .stream().findFirst().orElseThrow(() -> notFound("对话不存在"));
    }

    public List<MessageView> messages(String username, UUID conversationId) {
        conversation(username, conversationId);
        return jdbc.query("""
                SELECT m.* FROM ai_message m WHERE m.conversation_id=? ORDER BY m.created_at,m.id
                """, this::messageRow, conversationId);
    }

    public UUID addMessage(UUID conversationId, String role, String content, UUID requestId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_message(id,conversation_id,role,content,request_id) VALUES (?,?,?,?,?)",
                id, conversationId, role, content, requestId);
        jdbc.update("UPDATE ai_conversation SET updated_at=CURRENT_TIMESTAMP WHERE id=?", conversationId);
        return id;
    }

    public UUID startInvocation(String username, UUID conversationId, UUID messageId, UUID requestId,
                                AiProperties properties) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_invocation_log(id,conversation_id,message_id,request_id,user_id,provider,model_name)
                VALUES (?,?,?,?,?,?,?)
                """, id, conversationId, messageId, requestId, userId(username), properties.provider(),
                properties.effectiveModelName());
        return id;
    }

    public void finishInvocation(UUID id, long latencyMs, boolean success, String errorCode, String errorSummary) {
        jdbc.update("""
                UPDATE ai_invocation_log SET latency_ms=?,success=?,error_code=?,error_summary=? WHERE id=?
                """, latencyMs, success, errorCode, truncate(errorSummary, 500), id);
    }

    public void logTool(UUID invocationId, String toolName, Object arguments, Object result,
                        long latencyMs, boolean success, String error) {
        jdbc.update("""
                INSERT INTO ai_tool_call_log(id,invocation_id,tool_name,argument_summary,result_summary,
                  latency_ms,success,error_summary)
                VALUES (?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?)
                """, UUID.randomUUID(), invocationId, toolName, json(arguments), json(result), latencyMs,
                success, truncate(error, 500));
    }

    public void saveDraft(UUID id, UUID conversationId, String username, Object payload, OffsetDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO ai_action_draft(id,conversation_id,user_id,action_type,payload_json,expires_at)
                VALUES (?,?,?,'BARRIER_REPORT',CAST(? AS jsonb),?)
                """, id, conversationId, userId(username), json(payload), expiresAt);
    }

    public void confirmDraft(UUID id, String username) {
        int changed = jdbc.update("""
                UPDATE ai_action_draft SET status='CONFIRMED'
                WHERE id=? AND user_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP
                """, id, userId(username));
        if (changed == 0) throw notFound("草稿不存在、已处理或已过期");
    }

    public List<InvocationView> invocations() {
        return jdbc.query("""
                SELECT l.*,u.username FROM ai_invocation_log l JOIN app_user u ON u.id=l.user_id
                ORDER BY l.created_at DESC LIMIT 200
                """, (rs, row) -> new InvocationView(
                rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                rs.getObject("request_id", UUID.class), rs.getString("username"), rs.getString("provider"),
                rs.getString("model_name"), rs.getLong("latency_ms"), rs.getBoolean("success"),
                rs.getString("error_code"), rs.getString("error_summary"), tools(rs.getObject("id", UUID.class)),
                rs.getObject("created_at", OffsetDateTime.class)));
    }

    private List<ToolCallView> tools(UUID invocationId) {
        return jdbc.query("""
                SELECT tool_name,argument_summary::text,result_summary::text,latency_ms,success,error_summary
                FROM ai_tool_call_log WHERE invocation_id=? ORDER BY created_at
                """, (rs, row) -> new ToolCallView(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getBoolean(5), rs.getString(6)), invocationId);
    }

    private ConversationView conversationRow(ResultSet rs, int row) throws SQLException {
        return new ConversationView(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("status"), rs.getString("provider"), rs.getString("model_name"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private MessageView messageRow(ResultSet rs, int row) throws SQLException {
        return new MessageView(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("content"),
                rs.getObject("request_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String truncate(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
