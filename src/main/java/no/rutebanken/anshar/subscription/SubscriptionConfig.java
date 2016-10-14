package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.Siri20ToSiriRS14Subscription;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriRS20Subscription;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriWS14RequestResponse;
import no.rutebanken.anshar.routes.siri.Siri20ToSiriWS14Subscription;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RightPaddingStopPlaceAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import javax.xml.datatype.DatatypeConfigurationException;
import java.time.Duration;
import java.util.*;

@Configuration
public class SubscriptionConfig {

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.subscription.initial.duration.hours}")
    private Integer initialDuration = 1;

    @Value("${anshar.subscription.heartbeat.interval.seconds}")
    private Integer heartbeatInterval = 60;

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
    private List<ValueAdapter> ruterMappingAdapters;
    private List<ValueAdapter> atb_kolumbus_mappingAdapters;


    public SubscriptionConfig() {

        ruterMappingAdapters = new ArrayList<>();
        ruterMappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        ruterMappingAdapters.add(new RuterSubstringAdapter(StopPointRef.class, ':', '0', 2));
        ruterMappingAdapters.add(new LeftPaddingAdapter(StopPointRef.class, 10, '0'));

        ruterMappingAdapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 8, '0'));
        ruterMappingAdapters.add(new RightPaddingStopPlaceAdapter(StopPlaceRef.class, 8, "01"));


        atb_kolumbus_mappingAdapters = new ArrayList<>();
        //StopPointRef
        atb_kolumbus_mappingAdapters.add(new LeftPaddingAdapter(StopPointRef.class, 8, '0'));
        atb_kolumbus_mappingAdapters.add(new RightPaddingStopPlaceAdapter(StopPointRef.class, 8, "01"));

        //OriginRef
        atb_kolumbus_mappingAdapters.add(new LeftPaddingAdapter(JourneyPlaceRefStructure.class, 8, '0'));
        atb_kolumbus_mappingAdapters.add(new RightPaddingStopPlaceAdapter(JourneyPlaceRefStructure.class, 8, "01"));

        //DestinationRef
        atb_kolumbus_mappingAdapters.add(new LeftPaddingAdapter(DestinationRef.class, 8, '0'));
        atb_kolumbus_mappingAdapters.add(new RightPaddingStopPlaceAdapter(DestinationRef.class, 8, "01"));

    }

    @Bean
    RouteBuilder createKolumbusSiriSXSubscriptionRoute() throws DatatypeConfigurationException {

        Map<String, String> urlMap = new HashMap<String, String>();
        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/SXWS/SXService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/SXWS/SXService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "kolumbussx",
                "kol",
                SubscriptionSetup.ServiceType.SOAP,
                atb_kolumbus_mappingAdapters,
                new HashMap<>(), UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableKolumbusSX
        );

        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            return new Siri20ToSiriWS14Subscription(sub);
        } else {
            return new Siri20ToSiriWS14RequestResponse(sub);
        }
    }


    @Bean
    RouteBuilder createKolumbusSiriVMSubscriptionRoute() throws DatatypeConfigurationException {

        Map<String, String> urlMap = new HashMap<String, String>();

        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/VMWS/VMService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/VMWS/VMService.svc");
        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "sis.kolumbus.no:90/VMWS/VMService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.kolumbus.no/siri",
                urlMap,
                "1.4",
                "kolumbusvm",
                "kol",
                SubscriptionSetup.ServiceType.SOAP,
                atb_kolumbus_mappingAdapters,
                new HashMap<>(), UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableKolumbusVM
        );

        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
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
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "atbsx",
                "atb",
                SubscriptionSetup.ServiceType.SOAP,
                atb_kolumbus_mappingAdapters,
                new HashMap<>(), UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAtbSX);


        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            return new Siri20ToSiriWS14Subscription(sub);
        } else {
            return new Siri20ToSiriWS14RequestResponse(sub);
        }
    }

    @Bean
    RouteBuilder createAtBSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "st.atb.no/VMWS/VMService.svc");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "st.atb.no/VMWS/VMService.svc");
        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "st.atb.no/VMWS/VMService.svc");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "atbvm",
                "atb",
                SubscriptionSetup.ServiceType.SOAP,
                atb_kolumbus_mappingAdapters,
                new HashMap<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAtbVM);

        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
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

        List<ValueAdapter> mappingAdapters = new ArrayList<>();

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "aktsx",
                "akt",
                SubscriptionSetup.ServiceType.SOAP,
                mappingAdapters,
                new HashMap<>(), UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAktSX);

        //Currently only SUBSCRIBE is supported
        return new Siri20ToSiriRS14Subscription(sub);
    }

    @Bean
    RouteBuilder createAktSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "83.145.60.18/siri/1.4/et");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "83.145.60.18/siri/1.4/et");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.siri.org.uk/siri",
                urlMap,
                "1.4",
                "aktet",
                "akt",
                SubscriptionSetup.ServiceType.SOAP,
                atb_kolumbus_mappingAdapters,
                new HashMap<>(), UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableAktET);

        //Currently only SUBSCRIBE is supported
        return new Siri20ToSiriRS14Subscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriSXSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "sirisx.ruter.no/sx/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sirisx.ruter.no/sx/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "sirisx.ruter.no/sx/checkstatus.xml");

        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofDays(7),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "rutersx",
                "rut",
                SubscriptionSetup.ServiceType.REST,
                ruterMappingAdapters,
                new HashMap<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableRuterSX);

        //Currently only SUBSCRIBE is supported
        return new Siri20ToSiriRS20Subscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriVMSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/vm/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/vm/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/vm/checkstatus.xml");


        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
//                Duration.ofSeconds(heartbeatInterval),
                Duration.ofDays(7),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "rutervm",
                "rut",
                SubscriptionSetup.ServiceType.REST,
                ruterMappingAdapters,
                new HashMap<>(), "20c2e3a3-1659-41d3-b1a2-1411978b1d43",  //Hard-coded subscriptionId - since Init does not support dynamic address
                "RutebankenDEV",
                //UUID.randomUUID().toString(),
                Duration.ofHours(initialDuration),
                enableRuterVM);

        //Currently only SUBSCRIBE is supported
        return new Siri20ToSiriRS20Subscription(sub);
    }

    @Bean
    RouteBuilder createRuterSiriETSubscriptionRoute() {

        Map<String, String> urlMap = new HashMap<>();
        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/et/subscribe.xml");
        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/et/managesubscription.xml");
        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/et/checkstatus.xml");



        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                inboundUrl,
                Duration.ofSeconds(heartbeatInterval),
                "http://www.siri.org.uk/siri",
                urlMap,
                "2.0",
                "ruteret",
                "rut",
                SubscriptionSetup.ServiceType.REST,
                ruterMappingAdapters,
                new HashMap<>(), "1f6a687e-58b8-4e46-a23c-98adadad78ed",  //Hard-coded subscriptionId - since Init does not support dynamic address
                //UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofHours(initialDuration),
                enableRuterET);

        //Currently only SUBSCRIBE is supported
        return new Siri20ToSiriRS20Subscription(sub);
    }

}
