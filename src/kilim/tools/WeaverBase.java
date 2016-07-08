package kilim.tools;

import kilim.analysis.ClassInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface WeaverBase {

    List<ClassInfo>  weave(InputStream is) throws IOException;
    
}
