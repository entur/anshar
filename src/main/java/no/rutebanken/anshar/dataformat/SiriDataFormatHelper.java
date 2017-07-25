package no.rutebanken.anshar.dataformat;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;

import uk.org.siri.siri20.Siri;

public class SiriDataFormatHelper {
	private static DataFormat siriJaxb;
	
	private static Object lock = new Object();

    public static DataFormat getSiriJaxbDataformat() {
    	return siriJaxb;
    }
    
    static {
    	synchronized (lock) {
			try {
				siriJaxb = new JaxbDataFormat(JAXBContext.newInstance(Siri.class));
			} catch (JAXBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    }
    

}
