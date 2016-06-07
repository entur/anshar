<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:siri="http://www.siri.org.uk/siri"
    exclude-result-prefixes="xs" version="2.0">

    <xsl:output indent="yes"/>
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>
        
    <xsl:template match="siri:*/@version">
        <xsl:attribute name="version">1.4</xsl:attribute>
    </xsl:template>    

</xsl:stylesheet>
