package com.example.virtualbranch.storage;

import java.io.InputStream;

public interface ObjectStorageService {

    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    String presignGetUrl(String objectKey, int expirySeconds);
}
