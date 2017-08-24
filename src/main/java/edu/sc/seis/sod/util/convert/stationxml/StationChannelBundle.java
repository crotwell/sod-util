package edu.sc.seis.sod.util.convert.stationxml;

import java.util.ArrayList;
import java.util.List;

import edu.sc.seis.seisFile.fdsnws.stationxml.Station;



public class StationChannelBundle {

    public StationChannelBundle(Station station, List<ChannelSensitivityBundle> chanList) {
        this.station = station;
        this.chanList = chanList;
    }
    
    public StationChannelBundle(Station station) {
        this( station, new ArrayList<ChannelSensitivityBundle>());
    }
    
    public void setChanList(List<ChannelSensitivityBundle> chanList) {
        this.chanList = chanList;
    }


    public Station getStation() {
        return station;
    }
    
    public List<ChannelSensitivityBundle> getChanList() {
        return chanList;
    }

    Station station;
    List<ChannelSensitivityBundle> chanList;
}
