package com.digitalwill.crypto.storage;

import com.digitalwill.crypto.exception.CryptoException;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** New uploads share the metadata transaction. Existing filesystem references still resolve. */
@Service
@Primary
public class DatabaseDocumentStorageService implements DocumentStorageService {
    private static final String PREFIX = "db:";
    private final JdbcTemplate database;
    private final LocalDocumentStorageService legacyStorage;

    public DatabaseDocumentStorageService(JdbcTemplate database, LocalDocumentStorageService legacyStorage) {
        this.database = database;
        this.legacyStorage = legacyStorage;
    }

    @Override
    public String referenceFor(UUID willId, UUID documentId) {
        return PREFIX + documentId;
    }

    @Override
    @Transactional
    public String store(UUID willId, UUID documentId, byte[] ciphertext) {
        int changed = database.update("UPDATE document_payloads SET ciphertext = ? WHERE document_id = ?", ciphertext, documentId);
        if (changed == 0) {
            database.update("INSERT INTO document_payloads (document_id, ciphertext) VALUES (?, ?)", documentId, ciphertext);
        }
        return referenceFor(willId, documentId);
    }

    @Override
    public byte[] retrieve(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) return legacyStorage.retrieve(reference);
        try {
            return database.queryForObject("SELECT ciphertext FROM document_payloads WHERE document_id = ?",
                    byte[].class, UUID.fromString(reference.substring(PREFIX.length())));
        } catch (EmptyResultDataAccessException missing) {
            throw new CryptoException("Encrypted document file not found");
        }
    }

    @Override
    public void delete(String reference) {
        if (reference != null && reference.startsWith(PREFIX)) {
            database.update("DELETE FROM document_payloads WHERE document_id = ?", UUID.fromString(reference.substring(PREFIX.length())));
        } else {
            legacyStorage.delete(reference);
        }
    }
}
