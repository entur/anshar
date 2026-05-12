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
package no.rutebanken.anshar.data.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class TimingTracer {

    private final String name;
    private final long startTime;
    private long lastMark;

    private final List<TimingPoint> timingPoints = new ArrayList<>();
    public TimingTracer(String name) {
        this.name = name;
        this.lastMark = System.currentTimeMillis();
        this.startTime = lastMark;
    }
    public void mark(String label) {
        long elapsed = System.currentTimeMillis()-lastMark;
        timingPoints.add(new TimingPoint(label, elapsed));
        lastMark = System.currentTimeMillis();
    }

    public long getTotalTime() {
        return lastMark - startTime;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TimingTracer.class.getSimpleName() + "[", "]")
                .add("name=" + name)
                .add("totalTime=" + getTotalTime())
                .add("timingPoints=" + timingPoints)
                .toString();
    }

    private record TimingPoint(String label, long elapsed) {

        @Override
            public String toString() {
                return new StringJoiner(", ", TimingPoint.class.getSimpleName() + "[", "]")
                        .add("label='" + label + "'")
                        .add("elapsed=" + elapsed)
                        .toString();
            }
        }
}

