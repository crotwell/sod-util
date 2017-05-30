package edu.sc.seis.sod.util.convert.sac;

import java.util.Calendar;
import java.util.TimeZone;

import edu.iris.dmc.seedcodec.CodecException;
import edu.sc.seis.seisFile.sac.Complex;
import edu.sc.seis.seisFile.sac.SacConstants;
import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacPoleZero;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import edu.sc.seis.sod.model.common.DistAz;
import edu.sc.seis.sod.model.common.FissuresException;
import edu.sc.seis.sod.model.common.ISOTime;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.SamplingImpl;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.event.OriginImpl;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.station.ChannelImpl;
import edu.sc.seis.sod.model.station.Filter;
import edu.sc.seis.sod.model.station.FilterType;
import edu.sc.seis.sod.model.station.Instrumentation;
import edu.sc.seis.sod.model.station.InvalidResponse;
import edu.sc.seis.sod.model.station.PoleZeroFilter;
import edu.sc.seis.sod.model.station.Response;
import edu.sc.seis.sod.model.station.Stage;
import edu.sc.seis.sod.model.station.TransferType;

/**
 * FissuresToSac.java
 * 
 * 
 * Created: Wed Apr 10 10:52:00 2002
 * 
 * @author <a href="mailto:">Philip Crotwell</a>
 * @version
 */

public class FissuresToSac {

	/**
	 * Creates a SacTimeSeries object from a LocalSeismogram. Headers in the SAC
	 * object are filled in as much as possible, with the notable exception of
	 * event information and station location and channel orientation.
	 * 
	 * @param seis
	 *            the <code>LocalSeismogramImpl</code> with the data
	 * @return a <code>SacTimeSeries</code> with data and headers filled
	 */
	public static SacTimeSeries getSAC(LocalSeismogramImpl seis)
			throws CodecException {
		float[] floatSamps;
		try {
			if (seis.can_convert_to_long()) {
				int[] idata = seis.get_as_longs();
				floatSamps = new float[idata.length];
				for (int i = 0; i < idata.length; i++) {
					floatSamps[i] = idata[i];
				}
			} else {
				floatSamps = seis.get_as_floats();
			} // end of else
		} catch (FissuresException e) {
			if (e.getCause() instanceof CodecException) {
				throw (CodecException) e.getCause();
			} else {
				throw new CodecException(e.the_error.error_description);
			}
		}
		SacHeader header = SacHeader.createEmptyEvenSampledTimeSeriesHeader();
		header.setIztype( SacConstants.IB);
		SamplingImpl samp = (SamplingImpl) seis.sampling_info;
		QuantityImpl period = samp.getPeriod();
		period = period.convertTo(UnitImpl.SECOND);
		float f = (float) period.get_value();
		header.setDelta( f);

		UnitImpl yUnit = (UnitImpl) seis.y_unit;
		QuantityImpl min = (QuantityImpl) seis.getMinValue();
		header.setDepmin( (float) min.convertTo(yUnit).getValue());
		QuantityImpl max = (QuantityImpl) seis.getMaxValue();
		header.setDepmax( (float) max.convertTo(yUnit).getValue());
		QuantityImpl mean = (QuantityImpl) seis.getMeanValue();
		header.setDepmen( (float) mean.convertTo(yUnit).getValue());

		setKZTime(header, new MicroSecondDate(seis.begin_time));

		header.setKnetwk(seis.channel_id.network_id.network_code);
		header.setKstnm( seis.channel_id.station_code);
		header.setKcmpnm( seis.channel_id.channel_code);
		header.setKhole( seis.channel_id.site_code);

        return new SacTimeSeries(header, floatSamps);
	}

	/**
	 * Creates a SacTimeSeries object from a LocalSeismogram. Headers in the SAC
	 * object are filled in as much as possible, with the notable exception of
	 * event information.
	 * 
	 * @param seis
	 *            a <code>LocalSeismogramImpl</code> value
	 * @param channel
	 *            a <code>Channel</code> value
	 * @return a <code>SacTimeSeries</code> value
	 */
	public static SacTimeSeries getSAC(LocalSeismogramImpl seis, ChannelImpl channel)
			throws CodecException {
		SacTimeSeries sac = getSAC(seis);
		addChannel(sac.getHeader(), channel);
		return sac;
	}

