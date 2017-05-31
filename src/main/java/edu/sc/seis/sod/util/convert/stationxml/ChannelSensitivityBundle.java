package edu.sc.seis.sod.util.convert.stationxml;

import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.station.ChannelImpl;

public class ChannelSensitivityBundle {
    
    public ChannelSensitivityBundle(ChannelImpl chan, QuantityImpl sensitivity) {
        super();
        this.chan = chan;
        this.sensitivity = sensitivity;
    }

    public ChannelImpl getChan() {
        return chan;
    }
    
    public QuantityImpl getSensitivity() {
        return sensitivity;
    }
    
    private ChannelImpl chan;
    private QuantityImpl sensitivity;
}
