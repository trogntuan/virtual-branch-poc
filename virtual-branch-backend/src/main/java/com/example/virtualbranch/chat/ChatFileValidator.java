package com.example.virtualbranch.chat;

import com.example.virtualbranch.config.ChatProperties;
import com.example.virtualbranch.document.DocumentEntity;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ChatFileValidator {

    private final ChatProperties chatProperties;

    public ChatFileValidator(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    public void validateUpload(MultipartFile file) {
        if (file.getSize() > chatProperties.maxFileSizeBytes()) {
            throw new ChatValidationException("FILE_TOO_LARGE");
        }
        String contentType = normalizeContentType(file.getContentType());
        String extension = extensionFromFilename(file.getOriginalFilename());
        if (!isAllowedContentType(contentType) && !isAllowedExtension(extension)) {
            throw new ChatValidationException("FILE_TYPE_NOT_ALLOWED");
        }
    }

    public boolean isCollabEligible(DocumentEntity document) {
        String contentType = normalizeContentType(document.getContentType());
        String extension = extensionFromFilename(document.getFileName());
        return chatProperties.collabAllowedContentTypes().contains(contentType)
                || chatProperties.collabAllowedExtensions().contains(extension);
    }

    public boolean isImage(DocumentEntity document) {
        String contentType = normalizeContentType(document.getContentType());
        return contentType.startsWith("image/");
    }

    private boolean isAllowedContentType(String contentType) {
        return contentType != null && chatProperties.allowedContentTypes().contains(contentType);
    }

    private boolean isAllowedExtension(String extension) {
        return extension != null && chatProperties.allowedExtensions().contains(extension);
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    public static String extensionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
