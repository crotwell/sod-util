package edu.sc.seis.sod.util.convert.stationxml;

import java.util.ArrayList;
import java.util.List;

import edu.sc.seis.sod.model.station.StationImpl;



public class StationChannelBundle {

    public StationChannelBundle(StationImpl station, List<ChannelSensitivityBundle> chanList) {
        this.station = station;
        this.chanList = chanList;
    }
    
    public StationChannelBundle(StationImpl station) {
        this( station, new ArrayList<ChannelSensitivityBundle>());
    }
    
    public void setChanList(List<ChannelSensitivityBundle> chanList) {
        this.chanList = chanList;
    }


    public StationImpl getStation() {
        return station;
    }
    
    public List<ChannelSensitivityBundle> getChanList() {
        return chanList;
    }

    StationImpl station;
    List<ChannelSensitivityBundle> chanList;
}
