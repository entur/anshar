/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.subscription;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Optional mapping from codespace (datasetId) to a human-readable display name,
 * loaded from a configurable file. When {@code anshar.validation.codespacenames.file}
 * is unset, the repository is empty and {@link #displayNameFor(String)} returns
 * {@code null} for every input.
 *
 * <p>File format: semicolon-separated CSV, one entry per line:
 * {@code CODESPACE;Display Name}. Blank lines and lines starting with
 * {@code #} are ignored. Lookups are case-insensitive on the codespace key.
 */
@Component
public class CodespaceNameRepository {

    private static final Logger logger = LoggerFactory.getLogger(CodespaceNameRepository.class);

    private final ResourceLoader resourceLoader;

    @Value("${anshar.validation.codespacenames.file:}")
    private String codespaceNamesFile;

    private Map<String, String> displayNames = Collections.emptyMap();

    public CodespaceNameRepository(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void load() {
        if (codespaceNamesFile == null || codespaceNamesFile.isBlank()) {
            logger.info("No codespace name mapping configured (anshar.validation.codespacenames.file is empty)");
            return;
        }
        Resource resource = resourceLoader.getResource(codespaceNamesFile);
        if (!resource.exists()) {
            logger.warn("Codespace names file not found: {} — falling back to codespace IDs", codespaceNamesFile);
            return;
        }
        Map<String, String> loaded = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int sep = trimmed.indexOf(';');
                if (sep <= 0 || sep == trimmed.length() - 1) {
                    logger.warn("Skipping malformed codespace name entry: {}", trimmed);
                    continue;
                }
                String codespace = trimmed.substring(0, sep).trim().toLowerCase(Locale.ROOT);
                String name = trimmed.substring(sep + 1).trim();
                loaded.put(codespace, name);
            }
        } catch (Exception e) {
            logger.warn("Failed to load codespace names from {}: {}", codespaceNamesFile, e.getMessage());
            return;
        }
        displayNames = Map.copyOf(loaded);
        logger.info("Loaded {} codespace display names from {}", displayNames.size(), codespaceNamesFile);
    }

    public String displayNameFor(String codespace) {
        if (codespace == null) {
            return null;
        }
        return displayNames.get(codespace.toLowerCase(Locale.ROOT));
    }
}