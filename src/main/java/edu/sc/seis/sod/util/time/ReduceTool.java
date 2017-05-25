package edu.sc.seis.sod.util.time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.sc.seis.sod.model.common.FissuresException;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.seismogram.EncodedData;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.Plottable;
import edu.sc.seis.sod.model.seismogram.PlottableChunk;
import edu.sc.seis.sod.model.seismogram.RequestFilter;
import edu.sc.seis.sod.model.seismogram.TimeSeriesDataSel;
import edu.sc.seis.sod.model.station.ChannelIdUtil;
import edu.sc.seis.sod.util.display.SimplePlotUtil;

/**
 * @author groves Created on Oct 29, 2004
 */
public class ReduceTool {

    public static boolean contains(LocalSeismogramImpl container,
                                    LocalSeismogramImpl containee) {
        return equalsOrBefore(container.getBeginTime(),
                              containee.getBeginTime())
                && equalsOrAfter(container.getEndTime(), containee.getEndTime());
    }
    
    /** moved to sod-bag Cut */
    @Deprecated
    public static LocalSeismogramImpl[] cutOverlap(LocalSeismogramImpl[] seis) throws FissuresException
    {
    throw new RuntimeException("Use Cut.cutOverlap");
    }
    
    public static LocalSeismogramImpl[] removeContained(LocalSeismogramImpl[] seis) {
        SortTool.byLengthAscending(seis);
        List results = new ArrayList();
        for(int i = 0; i < seis.length; i++) {
            MicroSecondDate iEnd = seis[i].getEndTime();
            MicroSecondDate iBegin = seis[i].getBeginTime();
            boolean contained = false;
            for(int j = i + 1; j < seis.length && !contained; j++) {
                if(equalsOrAfter(iBegin, seis[j].getBeginTime())
                        && equalsOrBefore(iEnd, seis[j].getEndTime())) {
                    contained = true;
                }
            }
            if(!contained) {
                results.add(seis[i]);
            }
        }
        return (LocalSeismogramImpl[])results.toArray(new LocalSeismogramImpl[0]);
    }

    /**
     * Unites contiguous and equal seismograms into a single
     * LocalSeismogramImpl. Partially overlapping seismograms are left separate.
     */
    public static LocalSeismogramImpl[] merge(LocalSeismogramImpl[] seis) {
        return new LSMerger().merge(seis);
    }

    /**
     * Unites all RequestFilters for the same channel in the given array into a
     * single requestfilter if they're contiguous or overlapping in time.
     */
    public static RequestFilter[] merge(RequestFilter[] ranges) {
        return new RFMerger().merge(ranges);
    }

    public static List<RequestFilter> trimTo(List<RequestFilter> rfList, List<RequestFilter> windowList) {
        List<RequestFilter> out = new ArrayList<RequestFilter>();
        for (RequestFilter window : windowList) {
            MicroSecondDate windowStart = window.start_time;
            MicroSecondDate windowEnd = window.end_time;
            for (RequestFilter rf : rfList) {
                MicroSecondDate rfStart = rf.start_time;
                MicroSecondDate rfEnd = rf.end_time;
                if ((rfStart.after(windowStart) || rfStart.equals(windowStart))
                        && (rfEnd.before(windowEnd) || rfEnd.equals(windowEnd))) {
                    // good, totally contained
                    out.add(rf);
                } else if (rfEnd.before(windowStart) || rfEnd.equals(windowStart)) {
                    // bad, completely before window
                } else if (rfStart.after(windowEnd) || rfStart.equals(windowEnd)) {
                    // bad, completely after window
                } else {
                    // some overlap
                    if (rfStart.before(windowStart)) {
                        rfStart = windowStart;
                    }
                    if (rfEnd.after(windowEnd)) {
                        rfEnd = windowEnd;
                    }
                    out.add(new RequestFilter(rf.channel_id, rfStart, rfEnd));
                }
            }
        }
        return out;
    }

