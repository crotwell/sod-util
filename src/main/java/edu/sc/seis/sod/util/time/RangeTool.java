package edu.sc.seis.sod.util.time;

import java.time.ZonedDateTime;
import java.util.List;

import edu.sc.seis.seisFile.fdsnws.stationxml.BaseNodeType;
import edu.sc.seis.sod.model.common.ISOTime;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.TimeRange;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.common.UnsupportedFormat;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.PlottableChunk;
import edu.sc.seis.sod.model.seismogram.RequestFilter;

/**
 * @author groves Created on Oct 28, 2004
 */
public class RangeTool {

    public static boolean areContiguous(PlottableChunk one, PlottableChunk two) {
        TimeInterval sampleInterval = new TimeInterval(0, UnitImpl.DAY);
        return areContiguous(one.getTimeRange(),
                             two.getTimeRange(),
                             sampleInterval);
    }

    public static boolean areContiguous(LocalSeismogramImpl one,
                                        LocalSeismogramImpl two) {
        LocalSeismogramImpl first;
        LocalSeismogramImpl second;
        String oneS = "one ";
        String twoS = "two ";
        try {
            oneS += BaseNodeType.toISOString(one.begin_time);
            twoS += BaseNodeType.toISOString(two.begin_time);
            ZonedDateTime oneB = one.getBeginTime();
            ZonedDateTime twoB = two.getBeginTime();
            
        } catch(UnsupportedFormat ee) {
            throw new RuntimeException(oneS+" "+twoS, ee);
        }
        if (one.getBeginTime().isBefore(two.getBeginTime())) {
            first = one;
            second = two;
        } else {
            first = two;
            second = one;
        }
        MicroSecondTimeRange firstRange = new MicroSecondTimeRange(first);
        // make one end time 1/2 sample later, so areContiguous will check that first
        // sample of second is within 1/2 sample period of time of next data point
        return areContiguous(new MicroSecondTimeRange(firstRange.getBeginTime(), 
                                                      firstRange.getEndTime().add((TimeInterval)one.getSampling().getPeriod().multiplyBy(0.5))),
                             new MicroSecondTimeRange(second),
                             (TimeInterval)first.getSampling().getPeriod());
    }

    public static boolean areContiguous(RequestFilter one, RequestFilter two) {
        return areContiguous(new MicroSecondTimeRange(one),
                             new MicroSecondTimeRange(two));
    }

    public static boolean areContiguous(MicroSecondTimeRange one,
                                        MicroSecondTimeRange two,
                                        TimeInterval interval) {
        if(!RangeTool.areOverlapping(one, two)) {
            TimeInterval littleMoreThanInterval = (TimeInterval)interval.add(new TimeInterval(1, UnitImpl.MICROSECOND));
            if(one.getEndTime().before(two.getBeginTime())) {
                return one.getEndTime()
                        .add(littleMoreThanInterval)
                        .after(two.getBeginTime());
            }
            return two.getEndTime().before(one.getBeginTime()) &&
            two.getEndTime().add(littleMoreThanInterval).after(one.getBeginTime());
        }
        return false;
    }

    public static boolean areContiguous(MicroSecondTimeRange one,
                                        MicroSecondTimeRange two) {
        return one.getEndTime().equals(two.getBeginTime())
                || one.getBeginTime().equals(two.getEndTime());
    }

    public static boolean areOverlapping(PlottableChunk one, PlottableChunk two) {
        return areOverlapping(one.getTimeRange(), two.getTimeRange());
    }

    public static boolean areOverlapping(MicroSecondTimeRange one,
                                         MicroSecondTimeRange two) {
        if(one.getBeginTime().before(two.getEndTime())
                && one.getEndTime().after(two.getBeginTime())) {
            return true;
        }
        return false;
    }

    public static boolean areOverlapping(LocalSeismogramImpl one,
                                         LocalSeismogramImpl two) {
        TimeRange oneTr = new TimeRange(one.getBeginTime(),
                                                              one.getEndTime());
        TimeRange twoTr = new TimeRange(two.getBeginTime(),
                                                              two.getEndTime());
        return areOverlapping(oneTr, twoTr);
    }

    /**
     * @returns A time range encompassing the earliest begin time of the passed
     *          in seismograms to the latest end time
     */
    public static MicroSecondTimeRange getFullTime(LocalSeismogramImpl[] seis) {
        if(seis.length == 0) {
            return ZERO_TIME;
        }
        ZonedDateTime beginTime = SortTool.byBeginTimeAscending(seis)[0].getBeginTime();
        ZonedDateTime endTime = ISOTime.wayPast;
        for(int i = 0; i < seis.length; i++) {
            if(seis[i].getEndTime().isAfter(endTime)) {
                endTime = seis[i].getEndTime();
            }
        }
        return new TimeRange(beginTime, endTime);
    }


    /**
     * @returns A time range encompassing the earliest begin time of the passed
     *          in request filter to the latest end time
     */
    public static MicroSecondTimeRange getFullTime(RequestFilter[] seis) {
        if(seis.length == 0) {
            return ZERO_TIME;
        }
        MicroSecondDate beginTime = new MicroSecondDate(SortTool.byBeginTimeAscending(seis)[0].start_time);
        MicroSecondDate endTime = new MicroSecondDate(0);
        for(int i = 0; i < seis.length; i++) {
            if(new MicroSecondDate(seis[i].end_time).after(endTime)) {
                endTime = new MicroSecondDate(seis[i].end_time);
            }
        }
        return new MicroSecondTimeRange(beginTime, endTime);
    }
    
    
    public static MicroSecondTimeRange getFullTime(List<PlottableChunk> pc) {
        if(pc.size() == 0) {
            return ZERO_TIME;
        }
        MicroSecondDate beginTime = SortTool.byBeginTimeAscending(pc).get(0).getBeginTime();
        MicroSecondDate endTime = new MicroSecondDate(0);
        for (PlottableChunk plottableChunk : pc) {
            if(plottableChunk.getEndTime().after(endTime)) {
                endTime = plottableChunk.getEndTime();
            }
        }
        return new MicroSecondTimeRange(beginTime, endTime);
    }
    
    public static final MicroSecondTimeRange ZERO_TIME = new MicroSecondTimeRange(new MicroSecondDate(0),
                                                                                  new MicroSecondDate(0));

    public static final MicroSecondTimeRange ONE_TIME = new MicroSecondTimeRange(new MicroSecondDate(0),
                                                                                 new MicroSecondDate(1));
}