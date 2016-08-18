package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.Siri20ToSiri20RSSubscription;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriRS14Subscription;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriWS14RequestResponse;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriWS14Subscription;
import org.apache.camel.builder.RouteBuilder;
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

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.subscription.initial.duration.hours}")
    private Integer initialDuration = 1;

    @Value("${anshar.enabled.subscriptionRequests}")
    private boolean enableSubscriptionRequests;

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
        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/SXWS/SXService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/SXWS/SXService.svc");

        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "kolumbussx",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableKolumbusSX
        );

        return new Siri20ToSiriWS14Subscription(sub);
    }


    @Bean
    RouteBuilder createKolumbusSiriVMSubscriptionRoute() throws DatatypeConfigurationException {

        Map<String, String> urlMap = new HashMap<String, String>();

        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/VMWS/VMService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/VMWS/VMService.svc");
        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "sis.kolumbus.no:90/VMWS/VMService.svc");

        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "kolumbusvm",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableKolumbusVM
        );

        if (enableSubscriptionRequests) {
            return new Siri20ToSiriWS14Subscription(sub);
        } else {
            return new Siri20ToSiriWS14RequestResponse(sub);
        }
    }

    @Bean
    RouteBuilder createAtBSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "st.atb.no/SXWS/SXService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "st.atb.no/SXWS/SXService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "atbsx",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAtbSX);

        return new Siri20ToSiriWS14Subscription(sub);
    }

    @Bean
    RouteBuilder createAtBSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "st.atb.no/VMWS/VMService.svc?wsdl");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "st.atb.no/VMWS/VMService.svc?wsdl");
        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "st.atb.no/VMWS/VMService.svc?wsdl");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "atbvm",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAtbVM);

        if (enableSubscriptionRequests) {
            return new Siri20ToSiriWS14Subscription(sub);
        } else {
            return new Siri20ToSiriWS14RequestResponse(sub);
        }
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
                "atbsx",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                Duration.ofMinutes(5),
                enableAtbSX
        );

        return new Siri20ToSiriWS14RequestResponse(sub);
    }
*/

    @Bean
    RouteBuilder createAktSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "83.145.60.18/siri/1.4/sx");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "83.145.60.18/siri/1.4/sx");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "aktsx",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAktSX);

        return new Siri20ToSiriRS14Subscription(sub);
    }
    @Bean
    RouteBuilder createAktSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "83.145.60.18/siri/1.4/et");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "83.145.60.18/siri/1.4/et");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                inboundUrl,
                Duration.ofMinutes(1),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "aktet",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAktET);

        return new Siri20ToSiriRS14Subscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "sirisx.ruter.no/sx/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sirisx.ruter.no/sx/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "sirisx.ruter.no/sx/checkstatus.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                inboundUrl,
                Duration.ofDays(7),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "rutersx",
                SubscriptionSetup.ServiceType.REST,
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofDays(7),
                //Duration.ofHours(initialDuration),
                enableRuterSX);

        return new Siri20ToSiri20RSSubscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/vm/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/vm/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/vm/checkstatus.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                inboundUrl,
                Duration.ofDays(7),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "rutervm",
                SubscriptionSetup.ServiceType.REST,
                "20c2e3a3-1659-41d3-b1a2-1411978b1d43",  //Hard-coded subscriptionId - since Init does not support dynamic address
                "RutebankenDEV",
                //UUID.randomUUID().toString(),
                Duration.ofDays(7),
                //Duration.ofHours(initialDuration),
                enableRuterVM);

        return new Siri20ToSiri20RSSubscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/et/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/et/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/et/checkstatus.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                inboundUrl,
                Duration.ofDays(7),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "ruteret",
                SubscriptionSetup.ServiceType.REST,
                "1f6a687e-58b8-4e46-a23c-98adadad78ed",  //Hard-coded subscriptionId - since Init does not support dynamic address
                //UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofDays(7),
                //Duration.ofHours(initialDuration),
                enableRuterET);


        return new Siri20ToSiri20RSSubscription(sub);
    }

}