    /**
     * Unites all ranges in the given array into a single range if they're
     * contiguous or overlapping
     */
    public static MicroSecondTimeRange[] merge(MicroSecondTimeRange[] ranges) {
        return new MSTRMerger().merge(ranges);
    }
    
    public static List<MicroSecondTimeRange> mergeMicroSecondTimeRange(List<MicroSecondTimeRange> ranges) {
        return new MSTRMerger().merge(ranges);
    }

    /**
     * Unites all chunks in the given array into a single chunk if they're
     * contiguous or overlapping in time. Ignores the channels and samples per
     * second inside of the chunks, so they must be grouped according to that
     * before being merged
     */
    public static List<PlottableChunk> merge(List<PlottableChunk> chunks) {
        return new PlottableChunkMerger().merge(chunks);
    }
    
    public static RequestFilter cover(RequestFilter[] rf) {
        if (rf == null || rf.length == 0) { return null;}
        RFMerger rfm = new RFMerger();
        RequestFilter out = rf[0];
        for (int i = 1; i < rf.length; i++) {
            out = (RequestFilter)rfm.merge(out, rf[i]);
        }
        return out;
    }

    private static abstract class Merger {

        public abstract Object merge(Object one, Object two);

        public abstract boolean shouldMerge(Object one, Object two);

        public Object[] internalMerge(Object[] chunks,
                                      Object[] resultantTypeArray) {
            chunks = (Object[])chunks.clone();
            for(int i = 0; i < chunks.length; i++) {
                Object chunk = chunks[i];
                for(int j = i + 1; j < chunks.length; j++) {
                    Object chunk2 = chunks[j];
                    if(shouldMerge(chunk, chunk2)) {
                        chunks[j] = merge(chunk, chunk2);
                        chunks[i] = null;
                        break;
                    }
                }
            }
            List results = new ArrayList();
            for(int i = 0; i < chunks.length; i++) {
                if(chunks[i] != null) {
                    results.add(chunks[i]);
                }
            }
            return results.toArray(resultantTypeArray);
        }
    }

    private static class MSTRMerger extends Merger {

        public Object merge(Object one, Object two) {
            return new MicroSecondTimeRange(cast(one), cast(two));
        }

        public boolean shouldMerge(Object one, Object two) {
            MicroSecondTimeRange o = (MicroSecondTimeRange)one;
            MicroSecondTimeRange t = (MicroSecondTimeRange)two;
            if(o.getBeginTime().before(t.getBeginTime())) {
                return !o.getEndTime().before(t.getBeginTime());
            }
            return !t.getEndTime().before(o.getBeginTime());
        }

        public MicroSecondTimeRange cast(Object o) {
            return (MicroSecondTimeRange)o;
        }

        public MicroSecondTimeRange[] merge(MicroSecondTimeRange[] ranges) {
            return (MicroSecondTimeRange[])internalMerge(ranges,
                                                         new MicroSecondTimeRange[0]);
        }
        
        public List<MicroSecondTimeRange> merge(List<MicroSecondTimeRange> chunks) {
            return Arrays.asList((MicroSecondTimeRange[])internalMerge(chunks.toArray(),
                                                   new MicroSecondTimeRange[0]));
        }
    }

    private static class RFMerger extends Merger {

        public Object merge(Object one, Object two) {
            RequestFilter orig = (RequestFilter)one;
            MicroSecondTimeRange tr = new MicroSecondTimeRange(toMSTR(one),
                                                               toMSTR(two));
            return new RequestFilter(orig.channel_id, tr.getBeginTime(), tr.getEndTime());
        }

        protected String getChannelString(Object rf) {
            return ChannelIdUtil.toStringNoDates(((RequestFilter)rf).channel_id);
        }

        public boolean shouldMerge(Object one, Object two) {
            return getChannelString(one).equals(getChannelString(two))
                    && (RangeTool.areOverlapping(toMSTR(one), toMSTR(two)) || RangeTool.areContiguous(toMSTR(one),
                                                                                                      toMSTR(two)));
        }

