package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.*;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class SubscriptionConfig implements CamelContextAware {


    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Autowired
    private Config config;

    protected static CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Bean
    List<RouteBuilder> createSubscriptions() {
        List<RouteBuilder> builders = new ArrayList<>();
        List<SubscriptionSetup> subscriptionSetups = config.getSubscriptions();
        for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
            subscriptionSetup.setAddress(inboundUrl);

            if (subscriptionSetup.getVersion().equals("1.4")) {
                if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE){
                    if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                        builders.add(new Siri20ToSiriWS14Subscription(subscriptionSetup));
                    } else {
                        builders.add(new Siri20ToSiriRS14Subscription(subscriptionSetup));
                    }
                } else {
                    builders.add(new Siri20ToSiriWS14RequestResponse(subscriptionSetup));
                }
            } else {
                if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE){
                    builders.add(new Siri20ToSiriRS20Subscription(subscriptionSetup));
                } else {
                    builders.add(new Siri20ToSiriRS20RequestResponse(subscriptionSetup));
                }
            }
        }

        for (RouteBuilder builder : builders) {
            try {
                camelContext.addRoutes(builder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return builders;
    }

//    @Bean
//    RouteBuilder createKolumbusSiriSXSubscriptionRoute() throws DatatypeConfigurationException {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/SXWS/SXService.svc");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/SXWS/SXService.svc");
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(
//                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                //Duration.ofSeconds(30),
//                Duration.ofHours(30), //Unstable service
//                "http://www.kolumbus.no/siri",
//                urlMap,
//                "1.4",
//                "kolumbussx",
//                "kol",
//                SubscriptionSetup.ServiceType.SOAP,
//                MappingAdapterPresets.adapterPresets.get(Preset.KOLUMBUS),
//                new HashMap<>(),
//                UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//KolumbusSX
//
//        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
//            return new Siri20ToSiriWS14Subscription(sub);
//        } else {
//            return new Siri20ToSiriWS14RequestResponse(sub);
//        }
//    }


//    @Bean
//    RouteBuilder createKolumbusSiriVMSubscriptionRoute() throws DatatypeConfigurationException {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//
//        urlMap.put(RequestType.SUBSCRIBE, "sis.kolumbus.no:90/VMWS/VMService.svc");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sis.kolumbus.no:90/VMWS/VMService.svc");
//        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "sis.kolumbus.no:90/VMWS/VMService.svc");
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(
//                SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
//                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.kolumbus.no/siri",
//                urlMap,
//                "1.4",
//                "kolumbusvm",
//                "kol",
//                SubscriptionSetup.ServiceType.SOAP,
//                MappingAdapterPresets.adapterPresets.get(Preset.KOLUMBUS),
//                new HashMap<>(), UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//KolumbusVM
//
//        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
//            return new Siri20ToSiriWS14Subscription(sub);
//        } else {
//            return new Siri20ToSiriWS14RequestResponse(sub);
//        }
//    }
//
//    @Bean
//    RouteBuilder createAtBSiriSXSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "st.atb.no/SXWS/SXService.svc");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "st.atb.no/SXWS/SXService.svc");
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "1.4",
//                "atbsx",
//                "atb",
//                SubscriptionSetup.ServiceType.SOAP,
//                MappingAdapterPresets.adapterPresets.get(Preset.ATB),
//                new HashMap<>(), UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//AtbSX);
//
//
//        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
//            return new Siri20ToSiriWS14Subscription(sub);
//        } else {
//            return new Siri20ToSiriWS14RequestResponse(sub);
//        }
//    }
//
//    @Bean
//    RouteBuilder createAtBSiriVMSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "st.atb.no/VMWS/VMService.svc");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "st.atb.no/VMWS/VMService.svc");
//        urlMap.put(RequestType.GET_VEHICLE_MONITORING, "st.atb.no/VMWS/VMService.svc");
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
//                SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "1.4",
//                "atbvm",
//                "atb",
//                SubscriptionSetup.ServiceType.SOAP,
//                MappingAdapterPresets.adapterPresets.get(Preset.KOLUMBUS),
//                new HashMap<>(),
//                UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//AtbVM);
//
//        if (sub.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
//            return new Siri20ToSiriWS14Subscription(sub);
//        } else {
//            return new Siri20ToSiriWS14RequestResponse(sub);
//        }
//    }

/*
    @Bean
    RouteBuilder createAtBSiriSXReqRespRoute() {

        Map<RequestType, String> urlMap = new HashMap<>();
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
                false);//AtbSX
        );

        return new Siri20ToSiriWS14RequestResponse(sub);
    }
*/
//
//    @Bean
//    RouteBuilder createAktSiriSXSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "83.145.60.18/siri/1.4/sx");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "83.145.60.18/siri/1.4/sx");
//
//        List<ValueAdapter> mappingAdapters = new ArrayList<>();
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "1.4",
//                "aktsx",
//                "akt",
//                SubscriptionSetup.ServiceType.SOAP,
//                mappingAdapters,
//                new HashMap<>(), UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//AktSX);
//
//        //Currently only SUBSCRIBE is supported
//        return new Siri20ToSiriRS14Subscription(sub);
//    }
//
//    @Bean
//    RouteBuilder createAktSiriETSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "83.145.60.18/siri/1.4/et");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "83.145.60.18/siri/1.4/et");
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "1.4",
//                "aktet",
//                "akt",
//                SubscriptionSetup.ServiceType.SOAP,
//                MappingAdapterPresets.adapterPresets.get(Preset.KOLUMBUS),
//                new HashMap<>(), UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//AktET);
//
//        //Currently only SUBSCRIBE is supported
//        return new Siri20ToSiriRS14Subscription(sub);
//    }
//
//    @Bean
//    RouteBuilder createRuterSiriSXSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "sirisx.ruter.no/sx/subscribe.xml");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "sirisx.ruter.no/sx/managesubscription.xml");
//        urlMap.put(RequestType.CHECK_STATUS, "sirisx.ruter.no/sx/checkstatus.xml");
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                Duration.ofDays(7),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "2.0",
//                "rutersx",
//                "rut",
//                SubscriptionSetup.ServiceType.REST,
//                MappingAdapterPresets.adapterPresets.get(Preset.RUTER),
//                new HashMap<>(),
//                UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//RuterSX);
//
//        //Currently only SUBSCRIBE is supported
//        return new Siri20ToSiriRS20Subscription(sub);
//    }
//
//    @Bean
//    RouteBuilder createRuterSiriVMSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/vm/subscribe.xml");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/vm/managesubscription.xml");
//        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/vm/checkstatus.xml");
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
////                Duration.ofSeconds(30),
//                Duration.ofDays(7),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "2.0",
//                "rutervm",
//                "rut",
//                SubscriptionSetup.ServiceType.REST,
//                MappingAdapterPresets.adapterPresets.get(Preset.RUTER),
//                new HashMap<>(), "20c2e3a3-1659-41d3-b1a2-1411978b1d43",  //Hard-coded subscriptionId - since Init does not support dynamic address
//                "RutebankenDEV",
//                //UUID.randomUUID().toString(),
//                Duration.ofHours(1),
//                false);//RuterVM);
//
//        //Currently only SUBSCRIBE is supported
//        return new Siri20ToSiriRS20Subscription(sub);
//    }
//
//    @Bean
//    RouteBuilder createRuterSiriETSubscriptionRoute() {
//
//        Map<RequestType, String> urlMap = new HashMap<>();
//        urlMap.put(RequestType.SUBSCRIBE, "109.239.226.193:8080/RutebankenDEV/et/subscribe.xml");
//        urlMap.put(RequestType.DELETE_SUBSCRIPTION, "109.239.226.193:8080/RutebankenDEV/et/managesubscription.xml");
//        urlMap.put(RequestType.CHECK_STATUS, "109.239.226.193:8080/RutebankenDEV/et/checkstatus.xml");
//
//
//
//        SubscriptionSetup sub = new SubscriptionSetup(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE,
//                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
//                inboundUrl,
//                Duration.ofSeconds(30),
//                "http://www.siri.org.uk/siri",
//                urlMap,
//                "2.0",
//                "ruteret",
//                "rut",
//                SubscriptionSetup.ServiceType.REST,
//                MappingAdapterPresets.adapterPresets.get(Preset.RUTER),
//                new HashMap<>(), "1f6a687e-58b8-4e46-a23c-98adadad78ed",  //Hard-coded subscriptionId - since Init does not support dynamic address
//                //UUID.randomUUID().toString(),
//                "RutebankenDEV",
//                Duration.ofHours(1),
//                false);//RuterET);
//
//        //Currently only SUBSCRIBE is supported
//        return new Siri20ToSiriRS20Subscription(sub);
//    }

}
