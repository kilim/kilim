// copyright 2016 seth lytle
package kilim.analysis;

import kilim.mirrors.CachedClassMirrors;
import kilim.mirrors.Detector;

public class KilimContext {
    static public KilimContext DEFAULT = new KilimContext();
    
    public Detector detector = new Detector(new CachedClassMirrors());
    
}
