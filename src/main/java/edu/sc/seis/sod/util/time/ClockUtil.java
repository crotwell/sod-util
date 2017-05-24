package edu.sc.seis.sod.util.time;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import edu.sc.seis.sod.model.common.ISOTime;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.util.exceptionHandler.GlobalExceptionHandler;

/**
 * ClockUtil.java Created: Mon Mar 17 09:34:25 2003
 * 
 * @author Philip Crotwell
 */
public class ClockUtil {

    /**
     * Calculates the difference between the CPU clock and the time retrieved
     * from the http://www.seis.sc.edu/cgi-bin/date_time.pl. 
     */
    public static TimeInterval getTimeOffset() {
        if(serverOffset == null ) {
            if (warnServerFail ) {
                // already tried and failed, so...
                return ZERO_OFFSET;
            }
            try {
                serverOffset = getServerTimeOffset();
            } catch(Throwable e) {
            	noGoClock(e);
                return ZERO_OFFSET;
            } // end of try-catch
        } // end of if ()
        return serverOffset;
    }
    
    private static void noGoClock(Throwable e) {
        warnServerFail = true;
        // oh well, can't get to server, use CPU time, so
        // offset is zero, check for really bad clocks first
        logger.debug("Unable to make a connection to "+SEIS_SC_EDU_URL+" to verify system clock, assuming offset is zero.", e);
        logger.warn("Unable to make a connection to "+SEIS_SC_EDU_URL+" to verify system clock, assuming offset is zero.");
        MicroSecondDate localNow = new MicroSecondDate();
        if(!warnBadBadClock && OLD_DATE.after(localNow)) {
            warnBadBadClock = true;
            GlobalExceptionHandler.handle("Unable to check the time from the server and the computer's clock is obviously wrong. Please reset the clock on your computer to be closer to real time. \nComputer Time="
                                                  + localNow
                                                  + "\nTime checking url="
                                                  + SEIS_SC_EDU_URL,
                                          e);
        }
    }

    /**
     * Creates a new MicroSecondDate that reflects the current time to the best
     * ability of the system. If a connection to a remote server cannot be
     * established, then the current CPU time is used.
     */
    public static MicroSecondDate now() {
        return new MicroSecondDate().add(getTimeOffset());
    }

    public static MicroSecondDate tomorrow() {
        return now().add(ONE_DAY);
    }
    
    public static MicroSecondDate yesterday() {
        return now().subtract(ONE_DAY);
    }
    
    public static MicroSecondDate lastWeek() {
        return now().subtract(ONE_WEEK);
    }
    
    public static MicroSecondDate lastMonth() {
        return now().subtract(ONE_MONTH);
    }
    
    public static MicroSecondDate wayPast() {
        return new MicroSecondDate(0);
    }
    
    public static MicroSecondDate wayFuture() {
        return new MicroSecondDate(ISOTime.future);
    }

    public static TimeInterval getServerTimeOffset() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)SEIS_SC_EDU_URL.openConnection();
        conn.setReadTimeout(10000); // timeout after 10 seconds
        InputStream is = conn.getInputStream();
        InputStreamReader isReader = new InputStreamReader(is);
        BufferedReader bufferedReader = new BufferedReader(isReader);
        String str;
        String timeStr = null;
        while((str = bufferedReader.readLine()) != null) {
            timeStr = str;
        }
        MicroSecondDate localTime = new MicroSecondDate();
        MicroSecondDate serverTime = new ISOTime(timeStr).getDate();
        return new TimeInterval(localTime, serverTime);
    }
    
    private static boolean warnServerFail = false;

    private static boolean warnBadBadClock = false;

    private static TimeInterval serverOffset = null;

    private static final TimeInterval ZERO_OFFSET = new TimeInterval(0,
                                                                     UnitImpl.SECOND);

    private static URL SEIS_SC_EDU_URL;
    static {
        // we have to do this in a static block because of the exception
        try {
            SEIS_SC_EDU_URL = new URL("http://www.seis.sc.edu/cgi-bin/date_time.pl");
        } catch(MalformedURLException e) {
            // Can't happen
            GlobalExceptionHandler.handle("Caught MalformedURL with seis data_time.pl URL. This should never happen.",
                                          e);
        } // end of try-catch
    }

    /** Used to check for really obviously wrong system clocks, set to a day prior to the release date. */
    private static MicroSecondDate OLD_DATE = new ISOTime("2009-02-14T00:00:00.000Z").getDate();

    private static TimeInterval ONE_DAY = new TimeInterval(1, UnitImpl.DAY);
    private static TimeInterval ONE_WEEK = new TimeInterval(7, UnitImpl.DAY);
    private static TimeInterval ONE_MONTH = new TimeInterval(30, UnitImpl.DAY);
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ClockUtil.class);
} // ClockUtil