	/**
	 * Creates a SacTimeSeries object from a LocalSeismogram. Headers in the SAC
	 * object are filled in as much as possible, with the notable exception of
	 * station location and channel orientation information.
	 * 
	 * @param seis
	 *            a <code>LocalSeismogramImpl</code> value
	 * @param origin
	 *            an <code>Origin</code> value
	 * @return a <code>SacTimeSeries</code> value
	 */
	public static SacTimeSeries getSAC(LocalSeismogramImpl seis, OriginImpl origin)
			throws CodecException {
		SacTimeSeries sac = getSAC(seis);
		addOrigin(sac.getHeader(), origin);
		return sac;
	}

	/**
	 * Creates a SacTimeSeries object from a LocalSeismogram. Headers in the SAC
	 * object are filled in as much as possible.
	 * 
	 * @param seis
	 *            a <code>LocalSeismogramImpl</code> value
	 * @param channel
	 *            a <code>Channel</code> value
	 * @param origin
	 *            an <code>Origin</code> value
	 * @return a <code>SacTimeSeries</code> value
	 */
	public static SacTimeSeries getSAC(LocalSeismogramImpl seis,
			ChannelImpl channel, OriginImpl origin) throws CodecException {
		SacTimeSeries sac = getSAC(seis);
		if (channel != null) {
			addChannel(sac.getHeader(), channel);
		}
		if (origin != null) {
			addOrigin(sac.getHeader(), origin);
		}
		if (origin != null && channel != null) {
			DistAz distAz = new DistAz(channel, origin);
			sac.getHeader().setGcarc( (float) distAz.getDelta());
			sac.getHeader().setDist( (float) distAz.getDelta() * 111.19f);
			sac.getHeader().setAz( (float) distAz.getAz());
			sac.getHeader().setBaz( (float) distAz.getBaz());
		}
		return sac;
	}

	/**
	 * Adds the Channel information, including station location and channel
	 * orientation to the sac object.
	 * 
	 * @param sac
	 *            a <code>SacTimeSeries</code> object to be modified
	 * @param channel
	 *            a <code>Channel</code>
	 */
	public static void addChannel(SacHeader header, ChannelImpl channel) {
	    header.setStla( (float) channel.getSite().getLocation().latitude);
	    header.setStlo( (float) channel.getSite().getLocation().longitude);
		QuantityImpl z = (QuantityImpl) channel.getSite().getLocation().elevation;
		header.setStel( (float) z.convertTo(UnitImpl.METER).getValue());
		z = (QuantityImpl) channel.getSite().getLocation().depth;
		header.setStdp( (float) z.convertTo(UnitImpl.METER).getValue());

		header.setCmpaz( channel.getOrientation().azimuth);
		// sac vert. is 0, fissures and seed vert. is -90
		// sac hor. is 90, fissures and seed hor. is 0
		header.setCmpinc( 90 + channel.getOrientation().dip);
	}

	/**
	 * Adds origin informtion to the sac object, including the o marker.
	 * 
	 * @param sac
	 *            a <code>SacTimeSeries</code> object to be modified
	 * @param origin
	 *            an <code>Origin</code> value
	 */
	public static void addOrigin(SacHeader header, OriginImpl origin) {
        header.setEvla( origin.getLocation().latitude);
        header.setEvlo( origin.getLocation().longitude);
		QuantityImpl z = (QuantityImpl) origin.getLocation().elevation;
		header.setEvel( (float) z.convertTo(UnitImpl.METER).getValue());
		z = (QuantityImpl) origin.getLocation().depth;
		header.setEvdp( (float) z.convertTo(UnitImpl.METER).getValue());

		ISOTime isoTime = new ISOTime(header.getNzyear(), header.getNzjday(), header.getNzhour(),
		                              header.getNzmin(), header.getNzsec() + header.getNzmsec() / 1000f);
		MicroSecondDate beginTime = isoTime.getDate();
		MicroSecondDate originTime = new MicroSecondDate(origin.getOriginTime());
		setKZTime(header, originTime);
		TimeInterval sacBMarker = (TimeInterval) beginTime.subtract(originTime);
		sacBMarker = (TimeInterval) sacBMarker.convertTo(UnitImpl.SECOND);
		header.setB( (float) sacBMarker.getValue());
		header.setO( 0);
		header.setIztype( SacConstants.IO);
		if (origin.getMagnitudes().length > 0) {
		    header.setMag( origin.getMagnitudes()[0].value);
		}
	}

