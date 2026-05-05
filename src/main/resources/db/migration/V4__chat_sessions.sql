CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_sessions_user_updated_at ON chat_sessions (user_id, updated_at);
CREATE INDEX idx_chat_sessions_user_default ON chat_sessions (user_id, is_default);

ALTER TABLE documents ADD COLUMN session_id UUID;
ALTER TABLE documents ADD CONSTRAINT fk_documents_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id);
CREATE INDEX idx_documents_user_session_created_at ON documents (user_id, session_id, created_at);

ALTER TABLE chat_messages ADD COLUMN session_id UUID;
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id);
CREATE INDEX idx_chat_messages_user_session_created_at ON chat_messages (user_id, session_id, created_at);


