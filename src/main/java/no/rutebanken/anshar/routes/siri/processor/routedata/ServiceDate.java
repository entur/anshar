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
package no.rutebanken.anshar.routes.siri.processor.routedata;

import java.time.ZonedDateTime;
import java.util.Objects;

public class ServiceDate {
    public final int year;
    public final int month;
    public final int day;

    public ServiceDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public ServiceDate(ZonedDateTime departureTime) {
        this(departureTime.getYear(), departureTime.getMonthValue(), departureTime.getDayOfMonth());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceDate that = (ServiceDate) o;
        return year == that.year &&
                month == that.month &&
                day == that.day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }

    @Override
    public String toString() {
        return "ServiceDate[" + year + "-" + month + "-" + day + "]";
    }
}
