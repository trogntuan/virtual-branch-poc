package com.example.virtualbranch.common;

import com.example.virtualbranch.collab.DocCollabEntity;
import com.example.virtualbranch.document.DocumentEntity;
import com.example.virtualbranch.recording.RecordingEntity;
import com.example.virtualbranch.session.SessionEntity;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPersistenceSecurityTest {

    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "readUrl", "readurl", "playbackUrl", "playbackurl",
            "signedUrl", "signedurl", "presignedUrl", "presignedurl",
            "blob", "binary", "content", "pdfData", "mp4Data"
    );

    @Test
    void entitiesStoreObjectKeysOnlyNotSignedUrls() {
        assertNoForbiddenPersistenceFields(RecordingEntity.class);
        assertNoForbiddenPersistenceFields(DocumentEntity.class);
        assertNoForbiddenPersistenceFields(DocCollabEntity.class);
        assertNoForbiddenPersistenceFields(SessionEntity.class);
    }

    @Test
    void recordingAndDocumentEntitiesHaveObjectKeyField() {
        assertHasField(RecordingEntity.class, "objectKey");
        assertHasField(DocumentEntity.class, "objectKey");
    }

    private static void assertNoForbiddenPersistenceFields(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(FORBIDDEN_FIELD_NAMES.contains(name),
                    () -> entityClass.getSimpleName() + " must not persist field: " + field.getName());
        }
    }

    private static void assertHasField(Class<?> entityClass, String fieldName) {
        boolean found = false;
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                found = true;
                break;
            }
        }
        assertTrue(found, () -> entityClass.getSimpleName() + " should have field: " + fieldName);
    }
}
