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

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        List<PropertySource<?>> propertySource = null;
        try {
            if (name != null) {
                propertySource = new YamlPropertySourceLoader().load(name, resource.getResource());
            } else {
                propertySource = new YamlPropertySourceLoader().load(getNameForResource(resource.getResource()), resource.getResource());
            }
        } catch (Exception fileNotFoundException) {
            //Ignore - look up in filesystem below
        }

        // Properties not found through classpath - resolve properties from absolute path
        if (propertySource == null) {
            String path = ((ClassPathResource) resource.getResource()).getPath();
            if (!path.startsWith("/")) {
                path = "/"+path;
            }
            propertySource = new YamlPropertySourceLoader().load(null, new FileSystemResource(path));
        }

        if (propertySource != null && !propertySource.isEmpty()) {
            return propertySource.get(0);
        }
        return null;
    }

    private static String getNameForResource(Resource resource) {
        String name = resource.getDescription();
        if (!StringUtils.hasText(name)) {
            name = resource.getClass().getSimpleName() + "@" + System.identityHashCode(resource);
        }
        return name;
    }
}