<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:siri="http://www.siri.org.uk/siri"
    exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>

    <xsl:param name="operatorNamespace"/>

    <xsl:template match="/siri:Siri">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="siri:ServiceRequest">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="*"/>

    <xsl:template match="siri:VehicleMonitoringRequest | siri:SituationExchangeRequest | siri:EstimatedTimetableRequest | siri:SubscriptionRequest | siri:TerminateSubscriptionRequest"> <!-- TODO add all conseptual types of requests -->
        <xsl:element name="soapenv:Envelope" >
            <xsl:element name="soapenv:Header"/>
            <xsl:element name="soapenv:Body">
            
                <xsl:choose>
                    <xsl:when test="local-name()='SubscriptionRequest'">
                        <xsl:element name="Subscribe" namespace="{$operatorNamespace}"> 
                            <xsl:element name="SubscriptionRequestInfo">
                                <xsl:copy-of select="siri:SubscriptionContext" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:choose>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:SituationExchangeSubscriptionRequest">
                                         <xsl:element name="siri:SituationExchangeSubscriptionRequest">
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:SubscriberRef" copy-namespaces="no"/>
                                             <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>
                                             
                                             <xsl:element name="siri:SituationExchangeRequest">
                                                 <xsl:attribute name="version">
                                                     <!-- <xsl:value-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/@version"/> -->
                                                     <xsl:value-of select="1.4"/>
                                                 </xsl:attribute>
                                                 <xsl:copy-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/*" copy-namespaces="no"/>
                                             </xsl:element>
                                         </xsl:element>
                                    </xsl:when>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:VehicleMonitoringSubscriptionRequest">
                                        <xsl:element name="siri:VehicleMonitoringSubscriptionRequest">
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:SubscriberRef" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>
                                            
                                            <xsl:element name="siri:VehicleMonitoringRequest">
                                                <xsl:attribute name="version">
                                                    <!-- <xsl:value-of select="siri:SituationExchangeSubscriptionRequest/siri:SituationExchangeRequest/@version"/> -->
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of select="siri:VehicleMonitoringSubscriptionRequest/siri:VehicleMonitoringRequest/*" copy-namespaces="no"/>
                                            </xsl:element>
                                        </xsl:element>
                                    </xsl:when>
                                    <xsl:when test="/siri:Siri/siri:SubscriptionRequest/siri:EstimatedTimetableSubscriptionRequest">
                                        <xsl:element name="siri:EstimatedTimetableSubscriptionRequest">
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:SubscriptionIdentifier" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:SubscriberRef" copy-namespaces="no"/>
                                            <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:InitialTerminationTime" copy-namespaces="no"/>
                                            
                                            <xsl:element name="siri:EstimatedTimetableRequest">
                                                <xsl:attribute name="version">
                                                    <xsl:value-of select="1.4"/>
                                                </xsl:attribute>
                                                <xsl:copy-of select="siri:EstimatedTimetableSubscriptionRequest/siri:EstimatedTimetableRequest/*" copy-namespaces="no"/>
                                            </xsl:element>
                                        </xsl:element>
                                    </xsl:when>
                                </xsl:choose>
                            </xsl:element>
                            
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:when test="local-name()='TerminateSubscriptionRequest'">
                        <xsl:element name="siri:DeleteSubscription" namespace="{$operatorNamespace}"> 
                            <xsl:element name="DeleteSubscriptionInfo">
                                <xsl:copy-of select="siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:MessageIdentifier" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:copy-of select="siri:SubscriberRef" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:All" copy-namespaces="no"/>
                                <xsl:copy-of select="siri:SubscriptionRef" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:element name="{concat('Get',substring-before(local-name(),'Request'))}" namespace="{$operatorNamespace}"> 
                            <xsl:element name="ServiceRequestInfo">
                                <xsl:copy-of select="../siri:ServiceRequestContext" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:RequestTimestamp" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:Address" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:RequestorRef" copy-namespaces="no"/>
                                <xsl:copy-of select="../siri:MessageIdentifier" copy-namespaces="no"/>
                            </xsl:element>
                            <xsl:element name="Request">
                                <xsl:attribute name="version">
                                    <xsl:value-of select="/siri:Siri/@version"/>
                                </xsl:attribute>
                                <xsl:copy-of select="*" copy-namespaces="no"/>
                                
                            </xsl:element>
                            <xsl:element name="RequestExtension"/>
                        </xsl:element>
                    </xsl:otherwise>
                </xsl:choose>
                
                
                
            </xsl:element>
        </xsl:element>
        

    </xsl:template>
    
    

</xsl:stylesheet>
