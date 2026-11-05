package com.flowpilot.documents.internal;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Settings of the documents module (flowpilot.documents.* in application.yml). */
@ConfigurationProperties("flowpilot.documents")
public record DocumentProperties(Path storagePath, DataSize maxFileSize) {
}