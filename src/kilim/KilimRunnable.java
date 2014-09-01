package kilim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KilimRunnable implements Runnable {
    private static final Logger logger_ = LoggerFactory.getLogger(KilimRunnable.class);
    private long                creationTime_;
    private long                timeInQ_;
    private long                executionStartTime_;
    private long                processingTime_;

    public KilimRunnable() {
        creationTime_ = System.currentTimeMillis();
    }

    private void beforeExecute() {
        executionStartTime_ = System.currentTimeMillis();
        timeInQ_ = (executionStartTime_ - creationTime_);
    }

    public abstract void doWork();

    public void run() {
        beforeExecute();
        doWork();
        afterExecute();
    }

    private void afterExecute() {
        long currentTime = System.currentTimeMillis();
        processingTime_ = timeInQ_ + (currentTime - executionStartTime_);
        logger_.debug("timeinQ : " + timeInQ_);
        logger_.debug("processingtime : " + processingTime_);
    }

    protected long getTimeInQ() {
        return timeInQ_;
    }

    protected long getProcessingTime() {
        return processingTime_;
    }
}
