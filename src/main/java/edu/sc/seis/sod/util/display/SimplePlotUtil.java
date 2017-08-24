package edu.sc.seis.sod.util.display;

import java.awt.Dimension;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import edu.iris.dmc.seedcodec.CodecException;
import edu.sc.seis.seisFile.fdsnws.quakeml.Pick;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.SamplingImpl;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.common.UnitRangeImpl;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.Plottable;
import edu.sc.seis.sod.model.seismogram.PlottableChunk;
import edu.sc.seis.sod.model.util.LinearInterp;

/**
 * SimplePlotUtil.java Created: Thu Jul 8 11:22:02 1999
 * 
 * @author Philip Crotwell, Charlie Groves
 * @version $Id: SimplePlotUtil.java 22054 2011-02-16 16:51:38Z crotwell $
 */
public class SimplePlotUtil {

    /**
     * Creates a plottable with all the data from the seismogram that falls
     * inside of the time range at samplesPerDay. Each pixel in the plottable is
     * of 1/pixelsPerDay days long. Two points are returned for each pixel. The
     * first value is the min value over the time covered in the seismogram, and
     * the second value is the max. The seismogram points in a plottable pixel
     * consist of the first point at or after the start time of the pixel to the
     * last point before the start time of the next pixel.
     */
    public static Plottable makePlottable(LocalSeismogramImpl seis,
                                          int pixelsPerDay)
            throws CodecException {
        MicroSecondTimeRange correctedSeisRange = correctTimeRangeForPixelData(seis,
                                                                               pixelsPerDay);
        int startPoint = getPoint(seis, correctedSeisRange.getBeginTime());
        int endPoint = getPoint(seis, correctedSeisRange.getEndTime());
        IntRange seisPixelRange = getDayPixelRange(seis,
                                                   pixelsPerDay,
                                                   seis.getBeginTime());
        int numPixels = seisPixelRange.getDifference();
        // check to see if numPixels doesn't go over
        MicroSecondDate rangeEnd = correctedSeisRange.getBeginTime()
                .add(new TimeInterval(getPixelPeriod(pixelsPerDay).multiplyBy(numPixels)));
        boolean corrected = false;
        if(rangeEnd.after(correctedSeisRange.getEndTime())) {
            numPixels--;
            corrected = true;
        }
        // end check and correction
        int startPixel = seisPixelRange.getMin();
        int[][] pixels = new int[2][numPixels * 2];
        int pixelPoint = startPixel < 0 ? 0 : startPoint;
        MicroSecondDate pixelEndTime = correctedSeisRange.getBeginTime();
        TimeInterval pixelPeriod = getPixelPeriod(pixelsPerDay);
        if(corrected) {}
        for(int i = 0; i < numPixels; i++) {
            pixelEndTime = pixelEndTime.add(pixelPeriod);
            int pos = 2 * i;
            int nextPos = pos + 1;
            pixels[0][pos] = startPixel + i;
            pixels[0][nextPos] = pixels[0][pos];
            int nextPixelPoint = getPixel(startPoint,
                                          endPoint,
                                          correctedSeisRange.getBeginTime(),
                                          correctedSeisRange.getEndTime(),
                                          pixelEndTime);
            QuantityImpl min = seis.getMinValue(pixelPoint, nextPixelPoint);
            pixels[1][pos] = (int)min.getValue();
            QuantityImpl max = seis.getMaxValue(pixelPoint, nextPixelPoint);
            pixels[1][nextPos] = (int)max.getValue();
            if(corrected && (i < 2 || i >= numPixels - 2)) {
                logger.debug(pixels[0][pos] + ": min " + min.getValue() + " max "
                        + max.getValue());
            }
            pixelPoint = nextPixelPoint;
        }
        return new Plottable(pixels[0], pixels[1]);
    }

    public static Plottable getEmptyPlottable() {
        int[] empty = new int[0];
        return new Plottable(empty, empty);
    }

