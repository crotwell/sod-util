package edu.sc.seis.sod.util.time;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.PlottableChunk;
import edu.sc.seis.sod.model.seismogram.RequestFilter;

/**
 * @author groves Created on Oct 28, 2004
 */
public class SortTool {

    public static LocalSeismogramImpl[] byLengthAscending(LocalSeismogramImpl[] seis) {
        Arrays.sort(seis, new SeisSizeSorter());
        return seis;
    }

    /**
     * @returns the seismograms in order of begin time
     */
    public static LocalSeismogramImpl[] byBeginTimeAscending(LocalSeismogramImpl[] seis) {
        Arrays.sort(seis, new SeisBeginSorter());
        return seis;
    }

    public static List<PlottableChunk> byBeginTimeAscending(List<PlottableChunk> pc) {
        Collections.sort(pc, new PCBeginSorter());
        return pc;
    }

    public static PlottableChunk[] byBeginTimeAscending(PlottableChunk[] pc) {
        Arrays.sort(pc, new PCBeginSorter());
        return pc;
    }

    public static RequestFilter[] byBeginTimeAscending(RequestFilter[] rf) {
        Arrays.sort(rf, new RFBeginSorter());
        return rf;
    }

    public static MicroSecondTimeRange[] byBeginTimeAscending(MicroSecondTimeRange[] ranges) {
        Arrays.sort(ranges, new MSTRBeginSorter());
        return ranges;
    }

    private static class AscendingSizeSorter implements Comparator {

        public int compare(Object o1, Object o2) {
            TimeInterval int1 = getInterval(o1);
            TimeInterval int2 = getInterval(o2);
            if(int1.lessThan(int2)) {
                return -1;
            } else if(int1.greaterThan(int2)) {
                return 1;
            }
            return 0;
        }

        public TimeInterval getInterval(Object o) {
            return (TimeInterval)o;
        }
    }

    public static class SeisSizeSorter extends AscendingSizeSorter {

        public TimeInterval getInterval(Object o) {
            return ((LocalSeismogramImpl)o).getTimeInterval();
        }
    }

    public static class AscendingTimeSorter implements Comparator {

        public int compare(Object o1, Object o2) {
            MicroSecondDate o1Begin = getTime(o1);
            MicroSecondDate o2Begin = getTime(o2);
            if(o1Begin.before(o2Begin)) {
                return -1;
            } else if(o1Begin.after(o2Begin)) {
                return 1;
            }
            return 0;
        }

        public MicroSecondDate getTime(Object o) {
            return (MicroSecondDate)o;
        }
    }

    private static class SeisBeginSorter extends AscendingTimeSorter {

        public MicroSecondDate getTime(Object o) {
            return ((LocalSeismogramImpl)o).getBeginTime();
        }
    }

    private static class PCBeginSorter extends AscendingTimeSorter {

        public MicroSecondDate getTime(Object o) {
            return ((PlottableChunk)o).getBeginTime();
        }
    }

    private static class RFBeginSorter extends AscendingTimeSorter {

        public MicroSecondDate getTime(Object o) {
            return new MicroSecondDate(((RequestFilter)o).start_time);
        }
    }

    private static class MSTRBeginSorter extends AscendingTimeSorter {

        public MicroSecondDate getTime(Object o) {
            return ((MicroSecondTimeRange)o).getBeginTime();
        }
    }
}