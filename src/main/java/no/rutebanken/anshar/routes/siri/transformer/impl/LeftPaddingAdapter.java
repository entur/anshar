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

package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.lang3.StringUtils;

public class LeftPaddingAdapter extends ValueAdapter {

    private final int paddingLength;
    private final char paddingChar;


    public LeftPaddingAdapter(Class clazz, int paddingLength, char paddingChar) {
        super(clazz);
        this.paddingLength = paddingLength;
        this.paddingChar = paddingChar;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty() || text.startsWith("NSR:")) {
            return text;
        }
        return StringUtils.leftPad(text, paddingLength, paddingChar);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeftPaddingAdapter)) return false;

        LeftPaddingAdapter that = (LeftPaddingAdapter) o;

        if (paddingLength != that.paddingLength) return false;
        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return paddingChar == that.paddingChar;

    }
}
