<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:siri="http://www.siri.org.uk/siri"
    exclude-result-prefixes="xs" version="2.0">

<!-- This file contains mappings from SIRI 2.0 to 1.4 and then using another transformation to 1.3 which is supported by onebusaway.
        It also contains various "fixes" to the data received from Kolumbus (for now) -->

    <xsl:output indent="yes"/>
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
        
    <xsl:template match="siri:*/@version">
        <xsl:attribute name="version">1.4</xsl:attribute>
    </xsl:template>

<!-- Todo add all possible deliveries -->
    <xsl:template match="siri:SituationExchangeDelivery[count(@version) = 0]">
        <xsl:element name="{local-name()}" namespace="{namespace-uri()}">
            <xsl:attribute name="version">1.4</xsl:attribute>
            <xsl:apply-templates/>
        </xsl:element>
    </xsl:template>

    <!-- Remove illegal element used by Ruter -->
    <xsl:template match="siri:Affects/siri:Networks/siri:AffectedNetwork/siri:AffectedLine/siri:PublishedLineName"/>

    <!-- Add publication window for alerts that does not contain this. Used by SX -->
    <xsl:template match="siri:PtSituationElement/siri:ValidityPeriod[1]">
        <xsl:copy-of select="../siri:ValidityPeriod"/>
        <xsl:copy-of select="siri:Repetitions"/>
        <!-- if PublicationWindow has textual content -->
        <xsl:if test="count(../siri:PublicationWindow/text()) = 1">
            <xsl:element name="siri:PublicationWindow">
                <xsl:element name="siri:StartTime">
                   <xsl:choose>
                       <xsl:when test="../siri:PublicationWindow/text() = '0001-01-01T00:00:00'">
                           <xsl:value-of select="siri:StartTime"/>
                       </xsl:when>
                       <xsl:otherwise>
                           <xsl:value-of select="../siri:PublicationWindow/text()"/>
                       </xsl:otherwise>
                   </xsl:choose>
                </xsl:element>
            </xsl:element>
        </xsl:if>
    </xsl:template>
    
    <!-- If textual content remove (handled above) -->
    <xsl:template match="siri:PublicationWindow[text()]"/>
    
    <xsl:template match="siri:Extensions/*[position() > 1]"/>
    
    <!-- Only want match on first as the order of elements must be kept correct (ValidityPeriod(s), Repetitions and ValidityPeriod(s) -->
    <xsl:template match="
        siri:PtSituationElement/siri:ValidityPeriod[position() > 1]"/>
    
</xsl:stylesheet>
