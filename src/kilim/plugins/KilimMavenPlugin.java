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
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES,
        requiresDependencyResolution=ResolutionScope.RUNTIME)
public class KilimMavenPlugin extends AbstractMojo {

    /** additional arguments to pass to the weaver */
    @Parameter(defaultValue="-q", property = "kilim.args", required = false)
    private String args;
    
    /** location of the class files */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "kilim.in", required = true)
    private File in;

    /** location of the test class files */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "kilim.tin", required = true)
    private File tin;

    /** destination for the woven class files */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "kilim.out", required = true)
    private File out;

    /** destination for the woven test class files */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "kilim.tout", required = true)
    private File tout;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        try {
            String indir = in.getAbsolutePath();
            String tindir = tin.getAbsolutePath();
            String outdir = out.getAbsolutePath();
            String toutdir = tout.getAbsolutePath();

            {
                getLog().debug("kilim weaver input/output dirs are: " + indir + ", " + outdir);
                String [] roots = project.getCompileClasspathElements().toArray(new String[0]);

                Weaver.outputDir = outdir;
                getLog().debug("plugin.args: " + args);
                if (args != null && args.length() > 0) {
                    Weaver.parseArgs(args.split(" "));
                }

                int err = Weaver.doMain(new String []{ indir },roots);
                getLog().debug("kilim weaver done");
                if (err > 0)
                    throw new MojoExecutionException("Error while weaving the classes");
            }

            {
                getLog().debug("kilim weaver test input/output dirs are: " + tindir + ", " + toutdir);
                String [] troots = project.getTestClasspathElements().toArray(new String[0]);

                Weaver.outputDir = toutdir;
                int err = Weaver.doMain(new String []{ tindir },troots);
                getLog().debug("kilim weaver done - tests");
                if (err > 0)
                    throw new MojoExecutionException("Error while weaving the test classes");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error while weaving the classes", e);
        }
    }
}
