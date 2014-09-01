package kilim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class KilimStats {
    private static final String   colon_             = " : ";
    private static final int      defaultMaxBuckets_ = 33;
    protected static final String newLine_           = System.getProperty("line.separator");
    /* last bucket is [2^(maxBuckets-3)+1, inf0 */
    private int                   maxBuckets_;

    private AtomicLong            numUpdates_        = new AtomicLong(0);
    private List<AtomicLong>      buckets_           = new ArrayList<AtomicLong>();
    private AtomicLong            max_               = new AtomicLong(0);
    private AtomicLong            aggregrate_        = new AtomicLong(0);
    private String                units_;

    public static final String    milliseconds_      = "ms";

    public KilimStats() {
        /* assume units in 'time' */
        this(milliseconds_);
    }

    public KilimStats(int maxBuckets) {
        /* assume units in 'time' */
        this(maxBuckets, milliseconds_);
    }

    public KilimStats(String units) {
        this(defaultMaxBuckets_, units);
    }

    public KilimStats(int maxBuckets, String units) {
        maxBuckets_ = maxBuckets;
        units_ = units;
        for (int i = 0; i < maxBuckets_; ++i) {
            buckets_.add(new AtomicLong(0));
        }
    }

    private double log2(double num) {
        return (double) (Math.log(num) / Math.log(2));
    }

    private int getBucket(int num) {
        int i = 0;
        if (num > 0) {
            i = (int) Math.ceil(log2(num));
            i = Math.min(i + 1, maxBuckets_ - 1);
        }
        return i;
    }

    private String getRange(int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (index <= 2) {
            sb.append(index);
        } else {
            sb.append((1 << (index - 2)) + 1);
            sb.append('-');
            if (index < (maxBuckets_ - 1))
                sb.append(1 << (index - 1));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * @param stat
     */
    public void record(int stat) {
        int index = getBucket(stat);
        long current = max_.get();
        if (stat > current)
            max_.compareAndSet(current, stat);
        aggregrate_.addAndGet(stat);
        buckets_.get(index).incrementAndGet();
        numUpdates_.incrementAndGet();

    }

    public String dumpStatistics(String key) {
        StringBuilder sb = new StringBuilder();

        int size = buckets_.size();
        String token = key;
        for (int i = 0; i < size; ++i) {
            if (buckets_.get(i).get() > 0) {
                sb.append(token + "/range ");
                sb.append(String.format("%14s", getRange(i)));
                sb.append("\t" + colon_);
                sb.append(buckets_.get(i).get());
                sb.append(newLine_);
            }
        }

        sb.append(token + "/max  " + max_.get() + " " + units_);
        sb.append(newLine_);
        sb.append(token + "/aggr " + aggregrate_.get() + " " + units_);
        sb.append(newLine_);
        double average = (numUpdates_.get() == 0) ? 0
                : (double) aggregrate_.get() / (double) numUpdates_.get();
        sb.append(key + "/avg " + average + " " + units_);
        sb.append(newLine_);
        sb.append(key + "/nupdates " + numUpdates_);
        sb.append(newLine_);
        sb.append(newLine_);
        return sb.toString();
    }
}