        protected MicroSecondTimeRange toMSTR(Object o) {
            return new MicroSecondTimeRange((RequestFilter)o);
        }

        public RequestFilter[] merge(RequestFilter[] ranges) {
            return (RequestFilter[])internalMerge(ranges, new RequestFilter[0]);
        }
    }

    public static class LSMerger extends Merger {

        public Object merge(Object one, Object two) {
            return merge((LocalSeismogramImpl)one, (LocalSeismogramImpl)two);
        }

        public LocalSeismogramImpl merge(LocalSeismogramImpl seis,
                                         LocalSeismogramImpl seis2) {
            MicroSecondTimeRange fullRange = new MicroSecondTimeRange(toMSTR(seis),
                                                                      toMSTR(seis2));
            if(fullRange.equals(toMSTR(seis))) {
                return seis;
            }
            LocalSeismogramImpl earlier = seis;
            LocalSeismogramImpl later = seis2;
            if(seis2.getBeginTime().before(seis.getBeginTime())) {
                earlier = seis2;
                later = seis;
            }
            try {
                if(seis.is_encoded() && seis2.is_encoded()) {
                    EncodedData[] earlierED = earlier.get_as_encoded();
                    EncodedData[] laterED = later.get_as_encoded();
                    EncodedData[] outED = new EncodedData[earlierED.length
                            + laterED.length];
                    System.arraycopy(earlierED, 0, outED, 0, earlierED.length);
                    System.arraycopy(laterED,
                                     0,
                                     outED,
                                     earlierED.length,
                                     laterED.length);
                    TimeSeriesDataSel td = new TimeSeriesDataSel();
                    td.encoded_values(outED);
                    LocalSeismogramImpl newSeis = new LocalSeismogramImpl(earlier,
                                                                          td);
                    newSeis.num_points = seis.num_points + seis2.num_points;
                    return newSeis;
                }
                int numPoints = seis.getNumPoints() + seis2.getNumPoints();
                if(seis.can_convert_to_short() && seis2.can_convert_to_short()) {
                    short[] outS = new short[numPoints];
                    System.arraycopy(earlier.get_as_shorts(),
                                     0,
                                     outS,
                                     0,
                                     earlier.getNumPoints());
                    System.arraycopy(later.get_as_shorts(),
                                     0,
                                     outS,
                                     earlier.getNumPoints(),
                                     later.getNumPoints());
                    return new LocalSeismogramImpl(earlier, outS);
                } else if(seis.can_convert_to_long()
                        && seis2.can_convert_to_long()) {
                    int[] outI = new int[numPoints];
                    System.arraycopy(earlier.get_as_longs(),
                                     0,
                                     outI,
                                     0,
                                     earlier.getNumPoints());
                    System.arraycopy(later.get_as_longs(),
                                     0,
                                     outI,
                                     earlier.getNumPoints(),
                                     later.getNumPoints());
                    return new LocalSeismogramImpl(earlier, outI);
                } else if(seis.can_convert_to_float()
                        && seis2.can_convert_to_float()) {
                    float[] outF = new float[numPoints];
                    System.arraycopy(earlier.get_as_floats(),
                                     0,
                                     outF,
                                     0,
                                     earlier.getNumPoints());
                    System.arraycopy(later.get_as_floats(),
                                     0,
                                     outF,
                                     earlier.getNumPoints(),
                                     later.getNumPoints());
                    return new LocalSeismogramImpl(earlier, outF);
                } else {
                    double[] outD = new double[numPoints];
                    System.arraycopy(earlier.get_as_doubles(),
                                     0,
                                     outD,
                                     0,
                                     earlier.getNumPoints());
                    System.arraycopy(later.get_as_doubles(),
                                     0,
                                     outD,
                                     earlier.getNumPoints(),
                                     later.getNumPoints());
                    return new LocalSeismogramImpl(earlier, outD);
                }
            } catch(FissuresException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean shouldMerge(Object one, Object two) {
            return getChannelString(one).equals(getChannelString(two))
                    && (RangeTool.areContiguous((LocalSeismogramImpl)one,
                                                (LocalSeismogramImpl)two) || toMSTR(one).equals(toMSTR(two)));
        }

        protected String getChannelString(Object rf) {
            return ChannelIdUtil.toStringNoDates(((LocalSeismogramImpl)rf).channel_id);
        }

        protected MicroSecondTimeRange toMSTR(Object o) {
            return new MicroSecondTimeRange((LocalSeismogramImpl)o);
        }

        public LocalSeismogramImpl[] merge(LocalSeismogramImpl[] ranges) {
            return (LocalSeismogramImpl[])internalMerge(ranges,
                                                        new LocalSeismogramImpl[0]);
        }
    }

    private static class PlottableChunkMerger extends Merger {

        public Object merge(Object one, Object two) {
            PlottableChunk chunk = cast(one);
            PlottableChunk chunk2 = cast(two);
            MicroSecondTimeRange fullRange = new MicroSecondTimeRange(chunk.getTimeRange(),
                                                                      chunk2.getTimeRange());
            int samples = (int)Math.floor(chunk.getPixelsPerDay() * 2
                    * fullRange.getInterval().convertTo(UnitImpl.DAY).getValue());
            int[] y = new int[samples];
            fill(fullRange, y, chunk);
            fill(fullRange, y, chunk2);
            Plottable mergedData = new Plottable(null, y);
            PlottableChunk earlier = chunk;
            if(chunk2.getBeginTime().before(chunk.getBeginTime())) {
                earlier = chunk2;
            }
            return new PlottableChunk(mergedData,
                                      earlier.getBeginPixel(),
                                      earlier.getJDay(),
                                      earlier.getYear(),
                                      chunk.getPixelsPerDay(),
                                      chunk.getNetworkCode(),
                                      chunk.getStationCode(),
                                      chunk.getSiteCode(),
                                      chunk.getChannelCode());
        }

        public boolean shouldMerge(Object one, Object two) {
            return RangeTool.areContiguous(cast(one), cast(two))
                    || RangeTool.areOverlapping(cast(one), cast(two));
        }

        private PlottableChunk cast(Object o) {
            return (PlottableChunk)o;
        }

        public List<PlottableChunk> merge(List<PlottableChunk> chunks) {
            return Arrays.asList((PlottableChunk[])internalMerge(chunks.toArray(),
                                                   new PlottableChunk[0]));
        }

        public static int[] fill(MicroSecondTimeRange fullRange,
                                 int[] y,
                                 PlottableChunk chunk) {
            MicroSecondDate rowBeginTime = chunk.getBeginTime();
            int offsetIntoRequestSamples = SimplePlotUtil.getPixel(y.length / 2,
                                                                   fullRange,
                                                                   rowBeginTime) * 2;
            int[] dataY = chunk.getData().y_coor;
            int numSamples = dataY.length;
            int firstSampleForRequest = 0;
            if(offsetIntoRequestSamples < 0) {
                firstSampleForRequest = -1 * offsetIntoRequestSamples;
            }
            int lastSampleForRequest = numSamples;
            if(offsetIntoRequestSamples + numSamples > y.length) {
                lastSampleForRequest = y.length - offsetIntoRequestSamples;
            }
            for(int i = firstSampleForRequest; i < lastSampleForRequest; i++) {
                y[i + offsetIntoRequestSamples] = dataY[i];
            }
            return y;
        }
    }

    public static boolean equalsOrAfter(MicroSecondDate first,
                                        MicroSecondDate second) {
        return first.equals(second) || first.after(second);
    }

    public static boolean equalsOrBefore(MicroSecondDate first,
                                         MicroSecondDate second) {
        return first.equals(second) || first.before(second);
    }
}