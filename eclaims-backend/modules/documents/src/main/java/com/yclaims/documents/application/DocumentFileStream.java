package com.yclaims.documents.application;

import org.springframework.core.io.Resource;

/**
 * Resolved on-disk file for streaming to clients (authenticated download by document id).
 */
public record DocumentFileStream(Resource resource, String contentType, String filename) {}
