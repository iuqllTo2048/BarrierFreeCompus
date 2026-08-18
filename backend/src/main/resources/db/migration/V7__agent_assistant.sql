CREATE TABLE ai_conversation (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    title VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_conversation_user_updated ON ai_conversation(user_id, updated_at DESC);

CREATE TABLE ai_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content TEXT NOT NULL,
    request_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_message_conversation_created ON ai_message(conversation_id, created_at);

CREATE TABLE ai_invocation_log (
    id UUID PRIMARY KEY,
    conversation_id UUID REFERENCES ai_conversation(id) ON DELETE SET NULL,
    message_id UUID REFERENCES ai_message(id) ON DELETE SET NULL,
    request_id UUID NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64),
    error_summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_invocation_created ON ai_invocation_log(created_at DESC);
CREATE INDEX idx_ai_invocation_request ON ai_invocation_log(request_id);

CREATE TABLE ai_tool_call_log (
    id UUID PRIMARY KEY,
    invocation_id UUID NOT NULL REFERENCES ai_invocation_log(id) ON DELETE CASCADE,
    tool_name VARCHAR(100) NOT NULL,
    argument_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL,
    error_summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_tool_invocation ON ai_tool_call_log(invocation_id, created_at);

CREATE TABLE ai_action_draft (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    action_type VARCHAR(40) NOT NULL CHECK (action_type IN ('BARRIER_REPORT')),
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','EXPIRED','CANCELLED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_draft_user_status ON ai_action_draft(user_id, status, expires_at);
