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

public class RuterSubstringAdapter extends ValueAdapter {

    private final String replacementChar;
    private final String valueSeparator;
    private int lengthAfterSeparator;

    public String apply(String text) {
        if (text != null) {
            if (text.startsWith("NSR:")) {
                return text;
            }
            if (text.contains(valueSeparator)) {
                lengthAfterSeparator = 2;
                if (text.substring(text.indexOf(valueSeparator)).length() > lengthAfterSeparator) {
                    text = text.replaceFirst(valueSeparator, "");
                } else {
                    text = text.replaceFirst(valueSeparator, replacementChar);
                }
            }
        }
        return text;
    }

    public RuterSubstringAdapter(Class clazz, char valueSeparator, char replacementChar, int lengthAfterSeparator) {
        super(clazz);
        this.valueSeparator = ""+valueSeparator;
        this.replacementChar = ""+replacementChar;
        this.lengthAfterSeparator = lengthAfterSeparator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuterSubstringAdapter)) return false;

        RuterSubstringAdapter that = (RuterSubstringAdapter) o;

        if (lengthAfterSeparator != that.lengthAfterSeparator) return false;
        if (!replacementChar.equals(that.replacementChar)) return false;
        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return valueSeparator.equals(that.valueSeparator);

    }
}
