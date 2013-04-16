/*
 * Copyright 2013 Grzegorz Slowikowski (gslowikowski at gmail dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.code.play2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.javascript.jscomp.CompilerOptions;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import org.codehaus.plexus.util.DirectoryScanner;

import com.google.code.play2.jscompile.JavascriptCompiler;

/**
 * ...
 * 
 * @author <a href="mailto:gslowikowski@gmail.com">Grzegorz Slowikowski</a>
 * @since 1.0.0
 */
@Mojo( name = "closure-compile" )
public class Play2ClosureCompileMojo
    extends AbstractPlay2Mojo
{

    private final static String assetsSourceDirectoryName = "app/assets";

    private final static String targetDirectoryName = "resource_managed/main";

    private final static String[] closureExcludes = new String[] { "**/_*" };

    private final static String[] closureIncludes = new String[] { "**/*.js" };

    protected void internalExecute()
        throws MojoExecutionException, MojoFailureException, IOException
    {
        File basedir = project.getBasedir();
        File assetsSourceDirectory = new File( basedir, assetsSourceDirectoryName );

        if ( !assetsSourceDirectory.isDirectory() )
            return; // nothing to do

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( assetsSourceDirectory );
        scanner.setExcludes( closureExcludes );
        scanner.setIncludes( closureIncludes );
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        if ( files.length > 0 )
        {
            File targetDirectory = new File( project.getBuild().getDirectory() );
            File generatedDirectory = new File( targetDirectory, targetDirectoryName );
            File outputDirectory = new File( generatedDirectory, "public" );

            for ( String fileName : files )
            {
                getLog().debug( String.format( "Processing file \"%s\"", fileName ) );
                File srcJsFile = new File( assetsSourceDirectory, fileName );

                // String jsFileName = fileName.replace( ".coffee", ".js" );
                File jsFile = new File( outputDirectory, fileName/* jsFileName */);

                String minifiedJsFileName = fileName.replace( ".js", ".min.js" );
                File minifiedJsFile = new File( outputDirectory, minifiedJsFileName );

                boolean modified = true;
                if ( jsFile.isFile() )
                {
                    modified = ( jsFile.lastModified() < srcJsFile.lastModified() );
                }

                if ( modified )
                {
                    List<String> simpleCompilerOptions = new ArrayList<String>();// TODO-add options
                    CompilerOptions fullCompilerOptions = null;
                    createDirectory( jsFile.getParentFile(), false );
                    JavascriptCompiler compiler = JavascriptCompiler.getInstance();
                    JavascriptCompiler.CompileResult result =
                        compiler.compile( srcJsFile, simpleCompilerOptions, fullCompilerOptions );
                    String jsContent = result.getJs();
                    String minifiedJsContent = result.getMinifiedJs();
                    createDirectory( jsFile.getParentFile(), false );
                    writeToFile( jsFile, jsContent );
                    if ( minifiedJsContent != null )
                    {
                        createDirectory( minifiedJsFile.getParentFile(), false );
                        writeToFile( minifiedJsFile, minifiedJsContent );
                    }
                    else
                    {
                        if ( minifiedJsFile.exists() )
                        {// TODO-check if isFile
                            minifiedJsFile.delete();// TODO-check result
                        }
                    }
                }
            }

            boolean resourceAlreadyAdded = false;
            for ( Resource res : (List<Resource>) project.getResources() )
            {
                if ( res.getDirectory().equals( generatedDirectory.getAbsolutePath() ) )
                {
                    resourceAlreadyAdded = true;
                    break;
                }
            }
            if ( !resourceAlreadyAdded )
            {
                Resource resource = new Resource();
                resource.setDirectory( generatedDirectory.getAbsolutePath() );
                project.addResource( resource );
                getLog().debug( "Added resource: " + resource.getDirectory() );
            }
        }
    }

}