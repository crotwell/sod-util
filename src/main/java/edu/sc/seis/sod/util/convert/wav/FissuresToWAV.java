package edu.sc.seis.sod.util.convert.wav;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.event.EventListenerList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.sc.seis.seisFile.mseed.Utility;
import edu.sc.seis.sod.model.common.FissuresException;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.SamplingImpl;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.util.exceptionHandler.GlobalExceptionHandler;

/**
 * FissuresToWAV.java
 * @see http://ccrma-www.stanford.edu/CCRMA/Courses/422/projects/WaveFormat/
 *
 *
 * Created: Wed Feb 19 15:35:06 2003
 *
 * @author <a href="mailto:crotwell@owl.seis.sc.edu">Philip Crotwell</a>
 * @version 1.0
 */
public class FissuresToWAV {

    private int chunkSize, numChannels, sampleRate, speedUp, bitsPerSample,
        blockAlign, byteRate, subchunk2Size;
    private Clip clip;
    private SeismogramContainer container;
    private EventListenerList listenerList = new EventListenerList();

    public FissuresToWAV(LocalSeismogramImpl seis, int speedUp) {
        this( SeismogramContainerFactory.create(new MemoryDataSetSeismogram(seis)), speedUp);
    }
     
    public FissuresToWAV(SeismogramContainer container, int speedUp) {
        this.container = container;
        this.speedUp = speedUp;
        numChannels = 1;
        bitsPerSample = 16;
        blockAlign = numChannels * (bitsPerSample/8);
    }

    public void writeWAV(DataOutput out, MicroSecondTimeRange tr) throws IOException, FissuresException  {
        updateInfo(container.getIterator(tr));
        writeChunkData(out);
        writeWAVData(out);
    }

    public void play(MicroSecondTimeRange tr){
        Thread playThread = new PlayThread(tr);
        playThread.start();
    }

    private synchronized void playFromThread(MicroSecondTimeRange tr){
        updateInfo();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try{
            writeWAVData(dos, container.getIterator(tr));
        }
        catch(Exception e){
            GlobalExceptionHandler.handle(e);
        }

        if (clip != null) clip.close();
        Clip clip = null;
        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(Clip.class, audioFormat);
        if (!AudioSystem.isLineSupported(info)) {
            logger.debug("Line not supported, apparently...");
        }
        // Obtain and open the line.
        try {
            clip = (Clip) AudioSystem.getLine(info);
            byte[] data = baos.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            AudioInputStream ais = new AudioInputStream(bais, audioFormat, data.length);
            //clip.open(audioFormat, data, 0, 100);
            try{
                clip.open(ais);
                firePlayEvent(calculateTime(tr, speedUp), clip);
                clip.start();
            }
            catch(IOException e){
                e.printStackTrace();
            }
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }

        try{
            baos.close();
            dos.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    private void updateInfo(){
        updateInfo(container.getIterator());
    }

    private void updateInfo(SeismogramIterator iterator){
        chunkSize = 36 + 2*iterator.getNumPoints();
        subchunk2Size = iterator.getNumPoints() * blockAlign;
        sampleRate = calculateSampleRate(container.getIterator().getSampling());
        byteRate = sampleRate * blockAlign;
    }

    public void setSpeedUp(int newSpeed){
        speedUp = newSpeed;
        updateInfo();
    }

    private void writeChunkData(DataOutput out) throws IOException{
        out.writeBytes("RIFF"); //ChunkID

        //ChunkSize
        writeLittleEndian(out, chunkSize);

        out.writeBytes("WAVE"); //Format

        // write fmt subchunk
        out.writeBytes("fmt "); //Subchunk1ID
        writeLittleEndian(out, 16); //Subchunk1Size
        writeLittleEndian(out, (short)1); // Audioformat = linear quantization, PCM
        writeLittleEndian(out, (short)numChannels); // NumChannels
        writeLittleEndian(out, sampleRate); // SampleRate
        writeLittleEndian(out, byteRate); // byte rate
        writeLittleEndian(out, (short)blockAlign); // block align
        writeLittleEndian(out, (short)bitsPerSample); // bits per sample

        // write data subchunk
        out.writeBytes("data");
        writeLittleEndian(out, subchunk2Size); // subchunk2 size
    }

    private void writeWAVData(DataOutput out)throws IOException {
        writeWAVData(out, container.getIterator());
    }

    private void writeWAVData(DataOutput out, SeismogramIterator iterator) throws IOException {

        //calculate maximum amplification factor to avoid either
        //clipping or dead quiet
        double[] minMaxMean = iterator.minMaxMean();
        double absMax = Double.MAX_VALUE;
        if (Math.abs(minMaxMean[0]) > Math.abs(minMaxMean[1])){
            absMax = Math.abs(minMaxMean[0]);
        }
        else{
            absMax = Math.abs(minMaxMean[1]);
        }
        double amplification = (32000.0/absMax);

        while (iterator.hasNext()){
            try{
                QuantityImpl next = (QuantityImpl)iterator.next();
                writeLittleEndian(out, (short)(amplification * next.getValue()));
            }
            catch(NullPointerException e){
                writeLittleEndian(out, (short)0);
            }
            catch(ArrayIndexOutOfBoundsException e){
                writeLittleEndian(out, (short)0);
            }
        }
    }

    public void addPlayEventListener(PlayEventListener pel){
        listenerList.add(PlayEventListener.class, pel);
    }

    private void firePlayEvent(TimeInterval interval, Clip clip){
        PlayEvent playEvent = null;
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==PlayEventListener.class) {
                if (playEvent == null)
                    playEvent = new PlayEvent(this, interval, clip);
                ((PlayEventListener)listeners[i+1]).eventPlayed(playEvent);
            }
        }
    }

    public int calculateSampleRate(SamplingImpl sampling){
        QuantityImpl freq = sampling.getFrequency();
        freq = freq.convertTo(UnitImpl.HERTZ);
        int sampleRate = (int)(freq.getValue() * speedUp);
        while (sampleRate > 48000){
            setSpeedUp(speedUp/2);
            logger.debug("speedUp = " + speedUp);
            sampleRate = (int)(freq.getValue() * speedUp);
            logger.debug("sampleRate = " + sampleRate);
        }
        return sampleRate;
    }

    public static TimeInterval calculateTime(MicroSecondTimeRange tr, int speedUp){
        TimeInterval interval = new TimeInterval(tr.getInterval().divideBy((double)speedUp));
        return interval;
    }

    protected static void writeLittleEndian(DataOutput out, int value)
        throws IOException {
        byte[] tmpBytes;
        tmpBytes = Utility.intToByteArray(value);
        out.write(tmpBytes[3]);
        out.write(tmpBytes[2]);
        out.write(tmpBytes[1]);
        out.write(tmpBytes[0]);
    }

    protected static void writeLittleEndian(DataOutput out, short value)
        throws IOException {
        byte[] tmpBytes;
        tmpBytes = Utility.intToByteArray((int)value);
        out.write(tmpBytes[3]);
        out.write(tmpBytes[2]);
    }

    public class PlayThread extends Thread{
        MicroSecondTimeRange timeRange;

        public PlayThread(MicroSecondTimeRange tr){
            timeRange = tr;
        }

        public void run(){
            playFromThread(timeRange);
        }

    }

    private static Logger logger = LoggerFactory.getLogger(FissuresToWAV.class.getName());

} // FissuresToWAV

