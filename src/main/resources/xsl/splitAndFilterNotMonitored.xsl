<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:siri="http://www.siri.org.uk/siri"
                version="1.0">

    <xsl:output method="xml" indent="yes" />

    <xsl:template match="/">
        <root>
            <xsl:call-template name="Siri" />
        </root>
    </xsl:template>

    <xsl:template name="Siri" match="/siri:Siri">

        <xsl:for-each select="/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery/siri:EstimatedJourneyVersionFrame/siri:EstimatedVehicleJourney[siri:Monitored/text()='true'] | /siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery/siri:EstimatedJourneyVersionFrame/siri:EstimatedVehicleJourney[siri:Cancellation/text()='true']">
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ResponseTimestamp"></xsl:copy-of>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ProducerRef"></xsl:copy-of>
                    <EstimatedTimetableDelivery>
                        <EstimatedJourneyVersionFrame>
                            <xsl:copy-of select="."></xsl:copy-of>
                        </EstimatedJourneyVersionFrame>
                    </EstimatedTimetableDelivery>
                </ServiceDelivery>
            </Siri>
        </xsl:for-each>

        <xsl:for-each select="/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery/siri:VehicleActivity/siri:MonitoredVehicleJourney[siri:Monitored/text()='true']">
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">>
                <ServiceDelivery>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ResponseTimestamp"></xsl:copy-of>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ProducerRef"></xsl:copy-of>
                    <VehicleMonitoringDelivery>
                        <VehicleActivity>
                            <xsl:copy-of select="."></xsl:copy-of>
                        </VehicleActivity>
                    </VehicleMonitoringDelivery>
                </ServiceDelivery>
            </Siri>
        </xsl:for-each>

        <xsl:for-each select="/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery/siri:Situations">
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ResponseTimestamp"></xsl:copy-of>
                    <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:ProducerRef"></xsl:copy-of>
                    <SituationExchangeDelivery version="2.0">
                        <xsl:copy-of select="/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery/siri:ResponseTimestamp"></xsl:copy-of>
                        <xsl:copy-of select="."></xsl:copy-of>
                    </SituationExchangeDelivery>
                </ServiceDelivery>
            </Siri>
        </xsl:for-each>

        <xsl:for-each select="/siri:Siri/siri:DataReadyNotification">
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <xsl:copy-of select="."></xsl:copy-of>
            </Siri>
        </xsl:for-each>

    </xsl:template>

</xsl:stylesheet>
