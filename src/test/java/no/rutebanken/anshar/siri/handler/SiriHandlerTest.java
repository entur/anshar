package no.rutebanken.anshar.siri.handler;

import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.JAXBException;

import static junit.framework.TestCase.fail;

public class SiriHandlerTest {
    private SiriHandler handler;

    static String SX_SUBSCRIPTION_ID = "1234";
    static String ET_SUBSCRIPTION_ID = "2345";
    static String VM_SUBSCRIPTION_ID = "3456";
    static String PT_SUBSCRIPTION_ID = "4567";

    @BeforeClass
    public static void setUpClass() {
        SubscriptionManager.addSubscription(SX_SUBSCRIPTION_ID, getSxSubscription());
        SubscriptionManager.activatePendingSubscription(SX_SUBSCRIPTION_ID);
        SubscriptionManager.addSubscription(ET_SUBSCRIPTION_ID, getEtSubscription());
        SubscriptionManager.activatePendingSubscription(ET_SUBSCRIPTION_ID);
        SubscriptionManager.addSubscription(VM_SUBSCRIPTION_ID, getVmSubscription());
        SubscriptionManager.activatePendingSubscription(VM_SUBSCRIPTION_ID);
        SubscriptionManager.addSubscription(PT_SUBSCRIPTION_ID, getPtSubscription());
        SubscriptionManager.activatePendingSubscription(PT_SUBSCRIPTION_ID);

    }

    @Before
    public void setUp() {
        handler = new SiriHandler();
    }

    @Test
    public void testErrorInSXServiceDelivery() throws JAXBException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <SituationExchangeDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </SituationExchangeDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";

        try {
            Siri siri = handler.handleIncomingSiri(SX_SUBSCRIPTION_ID, xml);
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    @Test
    public void testErrorInETServiceDelivery() throws JAXBException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <EstimatedTimetableDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </EstimatedTimetableDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";

        try {
            handler.handleIncomingSiri(ET_SUBSCRIPTION_ID, xml);
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    @Test
    public void testErrorInVMServiceDelivery() throws JAXBException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <VehicleMonitoringDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </VehicleMonitoringDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";
        try {
            handler.handleIncomingSiri(VM_SUBSCRIPTION_ID, xml);
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }


    @Test
    public void testErrorInPTServiceDelivery() throws JAXBException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<siri:Siri xmlns:siri=\"http://www.siri.org.uk/siri\">\n" +
                "  <siril:ServiceDelivery xmlns:siril=\"http://www.siri.org.uk/siri\">\n" +
                "    <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "    <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">ATB</ProducerRef>\n" +
                "    <ResponseMessageIdentifier xmlns=\"http://www.siri.org.uk/siri\">R_</ResponseMessageIdentifier>\n" +
                "    <ProductionTimetableDelivery xmlns=\"http://www.siri.org.uk/siri\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"                                 xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"                                 xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"                                 version=\"2.0\">\n" +
                "      <ResponseTimestamp>2016-11-10T04:27:15.9028457+01:00</ResponseTimestamp>\n" +
                "      <RequestMessageRef>e1995179-cc74-4354-84b2-dbb9850c1b9a</RequestMessageRef>\n" +
                "      <Status>false</Status>\n" +
                "      <ErrorCondition>\n" +
                "        <NoInfoForTopicError/>\n" +
                "        <Description>Unable to connect to the remote server</Description>\n" +
                "      </ErrorCondition>\n" +
                "    </ProductionTimetableDelivery>\n" +
                "  </siril:ServiceDelivery>\n" +
                "</siri:Siri>\n";
        try {
            handler.handleIncomingSiri(PT_SUBSCRIPTION_ID, xml);
        } catch (Throwable t) {
            fail("Handling empty response caused exception");
        }
    }



    private static SubscriptionSetup getSxSubscription() {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setSubscriptionType(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE);
        return setup;
    }

    private static SubscriptionSetup getVmSubscription() {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setSubscriptionType(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING);
        return setup;
    }

    private static SubscriptionSetup getEtSubscription() {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setSubscriptionType(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE);
        return setup;
    }

    private static SubscriptionSetup getPtSubscription() {
        SubscriptionSetup setup = new SubscriptionSetup();
        setup.setSubscriptionType(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE);
        return setup;
    }


}
