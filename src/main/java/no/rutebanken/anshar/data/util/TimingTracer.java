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
}
class TimingPoint {
    private final String label;
    private final long elapsed;
    TimingPoint(String label, long elapsed) {
        this.label = label;
        this.elapsed = elapsed;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TimingPoint.class.getSimpleName() + "[", "]")
                .add("label='" + label + "'")
                .add("elapsed=" + elapsed)
                .toString();
    }
}
