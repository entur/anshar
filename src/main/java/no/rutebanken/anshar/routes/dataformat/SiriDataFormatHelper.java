package no.rutebanken.anshar.routes.dataformat;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

import java.util.HashMap;
import java.util.Map;

public class SiriDataFormatHelper {

    private static final HashMap<String, DataFormat> dataformats = new HashMap<>();

    static {
        dataformats.put("", createDataformat(""));
    }

    public static DataFormat getSiriJaxbDataformat() {
    	return createDataformat("");
    }

    public static DataFormat getSiriJaxbDataformat(NamespacePrefixMapper namespacePrefixMapper) {

        if (namespacePrefixMapper != null) {
            String preferredPrefix = namespacePrefixMapper.getPreferredPrefix("", "", true);
            if (preferredPrefix != null) {
                return createDataformat(preferredPrefix);
            }
        }

    	return getSiriJaxbDataformat();
    }

    private static DataFormat createDataformat(String prefix) {
        if (dataformats.containsKey(prefix)) {
            return dataformats.get(prefix);
        }
        Map<String, String> prefixMap = new HashMap<>();
        prefixMap.put("http://www.siri.org.uk/siri", prefix);
        JaxbDataFormat siriJaxb = new JaxbDataFormat("uk.org.siri.siri20");
        siriJaxb.setNamespacePrefix(prefixMap );

        dataformats.put(prefix, siriJaxb);
        return  siriJaxb;
    }


}

