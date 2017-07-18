package edu.sc.seis.sod.util.convert.sac;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;

import edu.sc.seis.seisFile.sac.SacConstants;
import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import edu.sc.seis.sod.model.common.FissuresException;
import edu.sc.seis.sod.model.common.ISOTime;
import edu.sc.seis.sod.model.common.Location;
import edu.sc.seis.sod.model.common.LocationType;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.Orientation;
import edu.sc.seis.sod.model.common.ParameterRef;
import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.SamplingImpl;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.TimeRange;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.event.CacheEvent;
import edu.sc.seis.sod.model.event.EventAttrImpl;
import edu.sc.seis.sod.model.event.Magnitude;
import edu.sc.seis.sod.model.event.OriginImpl;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.SeismogramAttrImpl;
import edu.sc.seis.sod.model.seismogram.TimeSeriesDataSel;
import edu.sc.seis.sod.model.station.ChannelId;
import edu.sc.seis.sod.model.station.ChannelImpl;
import edu.sc.seis.sod.model.station.NetworkAttrImpl;
import edu.sc.seis.sod.model.station.NetworkId;
import edu.sc.seis.sod.model.station.SiteId;
import edu.sc.seis.sod.model.station.SiteImpl;
import edu.sc.seis.sod.model.station.StationId;
import edu.sc.seis.sod.model.station.StationImpl;
import edu.sc.seis.sod.util.time.ClockUtil;

/**
 * SacToFissures.java Created: Thu Mar 2 13:48:26 2000
 * 
 * @author Philip Crotwell
 * @version
 */
public class SacToFissures {

    public SacToFissures() {}
    
    public static LocalSeismogramImpl getSeismogram(File sacFile) throws FileNotFoundException, IOException, FissuresException {
        SacTimeSeries sac = new SacTimeSeries();
        sac.read(sacFile);
        return getSeismogram(sac);
    }
    
    public static LocalSeismogramImpl getSeismogram(InputStream in) throws IOException, FissuresException {
        DataInputStream dis;
        if (in instanceof DataInputStream) {
            dis = (DataInputStream)in;
        } else {
            dis = new DataInputStream(in);
        }
        SacTimeSeries sac = new SacTimeSeries();
        sac.read(dis);
        return getSeismogram(sac);
    }

    /**
     * Gets a LocalSeismogram. The data comes from the sac file, while the
     * SeismogramAttr comes from attr. A check is made on the beginTime,
     * numPoints and sampling and the sac file is considered correct for these
     * three.
     */
    public static LocalSeismogramImpl getSeismogram(SacTimeSeries sac,
                                                    SeismogramAttrImpl attr)
            throws FissuresException {
        LocalSeismogramImpl seis = new LocalSeismogramImpl(attr, sac.getY());
        if(seis.getNumPoints() != sac.getHeader().getNpts()) {
            seis.num_points = sac.getHeader().getNpts();
        }
        SamplingImpl samp = seis.getSampling();
        TimeInterval period = ((SamplingImpl)samp).getPeriod();
        if(sac.getHeader().getDelta() != 0) {
            double error = (period.convertTo(UnitImpl.SECOND).getValue() - sac.getHeader().getDelta())
                    / sac.getHeader().getDelta();
            if(error > 0.01) {
                seis.sampling_info = new SamplingImpl(1,
                                                      new TimeInterval(sac.getHeader().getDelta(),
                                                                       UnitImpl.SECOND));
            } 
        }
        if( ! SacConstants.isUndef(sac.getHeader().getB())) {
            MicroSecondDate beginTime = getSeismogramBeginTime(sac);
            double error = seis.getBeginTime()
                    .subtract(beginTime)
                    .divideBy(period)
                    .getValue();
            if(Math.abs(error) > 0.01) {
                seis.begin_time = beginTime;
            } // end of if (error > 0.01)
        } // end of if (sac.b != -12345)
        return seis;
    }

    public static LocalSeismogramImpl getSeismogram(SacTimeSeries sac)
            throws FissuresException {
        TimeSeriesDataSel data = new TimeSeriesDataSel();
        data.flt_values(sac.getY());
        return new LocalSeismogramImpl(getSeismogramAttr(sac), data);
    }
    
