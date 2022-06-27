package no.rutebanken.anshar.routes.siri.processor.routedata;

import java.time.ZonedDateTime;
import java.util.Objects;

public class ServiceDate {
    final int year;
    final int month;
    final int day;

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
