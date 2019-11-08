<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:siri="http://www.siri.org.uk/siri"
                xmlns:ns2="http://www.ifopt.org.uk/acsb"
                version="1.0">

    <xsl:output method="xml" indent="yes" />

    <xsl:template match="/">
        <root>
            <xsl:call-template name="Siri" />
        </root>
    </xsl:template>

    <xsl:template name="Siri" match="/siri:Siri">

        <xsl:for-each select="/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery/siri:EstimatedJourneyVersionFrame/siri:EstimatedVehicleJourney[not(ns2:ServiceFeatureRef/text()='freightTrain')]">
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" version="2.0">>
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

        <xsl:for-each select="/siri:Siri/siri:DataReadyNotification">
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" version="2.0">>
                <xsl:copy-of select="."></xsl:copy-of>
            </Siri>
        </xsl:for-each>

    </xsl:template>

</xsl:stylesheet>
