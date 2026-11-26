CREATE TABLE document_payloads (
    document_id UUID PRIMARY KEY REFERENCES encrypted_documents(id) ON DELETE CASCADE,
    ciphertext BYTEA NOT NULL
);
