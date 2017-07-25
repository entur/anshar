package no.rutebanken.anshar.dataformat;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

import uk.org.siri.siri20.Siri;

public class SiriDataFormatHelper {
	private static DataFormat siriJaxb;
	
    public static DataFormat getSiriJaxbDataformat() {
    	return siriJaxb;
    }
    
    static {
        try {
            init();
        } catch (Exception e) {
            throw new InstantiationError();
        }
    }

    private static void init() throws JAXBException {
        if (siriJaxb == null) {
			siriJaxb = new JaxbDataFormat(JAXBContext.newInstance(Siri.class));
        }
    }


}