    public static SeismogramAttrImpl getSeismogramAttr(SacTimeSeries sac)
    throws FissuresException {
        MicroSecondDate beginTime = getSeismogramBeginTime(sac);
        ChannelId chanId = getChannelId(sac);
        String evtName = "   ";
        SacHeader header = sac.getHeader();
        if( ! SacConstants.isUndef(header.getKevnm())) {
            evtName += header.getKevnm().trim() + " ";
        }
        if( ! SacConstants.isUndef(header.getEvla()) &&  ! SacConstants.isUndef(header.getEvlo())
                &&  ! SacConstants.isUndef(header.getEvdp())) {
            evtName += "lat: " + header.getEvla() + " lon: " + header.getEvlo() + " depth: "
                    + (header.getEvdp() / 1000) + " km";
        }
        if( ! SacConstants.isUndef(sac.getHeader().getGcarc())) {
            DecimalFormat df = new DecimalFormat("##0.#");
            evtName += "  " + df.format(header.getGcarc()) + " deg.";
        }
        if( ! SacConstants.isUndef(sac.getHeader().getAz())) {
            DecimalFormat df = new DecimalFormat("##0.#");
            evtName += "  az " + df.format(header.getAz()) + " deg.";
        }
        // seis id can be anything, so set to net:sta:site:chan:begin
        String seisId = chanId.network_id.network_code + ":"
                + chanId.station_code + ":" + chanId.site_code + ":"
                + chanId.channel_code + ":" + beginTime.getISOString();
        return new SeismogramAttrImpl(seisId,
                                       beginTime,
                                       sac.getHeader().getNpts(),
                                       new SamplingImpl(1,
                                                        new TimeInterval(sac.getHeader().getDelta(),
                                                                         UnitImpl.SECOND)),
                                       UnitImpl.COUNT,
                                       chanId);
    }

    public static ChannelId getChannelId(SacTimeSeries sac) {
        return getChannelId(sac.getHeader());
    }

    public static ChannelId getChannelId(SacHeader header) {
        if( ! SacConstants.isUndef(header.getKhole())
                && header.getKhole().trim().length() == 2) { return getChannelId(header,
                                                                                 header.getKhole().trim()); }
        return getChannelId(header, "  ");
    }

    public static ChannelId getChannelId(SacTimeSeries sac, String siteCode) {
        return getChannelId(sac.getHeader(), siteCode);
    }

    public static ChannelId getChannelId(SacHeader header, String siteCode) {
        MicroSecondDate nzTime = getNZTime(header);
        String netCode = "XX";
        if( ! SacConstants.isUndef(header.getKnetwk())) {
            netCode = header.getKnetwk().trim().toUpperCase();
        }
        String staCode = "XXXXX";
        if( ! SacConstants.isUndef(header.getKstnm())) {
            staCode = header.getKstnm().trim().toUpperCase();
        }
        String chanCode = "XXX";
        if( ! SacConstants.isUndef(header.getKcmpnm())) {
            chanCode = header.getKcmpnm().trim().toUpperCase();
            if(chanCode.length() == 5) {
                // site code is first 2 chars of kcmpnm
                siteCode = edu.sc.seis.seisFile.fdsnws.stationxml.Channel.fixLocCode(chanCode.substring(0, 2));
                chanCode = chanCode.substring(2, 5);
            }
        }
        NetworkId netId = new NetworkId(netCode, nzTime);
        ChannelId id = new ChannelId(netId,
                                     staCode,
                                     siteCode,
                                     chanCode,
                                     nzTime);
        return id;
    }

    public static ChannelImpl getChannel(SacTimeSeries sac) {
        return getChannel(sac.getHeader());
    }
    
    public static ChannelImpl getChannel(SacHeader header) {
        ChannelId chanId = getChannelId(header);
        float stel = header.getStel();
        if(stel == -12345.0f) {
            stel = 0;
        } // end of if (stel == -12345.0f)
        float stdp = header.getStdp();
        if(stdp == -12345.0f) {
            stdp = 0;
        } // end of if (stdp == -12345.0f)
        Location loc = new Location(header.getStla(),
                                    header.getStlo(),
                                    new QuantityImpl(header.getStel(), UnitImpl.METER),
                                    new QuantityImpl(header.getStdp(), UnitImpl.METER));
        Orientation orient = new Orientation(header.getCmpaz(), header.getCmpinc() - 90);
        SamplingImpl samp = new SamplingImpl(1,
                                             new TimeInterval(header.getDelta(),
                                                              UnitImpl.SECOND));
        TimeRange effective = new TimeRange(chanId.network_id.begin_time,
                                            (MicroSecondDate)null);
        NetworkAttrImpl netAttr = new NetworkAttrImpl(chanId.network_id,
                                                  chanId.network_id.network_code,
                                                  "",
                                                  "",
                                                  effective);
        StationId staId = new StationId(chanId.network_id,
                                        chanId.station_code,
                                        chanId.network_id.begin_time);
        StationImpl station = new StationImpl(staId,
                                          chanId.station_code,
                                          loc,
                                          effective,
                                          "",
                                          "",
                                          "from sac",
                                          netAttr);
        SiteId siteId = new SiteId(chanId.network_id,
                                   chanId.station_code,
                                   chanId.site_code,
                                   chanId.network_id.begin_time);
        SiteImpl site = new SiteImpl(siteId, loc, effective, station, "from sac");
        return new ChannelImpl(chanId,
                               chanId.channel_code,
                               orient,
                               samp,
                               effective,
                               site);
    }

