package no.rutebanken.anshar.dataformat;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

public class SiriDataFormatHelper {
	private static DataFormat siriJaxb;
	
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
			siriJaxb = new JaxbDataFormat(JAXBContext.newInstance("uk.org.siri"));
        }
    }


}
