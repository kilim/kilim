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

    /**
     * Location of the class files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "kilimIn", required = true)
    private File inputDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "kilimTin", required = true)
    private File testDirectory;

    /**
     * Location of the woven class files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "kilimOut", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "kilimTout", required = true)
    private File testOutputDirectory;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        try {
            String indir = inputDirectory.getAbsolutePath();
            String tindir = testDirectory.getAbsolutePath();
            String outdir = outputDirectory.getAbsolutePath();
            String toutdir = testOutputDirectory.getAbsolutePath();

            {
                getLog().info("kilim weaver input/output dirs are: " + indir + ", " + outdir);
                String [] roots = project.getCompileClasspathElements().toArray(new String[0]);

                Weaver.outputDir = outdir;
                int err = Weaver.doMain(new String []{ indir },roots);
                getLog().info("kilim weaver done");
                if (err > 0)
                    throw new MojoExecutionException("Error while weaving the classes");
            }

            {
                getLog().info("kilim weaver test input/output dirs are: " + tindir + ", " + toutdir);
                String [] troots = project.getTestClasspathElements().toArray(new String[0]);

                Weaver.outputDir = toutdir;
                int err = Weaver.doMain(new String []{ tindir },troots);
                getLog().info("kilim weaver done - tests");
                if (err > 0)
                    throw new MojoExecutionException("Error while weaving the test classes");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error while weaving the classes", e);
        }
    }
}
