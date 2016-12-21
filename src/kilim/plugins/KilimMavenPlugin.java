// copyright 2013 github.com/jestan (Jestan Nirojan), 2016 nqzero - offered under the MIT License
//   jestan relicensed under mit from apache 2.0 in commit 6257a448d74616b4c2bc3a7d3cdf354de39165d7

package kilim.plugins;


import kilim.tools.Weaver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * maven plugin for ahead-of-time weaving of class files
 */
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution=ResolutionScope.RUNTIME)
public class KilimMavenPlugin extends AbstractMojo {

    /**
     * Location of the class files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "inputDir", required = true)
    private File inputDirectory;


    /**
     * Location of the woven class files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "outputDir", required = true)
    private File outputDirectory;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        try {
            String indir = inputDirectory.getAbsolutePath();
            String outdir = outputDirectory.getAbsolutePath();
            getLog().info("kilim weaver input/output dirs are: " + indir + ", " + outdir);
            
            String [] roots = project.getCompileClasspathElements().toArray(new String[0]);
            
            Weaver.outputDir = outdir;
            int err = Weaver.doMain(new String []{ indir },roots);
            getLog().info("kilim weaver done");
            if (err > 0)
                throw new MojoExecutionException("Error while weaving the classes");
        } catch (Exception e) {
            throw new MojoExecutionException("Error while weaving the classes", e);
        }
    }
}
