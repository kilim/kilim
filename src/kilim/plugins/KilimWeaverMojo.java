package kilim.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * Kilim class file weaver..!
 */
@Mojo(name = "weave", defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution=ResolutionScope.RUNTIME)
public class KilimWeaverMojo extends AbstractMojo {

    /**
     * Location of the class files.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "inputDir", required = true)
    private File inputDirectory;


    /**
     * Location of the weaved class files.
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
