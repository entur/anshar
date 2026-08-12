/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.MappingNames;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.InfoLinkStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalizes the URI of all InfoLinks:
 * <ul>
 *     <li>Values without a scheme are prefixed with "https://"</li>
 *     <li>Values that are empty, or still not valid URLs after prefixing, are removed</li>
 * </ul>
 * InfoLinks are removed completely when no valid links remain.
 */
public class CleanupInfoLinksPostProcessor extends ValueAdapter implements PostProcessor {

    private static final Pattern SUPPORTED_SCHEME = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://.*");
    private static final String DEFAULT_SCHEME = "https://";

    private final String datasetId;
    private final Logger logger = LoggerFactory.getLogger(CleanupInfoLinksPostProcessor.class);

    public CleanupInfoLinksPostProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri != null && siri.getServiceDelivery() != null) {

            List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = siri.getServiceDelivery().getSituationExchangeDeliveries();
            if (situationExchangeDeliveries != null) {
                for (SituationExchangeDeliveryStructure situationExchangeDelivery : situationExchangeDeliveries) {
                    SituationExchangeDeliveryStructure.Situations situations = situationExchangeDelivery.getSituations();
                    if (situations != null && situations.getPtSituationElements() != null) {
                        for (PtSituationElement ptSituationElement : situations.getPtSituationElements()) {
                            cleanupInfoLinks(ptSituationElement);
                        }
                    }
                }
            }
        }
    }

    private void cleanupInfoLinks(PtSituationElement situation) {
        PtSituationElement.InfoLinks infoLinks = situation.getInfoLinks();
        if (infoLinks == null) {
            return;
        }

        int removedCounter = 0;
        int appendedSchemeCounter = 0;
        for (Iterator<InfoLinkStructure> it = infoLinks.getInfoLinks().iterator(); it.hasNext(); ) {
            InfoLinkStructure infoLink = it.next();

            String originalUri = infoLink.getUri();
            String normalizedUri = normalizeUri(originalUri);
            if (normalizedUri == null) {
                it.remove();
                removedCounter++;
            } else {
                if (!hasSupportedScheme(originalUri)) {
                    // Anything but a missing scheme would have been removed above
                    appendedSchemeCounter++;
                }
                infoLink.setUri(normalizedUri);
            }
        }

        if (removedCounter > 0) {
            logger.warn("Removed {} infoLink(s) with missing or invalid URI from situation {} for dataset {}",
                    removedCounter, getSituationNumber(situation), datasetId);
            registerDataMapping(MappingNames.REMOVE_INVALID_INFO_LINK, removedCounter);
        }

        if (appendedSchemeCounter > 0) {
            registerDataMapping(MappingNames.APPEND_SCHEME_TO_INFO_LINK, appendedSchemeCounter);
        }

        if (infoLinks.getInfoLinks().isEmpty()) {
            // no infoLinks left
            situation.setInfoLinks(null);
        }
    }

    /**
     * Returns the normalized URI - prefixed with a scheme when necessary - or {@code null} when the
     * value cannot be used as a URL.
     */
    static String normalizeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }

        String candidate = uri.trim();
        if (!hasSupportedScheme(candidate)) {
            if (ANY_SCHEME.matcher(candidate).matches()) {
                // Only http/https are usable as InfoLinks
                return null;
            }
            // Assume the value starts with a hostname when no scheme is provided
            candidate = DEFAULT_SCHEME + (candidate.startsWith("//") ? candidate.substring(2) : candidate);
        }

        try {
            // Verifying that the value is a valid URL - as required by InfoLinkValidator
            URL url = URI.create(candidate).toURL();
            if (url.getHost() == null || url.getHost().isEmpty()) {
                // e.g. a path without hostname - "/some/path" => "https:///some/path"
                return null;
            }
        } catch (IllegalArgumentException | MalformedURLException e) {
            return null;
        }
        return candidate;
    }

    private static boolean hasSupportedScheme(String uri) {
        return uri != null && SUPPORTED_SCHEME.matcher(uri.trim()).matches();
    }

    private void registerDataMapping(MappingNames mappingName, int count) {
        getMetricsService().registerDataMapping(SiriDataType.SITUATION_EXCHANGE, datasetId, mappingName, count);
    }

    private String getSituationNumber(PtSituationElement situation) {
        return situation.getSituationNumber() != null ? situation.getSituationNumber().getValue() : null;
    }
}