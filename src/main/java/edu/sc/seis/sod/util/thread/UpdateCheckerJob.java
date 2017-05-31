package edu.sc.seis.sod.util.thread;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import com.isti.util.IstiVersion;
import com.isti.util.updatechecker.LocationUpdate;
import com.isti.util.updatechecker.UpdateAction;
import com.isti.util.updatechecker.UpdateInformation;
import com.isti.util.updatechecker.XMLUpdateCheckerClient;
import com.isti.util.updatechecker.XMLUpdateCheckerServer;

import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.util.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.util.time.ClockUtil;

public class UpdateCheckerJob implements Runnable {

    private String displayName;

    /**
     * @param forceCheck
     *            overides the users "don't bother me until..." setting in the
     *            Java Preferences. Usually this should be false, but is useful
     *            for testing.
     */
    public UpdateCheckerJob(String displayName,
                            String programName,
                            String version,
                            String updateURL,
                            boolean gui,
                            boolean forceCheck) {
        this.displayName = displayName;
        this.programName = programName;
        this.updateURL = updateURL;
        this.isGui = gui;
        this.forceCheck = forceCheck;
        this.version = version;
        this.prefsName = programName + "_" + NEXT_CHECK_DATE;
    }
    
    public void setUsePreferencesForStorage(boolean usePrefs) {
        this.usePrefs = usePrefs;
    }

    public void run() {
        // only check if have not yet checked, or if forceCheck is true
        if(!forceCheck && checkedYet) {
            return;
        }
        checkedYet = true;
        boolean checkNeeded = true;
        //Turn off pref logging so it doesn't complain on Linux if the pref node isn't there.
        Logger prefsLogger = Logger.getLogger("java.util.prefs");
        prefsLogger.setLevel(Level.OFF);
        MicroSecondDate now = ClockUtil.now();
        MicroSecondDate date = getNextCheck();
        if(date.after(now) && !forceCheck) {
            // don't check
            logger.debug("no updated wanted until " + date);
            return;
        }
        logger.debug("Connect to server");
        Properties httpHeaders = new Properties();
        httpHeaders.put("User-Agent", programName+"-"+version);
        XMLUpdateCheckerClient updateChecker = new XMLUpdateCheckerClient(new IstiVersion(version),
                                                                          new XMLUpdateCheckerServer(updateURL, httpHeaders));
        logger.debug("Check for update");
        if(updateChecker.isUpdateAvailable()) {
            UpdateInformation[] updates = updateChecker.getUpdates();
            logger.info("our version is " + version + ", update version is "
                    + updates[updates.length - 1].getVersion());
            UpdateAction[] actions = updates[updates.length - 1].getUpdateActions();
            LocationUpdate locationUpdate = (LocationUpdate)actions[0];
            try {
                if(isGui) {
                    handleUpdateGUI(locationUpdate);
                } else {
                    handleUpdateNonGUI(locationUpdate);
                }
            } catch(BackingStoreException e) {
                GlobalExceptionHandler.handle("trouble flushing preferences for updatechecker",
                                              e);
            }
        } else if(showNoUpdate) {
            JOptionPane.showMessageDialog(null,
                                          "No update is available",
                                          "Update Check",
                                          JOptionPane.INFORMATION_MESSAGE);
            logger.info("No update is available");
        }
    }

    protected void handleUpdateGUI(LocationUpdate locationUpdate)
            throws BackingStoreException {
        Object[] options = new String[3];
        options[0] = "Go To Update Page";
        options[1] = "Remind in a fortnight";
        options[2] = "Remind in a month";
        int n = JOptionPane.showOptionDialog(null,
                                             "An updated version of "
                                                     + programName
                                                     + " is available!\nPlease go to\n"
                                                     + locationUpdate.getLocation()
                                                     + "\nto get the latest version.",
                                             "An updated version of "
                                                     + programName
                                                     + " is available!",
                                             JOptionPane.YES_NO_OPTION,
                                             JOptionPane.QUESTION_MESSAGE,
                                             null, // don't use a custom Icon
                                             options, // the titles of buttons
                                             options[0]); // default button
                                                            // title
        logger.debug("return val is " + n);
        TimeInterval nextInterval = SIX_HOUR;
        if(n == JOptionPane.YES_OPTION) {
            logger.debug("Opening browser");
            locationUpdate.run();
        } else if(n == 1) {
            nextInterval = FORTNIGHT;
        } else if(n == 2) {
            nextInterval = MONTH;
        }
        MicroSecondDate nextCheck = ClockUtil.now().add(nextInterval);
        setNextCheck(nextCheck);
        logger.debug("no update check wanted for " + nextInterval
                + ", next at " + nextCheck);
    }

    protected void handleUpdateNonGUI(LocationUpdate locationUpdate)
            throws BackingStoreException {
        System.err.println("*******************************************************");
        System.err.println();
        System.err.println("An updated version of " + programName
                + " is available!");
        System.err.println("Please go to " + locationUpdate.getLocation()
                + " to get the latest version.");
        System.err.println();
        System.err.println("*******************************************************");
        setNextCheck(ClockUtil.now().add(SIX_HOUR));
    }
    
    protected MicroSecondDate getNextCheck() {
        if ( usePrefs) {
            MicroSecondDate now = ClockUtil.now();
            String nextCheckDate = getPrefs().get(prefsName, now.subtract(SIX_HOUR).getISOString());
            return new MicroSecondDate(nextCheckDate);
        } else {
            return ClockUtil.now().subtract(SIX_HOUR);
        }
    }
    
    protected void setNextCheck(MicroSecondDate date) throws BackingStoreException {
        if (usePrefs) {
            getPrefs().put(prefsName, date.getISOString());
            getPrefs().flush();
        }
    }
    
    protected Preferences getPrefs() {
        if (prefs == null) {
            prefs = Preferences.userNodeForPackage(this.getClass()).node("UpdateCheckerTask");
        }
        return prefs;
    }

    protected final TimeInterval SIX_HOUR = new TimeInterval(6, UnitImpl.HOUR);

    protected final TimeInterval FORTNIGHT = new TimeInterval(14, UnitImpl.DAY);

    protected final TimeInterval MONTH = new TimeInterval(30, UnitImpl.DAY);

    protected String prefsName;

    protected String version;

    protected String programName;

    protected boolean forceCheck;

    protected boolean showNoUpdate = false;

    protected boolean isGui;

    static boolean checkedYet = false;

    protected String updateURL;

    protected Preferences prefs;
    
    protected boolean usePrefs = true;
    
    static final String NEXT_CHECK_DATE = "nextCheckDate";

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UpdateCheckerJob.class);
}
