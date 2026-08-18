package com.careerops.backend.pkbimport.extraction;

import java.io.InputStream;

public interface DocumentTextExtractor {
    boolean supports(String lowerCaseExtension);

    String extract(InputStream inputStream);
}
