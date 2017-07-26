package no.rutebanken.anshar.dataformat;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

public class SiriDataFormatHelper {
	private static JaxbDataFormat siriJaxb;
	
    public static DataFormat getSiriJaxbDataformat() {
    	return siriJaxb;
    }
    
    static {
        try {
            init();
        } catch (Exception e) {
            throw new InstantiationError("Unable to instantiate Siri JAXB data format: "+e.getMessage());
        }
    }

    private static void init() throws JAXBException {
        if (siriJaxb == null) {
			Map<String, String> prefixMap = new HashMap<>();
			prefixMap.put("http://www.siri.org.uk/siri", "siri");
        	siriJaxb = new JaxbDataFormat("uk.org.siri.siri20");
			siriJaxb.setNamespacePrefix(prefixMap );
        }
    }


}

