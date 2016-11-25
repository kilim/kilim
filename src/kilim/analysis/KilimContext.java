// copyright 2016 nqzero - offered under the terms of the MIT License
package kilim.analysis;

import kilim.mirrors.CachedClassMirrors;
import kilim.mirrors.Detector;

public class KilimContext {
    static public KilimContext DEFAULT = new KilimContext();
    
    public Detector detector;
    
    public KilimContext() {
        detector = new Detector(new CachedClassMirrors());
    }
    public KilimContext(CachedClassMirrors mirrors) {
        detector = new Detector(mirrors);
    }
    
    
}
