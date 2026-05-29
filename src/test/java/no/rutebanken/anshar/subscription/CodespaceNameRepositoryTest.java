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

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CodespaceNameRepositoryTest {

    @Test
    public void unsetFileMeansEmptyRepository() {
        CodespaceNameRepository repo = newRepo("");
        assertNull(repo.displayNameFor("NSB"));
        assertNull(repo.displayNameFor(null));
    }

    @Test
    public void missingFileFallsBackToEmpty() {
        CodespaceNameRepository repo = newRepo("classpath:does-not-exist.csv");
        assertNull(repo.displayNameFor("NSB"));
    }

    @Test
    public void loadsEntriesAndLookupIsCaseInsensitive() throws Exception {
        Path tmp = Files.createTempFile("codespacenames-", ".csv");
        Files.writeString(tmp, """
                # comment line, should be ignored
                NSB;Vy (formerly NSB)
                ATB;AtB (Trøndelag)

                BROKEN_NO_SEP
                ;OnlyNameNoCode

                rut;Ruter
                """);
        CodespaceNameRepository repo = newRepo("file:" + tmp.toAbsolutePath());

        assertEquals("Vy (formerly NSB)", repo.displayNameFor("NSB"));
        assertEquals("Vy (formerly NSB)", repo.displayNameFor("nsb"), "lookup is case-insensitive");
        assertEquals("AtB (Trøndelag)", repo.displayNameFor("ATB"));
        assertEquals("Ruter", repo.displayNameFor("RUT"), "lowercase keys in file resolve uppercase queries");
        assertNull(repo.displayNameFor("UNKNOWN"));
    }

    private CodespaceNameRepository newRepo(String filePath) {
        CodespaceNameRepository repo = new CodespaceNameRepository(new DefaultResourceLoader());
        ReflectionTestUtils.setField(repo, "codespaceNamesFile", filePath);
        ReflectionTestUtils.invokeMethod(repo, "load");
        return repo;
    }
}