    /**
     * calculates the reference (NZ) time from the sac headers NZYEAR, NZJDAY,
     * NZHOUR, NZMIN, NZSEC, NZMSEC. If any of these are UNDEF (-12345), then ClockUtil.wayPast
     */
    public static MicroSecondDate getNZTime(SacTimeSeries sac) {
        return getNZTime(sac.getHeader());
    }


    /**
     * calculates the reference (NZ) time from the sac headers NZYEAR, NZJDAY,
     * NZHOUR, NZMIN, NZSEC, NZMSEC. If any of these are UNDEF (-12345), then ClockUtil.wayPast
     */
    public static MicroSecondDate getNZTime(SacHeader header) {
        if ( SacConstants.isUndef(header.getNzyear()) ||
                SacConstants.isUndef(header.getNzjday()) ||
                SacConstants.isUndef(header.getNzhour()) ||
                SacConstants.isUndef(header.getNzmin()) ||
                SacConstants.isUndef(header.getNzsec()) ||
                SacConstants.isUndef(header.getNzmsec())) {
            return ClockUtil.wayPast();
        }
        ISOTime isoTime = new ISOTime(header.getNzyear(),
                                      header.getNzjday(),
                                      header.getNzhour(),
                                      header.getNzmin(),
                                      header.getNzsec() + header.getNzmsec() / 1000f);
        MicroSecondDate originTime = isoTime.getDate();
        return originTime;
    }

    /**
     * calculates the event origin time from the sac headers O, NZYEAR, NZJDAY,
     * NZHOUR, NZMIN, NZSEC, NZMSEC.
     */
    public static MicroSecondDate getEventOriginTime(SacTimeSeries sac) {
        return getEventOriginTime(sac.getHeader());
    }

    public static MicroSecondDate getEventOriginTime(SacHeader header) {
        MicroSecondDate originTime = getNZTime(header);
        TimeInterval sacOMarker = new TimeInterval(header.getO(), UnitImpl.SECOND);
        originTime = originTime.add(sacOMarker);
        return originTime;
    }

    /**
     * calculates the seismogram begin time from the sac headers B, NZYEAR,
     * NZJDAY, NZHOUR, NZMIN, NZSEC, NZMSEC.
     */
    public static MicroSecondDate getSeismogramBeginTime(SacTimeSeries sac) {
        return getSeismogramBeginTime(sac.getHeader());
    }

    /**
     * calculates the seismogram begin time from the sac headers B, NZYEAR,
     * NZJDAY, NZHOUR, NZMIN, NZSEC, NZMSEC.
     */
    public static MicroSecondDate getSeismogramBeginTime(SacHeader header ) {
        MicroSecondDate bTime = getNZTime(header);
        TimeInterval sacBMarker = new TimeInterval(header.getB(), UnitImpl.SECOND);
        bTime = bTime.add(sacBMarker);
        return bTime;
    }

    public static CacheEvent getEvent(SacTimeSeries sac) {
        return getEvent(sac.getHeader());
    }

    public static CacheEvent getEvent(SacHeader header) {
        if(! SacConstants.isUndef(header.getO()) && ! SacConstants.isUndef(header.getEvla())
                &&  ! SacConstants.isUndef(header.getEvlo()) &&  ! SacConstants.isUndef(header.getEvdp())) {
            MicroSecondDate beginTime = getEventOriginTime(header);
            EventAttrImpl attr = new EventAttrImpl("SAC Event");
            OriginImpl[] origins = new OriginImpl[1];
            Location loc;
            if(header.getEvdp() > 1000) {
                loc = new Location(header.getEvla(),
                                   header.getEvlo(),
                                   new QuantityImpl(0, UnitImpl.METER),
                                   new QuantityImpl(header.getEvdp(), UnitImpl.METER));
            } else {
                loc = new Location(header.getEvla(),
                                   header.getEvlo(),
                                   new QuantityImpl(0, UnitImpl.METER),
                                   new QuantityImpl(header.getEvdp(),
                                                    UnitImpl.KILOMETER));
            } // end of else
            origins[0] = new OriginImpl("genid:"
                                                + Math.round(Math.random()
                                                        * Integer.MAX_VALUE),
                                        "",
                                        "",
                                        beginTime,
                                        loc,
                                        new Magnitude[0],
                                        new ParameterRef[0]);
            return new CacheEvent(attr, origins, origins[0]);
        } else {
            return null;
        }
    }
} // SacToFissures
