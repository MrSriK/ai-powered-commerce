CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);
CREATE TRIGGER trg_ai_conversations_updated_at
  BEFORE UPDATE ON ai_conversations
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id),
    user_input TEXT NOT NULL,
    ai_output TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);
CREATE TRIGGER trg_ai_messages_updated_at
  BEFORE UPDATE ON ai_messages
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
