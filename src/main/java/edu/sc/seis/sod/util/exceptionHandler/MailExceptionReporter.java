package edu.sc.seis.sod.util.exceptionHandler;

import java.util.List;
import java.util.Properties;

/**
 * The four key strings' SMTP, SUBJECT, FROM and TO values must be set in the
 * passed in properties
 */
public class MailExceptionReporter extends ResultMailer implements
        ExceptionReporter {

    public MailExceptionReporter(Properties props)
            throws MissingPropertyException {
        super(props);
        if(props.containsKey(LIMIT)) {
            limit = Integer.parseInt(props.getProperty(LIMIT));
        }
    }

    public void report(String message, Throwable e, List sections)
            throws Exception {
        if(numSent < limit) {
            numSent++;
            mail(message, ExceptionReporterUtils.getTrace(e), sections);
        } else {
            logger.debug("Not sending an email since " + numSent
                    + " have been sent and " + limit
                    + " is the max number to send");
        }
    }
    
    public static void addMailExceptionReporter(Properties mailProps) {
        if(mailProps.containsKey("mail.smtp.host")) {
            try {
                GlobalExceptionHandler.add(new MailExceptionReporter(mailProps));
            } catch(MissingPropertyException e) {
                logger.debug("Not able to add a mail reporter.  This is only a problem if you specified one",
                             e);
            }
        } else {
            logger.debug("Not trying to add a mail reporter since mail.smtp.host isn't set");
        }
    }

    /**
     * mail.limit specifies the number of emails to send
     */
    public static final String LIMIT = "mail.limit";

    private int numSent = 0;

    private int limit = Integer.MAX_VALUE;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MailExceptionReporter.class);
}
