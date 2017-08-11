<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:soapenv2="http://www.w3.org/2003/05/soap-envelope"
    xmlns:siri="http://www.siri.org.uk/siri"
    xmlns:siril="http://www.siri.org.uk/siri"
    exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>
    
    <xsl:param name="operatorNamespace"/>

    <!-- If not SOAP-envelope - copy all as-is-->     
    <xsl:template match="/siri:Siri">
        <xsl:element name="siri:Siri">
            <xsl:copy-of select="*" />
        </xsl:element>
    </xsl:template>        
    
    <xsl:template match="/soapenv:Envelope">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="soapenv:Body">
        <xsl:apply-templates/>
    </xsl:template>
    
    <xsl:template match="soapenv:Header"/>

    <xsl:template match="/soapenv2:Envelope">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv2:Body">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="soapenv2:Header"/>

    <xsl:template match="*"/>

    <xsl:template match="*:NotifyVehicleMonitoring | *:NotifySituationExchange | *:NotifyEstimatedTimetable | *:NotifyHeartbeat | *:GetVehicleMonitoringResponse | *:GetSituationExchangeResponse | *:GetStopMonitoringResponse | *:GetEstimatedTimetableResponse | *:SubscribeResponse | *:DeleteSubscriptionResponse | *:HeartbeatNotification | *:SituationExchangeAnswer | *:VehicleMonitoringAnswer"> <!-- TODO add all conseptual types of requests -->
        
        <xsl:choose>
            <xsl:when test="local-name()='SubscribeResponse'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:SubscriptionResponse" > 
                        
                        <xsl:for-each select="SubscriptionAnswerInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="SubscriptionAnswerInfo/siril:ResponderRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Answer/siri:ResponseStatus">
                            <xsl:element name="siril:ResponseStatus">
                                <xsl:for-each select="*">
                                    <xsl:element name="siril:{local-name()}">      
                                        <xsl:apply-templates select="* | node()"/>
                                        <xsl:for-each select="*">
                                            <xsl:element name="siril:{local-name()}">      
                                                <xsl:apply-templates select="* | node()"/>
                                            </xsl:element>
                                        </xsl:for-each>
                                    </xsl:element>
                                </xsl:for-each>
                            </xsl:element>
                        </xsl:for-each>
                        
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='SituationExchangeAnswer'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceDelivery" > 
                        
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:SituationExchangeDelivery">
                            <xsl:copy-of select="Answer/SituationExchangeDelivery/*" copy-namespaces="no" />
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='VehicleMonitoringAnswer'">
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:ServiceDelivery" > 
                        
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ProducerRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="ServiceDeliveryInfo/siril:RequestMessageRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:element name="siril:VehicleMonitoringDelivery">
                            <xsl:copy-of select="Answer/VehicleMonitoringDelivery/*" copy-namespaces="no" />
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            <xsl:when test="local-name()='HeartbeatNotification'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="Answer/child::node()/@version"/>
                    </xsl:attribute>
                    <xsl:element name="siril:HeartbeatNotification" > 
                        <xsl:copy-of select="*" copy-namespaces="no" />
                    </xsl:element>
                </xsl:element>
            </xsl:when>
            
            <xsl:when test="local-name()='NotifyHeartbeat'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="'2.0'"/>
                    </xsl:attribute>
                    <xsl:element name="siril:HeartbeatNotification" > 
                        <xsl:copy-of select="HeartbeatNotifyInfo/*" />
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='NotifyEstimatedTimetable' or local-name()='NotifyVehicleMonitoring' or local-name()='NotifySituationExchange'">
                <xsl:element name="siril:Siri">
                    <xsl:attribute name="version">
                        <xsl:value-of select="'2.0'"/>
                    </xsl:attribute>
                    <xsl:element name="siril:ServiceDelivery">
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseTimestamp" copy-namespaces="no"/>
                        <xsl:copy-of select="ServiceDeliveryInfo/siril:ProducerRef" copy-namespaces="no"/>
                        
                        <xsl:copy-of select="Notification/*" />
                    </xsl:element>
                </xsl:element>
            </xsl:when>

            <xsl:when test="local-name()='DeleteSubscriptionResponse'">
                
                <xsl:element name="siril:Siri">
                    <xsl:element name="siril:TerminateSubscriptionResponse" > 
                        
                        <xsl:for-each select="DeleteSubscriptionAnswerInfo/siril:ResponseTimestamp">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="DeleteSubscriptionAnswerInfo/siril:ResponderRef">
                            <xsl:element name="siril:{local-name()}">      
                                <xsl:apply-templates select="* | node()"/>
                            </xsl:element>
                        </xsl:for-each>
                        <xsl:for-each select="Answer/siri:TerminationResponseStatus">
                            <xsl:element name="siril:TerminationResponseStatus">
                               <xsl:for-each select="*">
                                   <xsl:element name="siril:{local-name()}">      
                                       <xsl:apply-templates select="* | node()"/>
                                   </xsl:element>
                               </xsl:for-each>
                            </xsl:element>
                        </xsl:for-each>
                         
                    </xsl:element>
                </xsl:element>
            </xsl:when>
               <xsl:otherwise>
                   <xsl:element name="siril:Siri">
                       <xsl:attribute name="version">
                           <xsl:value-of select="Answer/child::node()/@version"/>
                       </xsl:attribute>
                       <xsl:element name="siril:ServiceDelivery">
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseTimestamp" copy-namespaces="no"/>
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:ProducerRef" copy-namespaces="no"/>
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:Address" copy-namespaces="no"/>
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:ConsumerAddress" copy-namespaces="no"/>
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:ResponseMessageIdentifier" copy-namespaces="no"/>
                           <xsl:copy-of select="ServiceDeliveryInfo/siril:RequestMessageRef" copy-namespaces="no"/>
                           <xsl:copy-of select="Answer/*"/>
                       </xsl:element>
                   </xsl:element>
               </xsl:otherwise>
           </xsl:choose>
        

    </xsl:template>
    
    

</xsl:stylesheet>