    public static void setKZTime(SacHeader header, MicroSecondDate date) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.setTime(date);
		header.setNzyear( cal.get(Calendar.YEAR));
		header.setNzjday( cal.get(Calendar.DAY_OF_YEAR));
		header.setNzhour( cal.get(Calendar.HOUR_OF_DAY));
		header.setNzmin( cal.get(Calendar.MINUTE));
		header.setNzsec( cal.get(Calendar.SECOND));
		header.setNzmsec( cal.get(Calendar.MILLISECOND));
	}

	public static SacPoleZero getPoleZero(Response response)
			throws InvalidResponse {
		Instrumentation.repairResponse(response);
		Instrumentation.checkResponse(response);
		Stage stage = response.stages[0];
		Filter filter = stage.filters[0];
		if (filter.discriminator().value() != FilterType._POLEZERO) {
			throw new IllegalArgumentException("Unexpected response type "
					+ filter.discriminator().value());
		}
		PoleZeroFilter pz = filter.pole_zero_filter();
		int gamma = 0;
		UnitImpl unit = (UnitImpl) stage.input_units;
        QuantityImpl scaleUnit = new QuantityImpl(1, unit);
		if (unit.isConvertableTo(UnitImpl.METER)) {
            gamma = 0;
            scaleUnit = scaleUnit.convertTo(UnitImpl.METER);
        } else if (unit.isConvertableTo(UnitImpl.METER_PER_SECOND)) {
            gamma = 1;
            scaleUnit = scaleUnit.convertTo(UnitImpl.METER_PER_SECOND);
        } else if (unit.isConvertableTo(UnitImpl.METER_PER_SECOND_PER_SECOND)) {
			gamma = 2;
            scaleUnit = scaleUnit.convertTo(UnitImpl.METER_PER_SECOND_PER_SECOND);
		} else {
		    throw new IllegalArgumentException("response unit is not displacement, velocity or acceleration: "+unit);
		}
		int num_zeros = pz.zeros.length + gamma;
		double mulFactor = 1;
		if (stage.type == TransferType.ANALOG) {
			mulFactor = 2 * Math.PI;
		}
		Complex[] zeros = SacPoleZero.initCmplx(num_zeros);
		for (int i = 0; i < pz.zeros.length; i++) {
			zeros[i] = new Complex(pz.zeros[i].real * mulFactor,
					pz.zeros[i].imaginary * mulFactor);
		}
		Complex[] poles = SacPoleZero.initCmplx(pz.poles.length);
		for (int i = 0; i < pz.poles.length; i++) {
			poles[i] = new Complex(pz.poles[i].real * mulFactor,
					pz.poles[i].imaginary * mulFactor);
		}
		float constant = stage.the_normalization[0].ao_normalization_factor;
		double sd = response.the_sensitivity.sensitivity_factor;
		double fs = response.the_sensitivity.frequency;
		sd *= Math.pow(2 * Math.PI * fs, gamma);
		double A0 = stage.the_normalization[0].ao_normalization_factor;
		double fn = stage.the_normalization[0].normalization_freq;
		A0 = A0 / Math.pow(2 * Math.PI * fn, gamma);
		if (stage.type == TransferType.ANALOG) {
			A0 *= Math.pow(2 * Math.PI, pz.poles.length - pz.zeros.length);
		}
		if (poles.length == 0 && zeros.length == 0) {
			constant = (float) (sd * A0);
		} else {
			constant = (float) (sd * calc_A0(poles, zeros, fs));
		}
		constant *= scaleUnit.getValue();
		return new SacPoleZero(poles, zeros, constant);
	}
	
	private static double calc_A0(Complex[] poles, Complex[] zeros, double ref_freq) {
		int i;
		Complex numer = ONE;
		Complex denom = ONE;
		Complex f0;
		double a0;
		f0 = new Complex(0, 2 * Math.PI * ref_freq);
		for (i = 0; i < zeros.length; i++) {
			denom = Complex.mul(denom, Complex.sub(f0, zeros[i]));
		}
		for (i = 0; i < poles.length; i++) {
			numer = Complex.mul(numer, Complex.sub(f0, poles[i]));
		}
		a0 = Complex.div(numer, denom).mag();
		return a0;
	}

	private static Complex ONE = new Complex(1,0);

}// FissuresToSac
