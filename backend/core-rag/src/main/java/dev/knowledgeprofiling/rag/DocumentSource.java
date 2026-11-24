package dev.knowledgeprofiling.rag;

import java.io.IOException;
import java.util.List;

/** Extracts a supported file into page-like text units while preserving location information. */
public interface DocumentSource {
    boolean supports(String filename, String contentType);
    String sourceType();
    List<ExtractedPage> extract(byte[] content) throws IOException;
}
