package no.rutebanken.anshar.subscription;

import org.apache.camel.builder.RouteBuilder;
import no.rutebanken.anshar.routes.Siri20ToSiri20RSSubscription;
import no.rutebanken.anshar.routes.Siri20ToSiriRS14Subscription;
import no.rutebanken.anshar.routes.Siri20ToSiriWS14Subscription;
import no.rutebanken.anshar.routes.SiriIncomingReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.xml.datatype.DatatypeConfigurationException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SubscriptionConfig {


    @Value("${anshar.incoming.port}")
    private String inboundPort = "9000";

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.subscription.initial.duration.hours}")
    private Integer initialDuration = 1;

    @Value("${anshar.enabled.ruter.sx}")
    private boolean enableRuterSX;

    @Value("${anshar.enabled.ruter.vm}")
    private boolean enableRuterVM;

    @Value("${anshar.enabled.ruter.et}")
    private boolean enableRuterET;

    @Value("${anshar.enabled.kolumbus.sx}")
    private boolean enableKolumbusSX;

    @Value("${anshar.enabled.kolumbus.vm}")
    private boolean enableKolumbusVM;


    @Value("${anshar.enabled.atb.sx}")
    private boolean enableAtbSX;

    @Value("${anshar.enabled.atb.vm}")
    private boolean enableAtbVM;

    @Value("${anshar.enabled.akt.sx}")
    private boolean enableAktSX;

    @Value("${anshar.enabled.akt.et}")
    private boolean enableAktET;


    @Bean
    RouteBuilder createKolumbusSiriSXSubscriptionRoute() throws DatatypeConfigurationException {

        Map<String, String> urlMap = new HashMap<String, String>();
        urlMap.put("Subscribe", "sis.kolumbus.no:90/SXWS/SXService.svc");
        urlMap.put("DeleteSubscription", "sis.kolumbus.no:90/SXWS/SXService.svc");

        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriWS14Subscription(sub, enableKolumbusSX);
    }


    @Bean
    RouteBuilder createKolumbusSiriVMSubscriptionRoute() throws DatatypeConfigurationException {

        Map<String, String> urlMap = new HashMap<String, String>();

        urlMap.put("Subscribe", "sis.kolumbus.no:90/VMWS/VMService.svc");
        urlMap.put("DeleteSubscription", "sis.kolumbus.no:90/VMWS/VMService.svc");

        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriWS14Subscription(sub, enableKolumbusVM);
    }

    @Bean
    RouteBuilder createAtBSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "st.atb.no/SXWS/SXService.svc");
        urlMap.put("DeleteSubscription", "st.atb.no//SXWS/SXService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriWS14Subscription(sub, enableAtbSX);
    }

    @Bean
    RouteBuilder createAtBSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "st.atb.no/VMWS/VMService.svc?wsdl");
        urlMap.put("DeleteSubscription", "st.atb.no/VMWS/VMService.svc?wsdl");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriWS14Subscription(sub, enableAtbVM);
    }

/*
    @Bean
    RouteBuilder createAtBSiriSXReqRespRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("GetSituationExchange", "st.atb.no/SXWS/SXService.svc");
        urlMap.put("GetVehicleMonitoring", "st.atb.no/SXWS/SXService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofMinutes(5)
        );

        return new Siri20ToSiriWS14RequestResponse(sub, enableAtbSX);
    }
*/

    @Bean
    RouteBuilder createAktSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "83.145.60.18/siri/1.4/sx");
        urlMap.put("DeleteSubscription", "83.145.60.18/siri/1.4/sx");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "Consat",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriRS14Subscription(sub, enableAktSX);
    }
    @Bean
    RouteBuilder createAktSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "83.145.60.18/siri/1.4/et");
        urlMap.put("DeleteSubscription", "83.145.60.18/siri/1.4/et");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "Consat",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiriRS14Subscription(sub, enableAktET);
    }

    @Bean
    RouteBuilder createRuterSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "sirisx.ruter.no/sx/subscribe.xml");
        urlMap.put("DeleteSubscription", "sirisx.ruter.no/sx/managesubscription.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "ruter",
                SubscriptionSetup.ServiceType.REST,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiri20RSSubscription(sub, enableRuterSX);
    }

    @Bean
    RouteBuilder createRuterSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "10.200.12.111:8888/RutebankenTEST/vm/subscribe.xml");
        urlMap.put("DeleteSubscription", "10.200.12.111:8888/RutebankenTEST/vm/managesubscription.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "ruter",
                SubscriptionSetup.ServiceType.REST,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiri20RSSubscription(sub, enableRuterVM);
    }

    @Bean
    RouteBuilder createRuterSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put("Subscribe", "10.200.12.111:8888/RutebankenTEST/et/subscribe.xml");
        urlMap.put("DeleteSubscription", "10.200.12.111:8888/RutebankenTEST/et/managesubscription.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "ruter",
                SubscriptionSetup.ServiceType.REST,
                UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration)
        );

        return new Siri20ToSiri20RSSubscription(sub, enableRuterET);
    }

    @Bean
    RouteBuilder createIncomingListenerRoute() {
        return new SiriIncomingReceiver(inboundPort, incomingPathPattern, incomingLogDirectory);
    }
}