    public static void debugExtraPixel(MicroSecondTimeRange correctedSeisRange,
                                       MicroSecondDate rangeEnd,
                                       LocalSeismogramImpl seis,
                                       int startPoint,
                                       int endPoint,
                                       int numPixels,
                                       IntRange seisPixelRange,
                                       int startPixel,
                                       TimeInterval pixelPeriod) {
        logger.warn("corrected for freak extra pixel!");
        logger.debug("correctedSeisRange: " + correctedSeisRange);
        logger.debug("end of range would have been " + rangeEnd
                + " without correction");
        logger.debug("seis.num_points: " + seis.num_points);
        logger.debug("startPoint: " + startPoint);
        logger.debug("endPoint: " + endPoint);
        logger.debug("seisPixelRange: " + seisPixelRange);
        logger.debug("numPixels after correction: " + numPixels);
        logger.debug("startPixel: " + startPixel);
        logger.debug("pixelPeriod: " + pixelPeriod);
    }

    public static TimeInterval getPixelPeriod(int pixelsPerDay) {
        double pixelPeriod = 1.0 / (double)pixelsPerDay;
        return new TimeInterval(pixelPeriod, UnitImpl.DAY);
    }

    public static MicroSecondDate getBeginningOfDay(MicroSecondDate date) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTime(date);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new MicroSecondDate(cal.getTime());
    }

    public static MicroSecondTimeRange getDay(MicroSecondDate date) {
        return new MicroSecondTimeRange(getBeginningOfDay(date), ONE_DAY);
    }

    public static MicroSecondDate getPixelBeginTime(MicroSecondTimeRange day,
                                                    int pixel,
                                                    int pixelsPerDay) {
        TimeInterval pixelPeriod = getPixelPeriod(pixelsPerDay);
        return day.getBeginTime()
                .add(new TimeInterval(pixelPeriod.multiplyBy(pixel)));
    }

    /*
     * gets the time range that makes up one pixel of plottable data that either
     * surrounds the given date or is directly after the given date
     */
    public static MicroSecondTimeRange getPixelTimeRange(MicroSecondDate point,
                                                         int pixelsPerDay,
                                                         boolean after) {
        TimeInterval pixelPeriod = getPixelPeriod(pixelsPerDay);
        MicroSecondTimeRange day = getDay(point);
        int pixel = getPixel(pixelsPerDay, day, point);
        if(after) {
            pixel++;
        }
        MicroSecondDate pixelBegin = getPixelBeginTime(day, pixel, pixelsPerDay);
        return new MicroSecondTimeRange(pixelBegin, pixelPeriod);
    }

    /*
     * Gets the pixel range of the seismogram from the point of view of the
     * beginning (midnight) of the day of the begin time of the seismogram. This
     * is to say that if you have an two-hour-long seismogram starting at noon
     * on a day with a resolution of 12 pixels per day, the range returned would
     * be 6 to 7.
     */
    public static IntRange getDayPixelRange(LocalSeismogramImpl seis,
                                            int pixelsPerDay) {
        return getDayPixelRange(seis,
                                pixelsPerDay,
                                getBeginningOfDay(new MicroSecondDate(seis.begin_time)));
    }

    /*
     * Same as above, except day can start at any time. The pixel time
     * boundaries are still dependent upon midnight of the seismogram start
     * time.
     */
    public static IntRange getDayPixelRange(LocalSeismogramImpl seis,
                                            int pixelsPerDay,
                                            ZonedDateTime startOfDay) {
        return getDayPixelRange(seis, pixelsPerDay, new MicroSecondDate(startOfDay));
    }


    /*
     * Same as above, except day can start at any time. The pixel time
     * boundaries are still dependent upon midnight of the seismogram start
     * time.
     */
    public static IntRange getDayPixelRange(LocalSeismogramImpl seis,
                                            int pixelsPerDay,
                                            MicroSecondDate startOfDay) {
        MicroSecondTimeRange seisTR = new MicroSecondTimeRange((LocalSeismogramImpl)seis);
        MicroSecondTimeRange dayTR = new MicroSecondTimeRange(startOfDay,
                                                              ONE_DAY);
        int startPixel = getPixel(pixelsPerDay, dayTR, seisTR.getBeginTime());
        if(getPixelTimeRange(seisTR.getBeginTime(), pixelsPerDay, false).getBeginTime()
                .before(seisTR.getBeginTime())) {
            // we don't want pixels with partial data
            startPixel++;
        }
        int endPixel = getPixel(pixelsPerDay, dayTR, seisTR.getEndTime());
        if(endPixel < startPixel) {
            // yes, this pretty much means the difference of the pixel range
            // will be 0
            endPixel = startPixel;
        }
        return new IntRange(startPixel, endPixel);
    }

    public static boolean canMakeAtLeastOnePixel(LocalSeismogramImpl seis,
                                                 int pixelsPerDay) {
        IntRange pixelRange = getDayPixelRange(seis, pixelsPerDay);
        return pixelRange.getMax() > pixelRange.getMin();
    }

    public static MicroSecondTimeRange correctTimeRangeForPixelData(LocalSeismogramImpl seis,
                                                                    int pixelsPerDay) {
        IntRange pixelRange = getDayPixelRange(seis, pixelsPerDay);
        MicroSecondTimeRange day = getDay(new MicroSecondDate(seis.begin_time));
        MicroSecondDate start = getPixelBeginTime(day,
                                                  pixelRange.getMin(),
                                                  pixelsPerDay);
        MicroSecondDate end = getPixelBeginTime(day,
                                                pixelRange.getMax(),
                                                pixelsPerDay);
        return new MicroSecondTimeRange(start, end);
    }

    public static int[][] compressXvalues(LocalSeismogramImpl seismogram,
                                          MicroSecondTimeRange timeRange,
                                          Dimension size) throws CodecException {
        LocalSeismogramImpl seis = (LocalSeismogramImpl)seismogram;
        int width = size.width;
        int[][] out = new int[2][];
        if(seis.getEndTime().isBefore(timeRange.getBeginTime().toZonedDateTime())
                || seis.getBeginTime().isAfter(timeRange.getEndTime().toZonedDateTime())) {
            out[0] = new int[0];
            out[1] = new int[0];
            logger.info("The end time is before the beginTime in simple seismogram");
            return out;
        }
        MicroSecondDate tMin = timeRange.getBeginTime();
        MicroSecondDate tMax = timeRange.getEndTime();
        int seisStartIndex = getPoint(seis, tMin);
        int seisEndIndex = getPoint(seis, tMax);
        if(seisStartIndex < 0) {
            seisStartIndex = 0;
        }
        if(seisEndIndex >= seis.getNumPoints()) {
            seisEndIndex = seis.getNumPoints() - 1;
        }
        MicroSecondDate tempdate = getValue(seis.getNumPoints(),
                                            new MicroSecondDate(seis.getBeginTime()),
                                            new MicroSecondDate(seis.getEndTime()),
                                            seisStartIndex);
        int pixelStartIndex = getPixel(width, timeRange, tempdate);
        tempdate = getValue(seis.getNumPoints(),
                            new MicroSecondDate(seis.getBeginTime()),
                            new MicroSecondDate(seis.getEndTime()),
                            seisEndIndex);
        int pixelEndIndex = getPixel(width, timeRange, tempdate);
        int pixels = seisEndIndex - seisStartIndex + 1;
        out[0] = new int[2 * pixels];
        out[1] = new int[out[0].length];
        int tempYvalues[] = new int[out[0].length];
        int seisIndex = seisStartIndex;
        int numAdded = 0;
        int xvalue = Math.round((float)(linearInterp(seisStartIndex,
                                                     pixelStartIndex,
                                                     seisEndIndex,
                                                     pixelEndIndex,
                                                     seisIndex)));
        int tempValue = 0;
        seisIndex++;
        int j = 0;
        while(seisIndex <= seisEndIndex) {
            tempValue = Math.round((float)(linearInterp(seisStartIndex,
                                                        pixelStartIndex,
                                                        seisEndIndex,
                                                        pixelEndIndex,
                                                        seisIndex)));
            tempYvalues[j++] = (int)seis.getValueAt(seisIndex).getValue();
            if(tempValue != xvalue) {
                out[0][numAdded] = xvalue;
                out[0][numAdded + 1] = xvalue;
                out[1][numAdded] = getMinValue(tempYvalues, 0, j - 1);
                out[1][numAdded + 1] = getMaxValue(tempYvalues, 0, j - 1);
                j = 0;
                xvalue = tempValue;
                numAdded = numAdded + 2;
            }
            seisIndex++;
        }
        int temp[][] = new int[2][numAdded];
        System.arraycopy(out[0], 0, temp[0], 0, numAdded);
        System.arraycopy(out[1], 0, temp[1], 0, numAdded);
        return temp;
    }

    private static int getMinValue(int[] yValues, int startIndex, int endIndex) {
        int minValue = java.lang.Integer.MAX_VALUE;
        for(int i = startIndex; i <= endIndex; i++) {
            if(yValues[i] < minValue)
                minValue = yValues[i];
        }
        return minValue;
    }

    private static int getMaxValue(int[] yValues, int startIndex, int endIndex) {
        int maxValue = java.lang.Integer.MIN_VALUE;
        for(int i = startIndex; i <= endIndex; i++) {
            if(yValues[i] > maxValue)
                maxValue = yValues[i];
        }
        return maxValue;
    }

    /**
     * solves the equation (yb-ya)/(xb-xa) = (y-ya)/(x-xa) for y given x. Useful
     * for finding the pixel for a value given the dimension of the area and the
     * range of values it is supposed to cover. Note, this does not check for xa ==
     * xb, in which case a divide by zero would occur.
     */
    @Deprecated
    public static final double linearInterp(double xa,
                                            double ya,
                                            double xb,
                                            double yb,
                                            double x) {
        return LinearInterp.linearInterp(xa, ya, xb, yb, x);
    }

    public static final int getPixel(int totalPixels,
                                     MicroSecondTimeRange tr,
                                     MicroSecondDate value) {
        return getPixel(totalPixels, tr.getBeginTime(), tr.getEndTime(), value);
    }

    public static final int getPoint(LocalSeismogramImpl seis,
                                     MicroSecondDate time) {
        return getPixel(seis.getNumPoints(),
                        new MicroSecondDate(seis.getBeginTime()),
                        new MicroSecondDate(seis.getEndTime()),
                        time);
    }

    public static final int getPixel(int totalPixels,
                                     MicroSecondDate begin,
                                     MicroSecondDate end,
                                     MicroSecondDate value) {
        return getPixel(0, totalPixels, begin, end, value);
    }

    public static final int getPixel(int startPixel,
                                     int endPixel,
                                     MicroSecondDate begin,
                                     MicroSecondDate end,
                                     MicroSecondDate value) {
        return (int)linearInterp(begin.getMicroSecondTime(),
                                 startPixel,
                                 end.getMicroSecondTime(),
                                 endPixel,
                                 value.getMicroSecondTime());
    }

    public static final MicroSecondDate getValue(int totalPixels,
                                                 MicroSecondDate begin,
                                                 MicroSecondDate end,
                                                 int pixel) {
        return getValue(0, totalPixels, begin, end, pixel);
    }

    public static final MicroSecondDate getValue(int startPixel,
                                                 int endPixel,
                                                 MicroSecondDate begin,
                                                 MicroSecondDate end,
                                                 int pixel) {
        double value = linearInterp(startPixel,
                                    0,
                                    endPixel,
                                    end.getMicroSecondTime()
                                            - begin.getMicroSecondTime(),
                                    pixel);
        return new MicroSecondDate(begin.getMicroSecondTime() + (long)value);
    }

    public static final int getPixel(int totalPixels,
                                     UnitRangeImpl range,
                                     QuantityImpl value) {
        QuantityImpl converted = value.convertTo(range.getUnit());
        return getPixel(totalPixels, range, converted.getValue());
    }

    public static final int getPixel(int totalPixels,
                                     UnitRangeImpl range,
                                     double value) {
        return (int)linearInterp(range.getMinValue(),
                                 0,
                                 range.getMaxValue(),
                                 totalPixels,
                                 value);
    }

    public static final QuantityImpl getValue(int totalPixels,
                                              UnitRangeImpl range,
                                              int pixel) {
        double value = linearInterp(0,
                                    range.getMinValue(),
                                    totalPixels,
                                    range.getMaxValue(),
                                    pixel);
        return new QuantityImpl(value, range.getUnit());
    }

    public static final MicroSecondDate getTimeForIndex(int index,
                                                        MicroSecondDate beginTime,
                                                        SamplingImpl sampling) {
        TimeInterval width = sampling.getPeriod();
        width = (TimeInterval)width.multiplyBy(index);
        return beginTime.add(width);
    }

    public static final TimeInterval ONE_DAY = new TimeInterval(1, UnitImpl.DAY);

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SimplePlotUtil.class);

    public static List<PlottableChunk> makePlottables(LocalSeismogramImpl[] seis, int pixelsPerDay)
            throws IOException {
        List<PlottableChunk> chunks = new ArrayList<PlottableChunk>();
        for (int i = 0; i < seis.length; i++) {
            LocalSeismogramImpl curSeis = (LocalSeismogramImpl)seis[i];
            if (curSeis.getNumPoints() > 0 && canMakeAtLeastOnePixel(seis[i], pixelsPerDay)) {
                try {
                    Plottable plott = makePlottable(curSeis, pixelsPerDay);
                    if (plott.x_coor.length > 0) {
                        MicroSecondDate plotStartTime = getBeginningOfDay(new MicroSecondDate(curSeis.getBeginTime()));
                        PlottableChunk chunk = new PlottableChunk(plott,
                                                                  getDayPixelRange(seis[i],
                                                                                                  pixelsPerDay,
                                                                                                  plotStartTime)
                                                                          .getMin(),
                                                                  plotStartTime,
                                                                  pixelsPerDay,
                                                                  curSeis.channel_id.getNetworkId(),
                                                                  curSeis.channel_id.getStationCode(),
                                                                  curSeis.channel_id.getLocCode(),
                                                                  curSeis.channel_id.getChannelCode());
                        chunks.add(chunk);
                    }
                } catch(CodecException e) {
                    logger.warn("unable to make plottable for "+curSeis+", skipping.", e);
                }
            }
        }
        return chunks;
    }

    public static List<PlottableChunk> convertToCommonPixelScale(List<PlottableChunk> chunks,
                                                             MicroSecondTimeRange requestRange,
                                                             int pixelsPerDay) {
        int requestPixels = getPixels(pixelsPerDay, requestRange);
        List<PlottableChunk> outChunks = new ArrayList<PlottableChunk>();
        Iterator<PlottableChunk> it = chunks.iterator();
        while (it.hasNext()) {
            PlottableChunk pc = it.next();
            if (pc.getEndTime().before(requestRange.getBeginTime())) {
                // whole chunk before request
                continue;
            }
            MicroSecondDate rowBeginTime = pc.getBeginTime();
            int offsetIntoRequestPixels = getPixel(requestPixels, requestRange, rowBeginTime);
            int numPixels = pc.getNumPixels();
            int firstPixelForRequest = 0;
            if (offsetIntoRequestPixels < 0) {
                // This db row has data starting before the request, start
                // at
                // pertinent point
                firstPixelForRequest = -1 * offsetIntoRequestPixels;
            }
            int lastPixelForRequest = numPixels;
            if (offsetIntoRequestPixels + numPixels > requestPixels) {
                // This row has more data than was requested in it, only get
                // enough to fill the request
                lastPixelForRequest = requestPixels - offsetIntoRequestPixels;
            }
            if (firstPixelForRequest > lastPixelForRequest) {
                throw new NegativeArraySizeException("first pixel > last pixel: f="+firstPixelForRequest+"  l="+lastPixelForRequest);
            }
            int pixelsUsed = lastPixelForRequest - firstPixelForRequest;
            int[] x = new int[pixelsUsed * 2];
            int[] y = new int[pixelsUsed * 2];
            int[] ploty = pc.getYData();
            System.arraycopy(ploty, firstPixelForRequest*2, y, 0, pixelsUsed*2);
            for (int i = 0; i < pixelsUsed * 2; i++) {
                x[i] = firstPixelForRequest + offsetIntoRequestPixels + i / 2;
            }
            Plottable p = new Plottable(x, y);
            PlottableChunk shiftPC = new PlottableChunk(p,
                                                        getPixel(rowBeginTime, pixelsPerDay)
                                                                + firstPixelForRequest,
                                                        PlottableChunk.getJDay(rowBeginTime),
                                                        PlottableChunk.getYear(rowBeginTime),
                                                        pixelsPerDay,
                                                        pc.getNetworkCode(),
                                                        pc.getStationCode(),
                                                        pc.getSiteCode(),
                                                        pc.getChannelCode());
            outChunks.add(shiftPC);
        }
        return outChunks;
    }

    public static int getPixels(int pixelsPerDay, MicroSecondTimeRange tr) {
        TimeInterval inter = tr.getInterval();
        inter = (TimeInterval)inter.convertTo(UnitImpl.DAY);
        double samples = pixelsPerDay * inter.getValue();
        return (int)Math.floor(samples);
    }
    
    // from PlottableChunk
    public static int getPixel(MicroSecondDate time, int pixelsPerDay) {
        MicroSecondDate day = new MicroSecondDate(PlottableChunk.stripToDay(time));
        MicroSecondTimeRange tr = new MicroSecondTimeRange(day, ONE_DAY);
        double pixel = SimplePlotUtil.getPixel(pixelsPerDay, tr, time);
        return (int)Math.floor(pixel);
    }
    

} // SimplePlotUtil
