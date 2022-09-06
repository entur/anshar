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

package no.rutebanken.anshar.routes.dataformat;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;
import org.entur.siri.validator.SiriValidator;

import java.util.HashMap;
import java.util.Map;

public class SiriDataFormatHelper {

    private static final HashMap<String, DataFormat> dataformats = new HashMap<>();

    static {
        dataformats.put("internal-siri20", createDataformat("siri20", SiriValidator.Version.VERSION_2_0));
        dataformats.put("internal-siri21", createDataformat("siri21", SiriValidator.Version.VERSION_2_1));
    }

    public static DataFormat getSiriJaxbDataformat() {
    	return getSiriJaxbDataformat(SiriValidator.Version.VERSION_2_0);
    }
    public static DataFormat getSiriJaxbDataformat(SiriValidator.Version version) {
    	return createDataformat("", version);
    }

    public static DataFormat getSiriJaxbDataformat(NamespacePrefixMapper namespacePrefixMapper) {
        return getSiriJaxbDataformat(namespacePrefixMapper, SiriValidator.Version.VERSION_2_0);
    }
    public static DataFormat getSiriJaxbDataformat(NamespacePrefixMapper namespacePrefixMapper, SiriValidator.Version version) {

        if (namespacePrefixMapper != null) {
            String preferredPrefix = namespacePrefixMapper.getPreferredPrefix("", "", true);
            if (preferredPrefix != null) {
                return createDataformat(preferredPrefix, version);
            }
        }

    	return getSiriJaxbDataformat(version);
    }

    private static DataFormat createDataformat(String prefix, SiriValidator.Version version) {
        if (dataformats.containsKey(prefix)) {
            return dataformats.get(prefix);
        }
        Map<String, String> prefixMap = new HashMap<>();
        if (prefix.startsWith("internal")) {
            prefixMap.put("http://www.siri.org.uk/siri", "");
        } else {
            prefixMap.put("http://www.siri.org.uk/siri", prefix);
        }
        String contextPath = "uk.org.siri.siri20";
        if (version.equals(SiriValidator.Version.VERSION_2_1)) {
            contextPath = "uk.org.siri.siri21";
        }

        JaxbDataFormat siriJaxb = new JaxbDataFormat(contextPath);
        siriJaxb.setNamespacePrefix(prefixMap);

        dataformats.put(prefix, siriJaxb);
        return  siriJaxb;
    }


}

