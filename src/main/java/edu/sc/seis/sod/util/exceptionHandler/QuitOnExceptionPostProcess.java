package edu.sc.seis.sod.util.exceptionHandler;

public class QuitOnExceptionPostProcess implements PostProcess {

    public QuitOnExceptionPostProcess(Class c) {
        this(c, 1);
    }
    public QuitOnExceptionPostProcess(Class c, int processResult) {
        quitType = c;
        this.processResult = processResult;
    }

    public void process(String message, Throwable thrown) {
        if(quitType.isInstance(thrown)) {
            logger.error("Quiting ...caught an exception of type: "
                    + quitType.getName()+"  message="+message, thrown);
            System.exit(1);
        } else if (thrown.getCause() != null) {
            process(message, thrown.getCause());
        }
    }
    
    int processResult;

    Class quitType;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(QuitOnExceptionPostProcess.class);
}
