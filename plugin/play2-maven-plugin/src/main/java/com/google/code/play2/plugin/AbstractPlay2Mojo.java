/*
 * Copyright 2013-2014 Grzegorz Slowikowski (gslowikowski at gmail dot com)
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

package com.google.code.play2.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;

import org.codehaus.plexus.util.FileUtils;

import com.google.code.play2.provider.api.Play2Provider;
import com.google.code.play2.provider.api.Play2Providers;

/**
 * Abstract base class for Play&#33; Mojos.
 * 
 * @author <a href="mailto:gslowikowski@gmail.com">Grzegorz Slowikowski</a>
 */
public abstract class AbstractPlay2Mojo
    extends AbstractMojo
{
    /**
     * Used to automatically select one of the "well known" Play&#33; providers if no provider added explicitly as plugin's dependency.
     * 
     * @since 1.0.0
     */
    @Parameter( property = "play2.version" )
    protected String playVersion;

    /**
     * <i>Maven Internal</i>: Project to interact with.
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    /**
     * Maven project builder used to resolve artifacts.
     */
    @Component
    protected MavenProjectBuilder projectBuilder;

    /**
     * Artifact factory used to look up artifacts in the remote repository.
     */
    @Component
    protected ArtifactFactory factory;

    /**
     * Artifact resolver used to resolve artifacts.
     */
    @Component
    protected ArtifactResolver resolver;

    /**
     * Location of the local repository.
     */
    @Parameter( property = "localRepository", readonly = true, required = true )
    protected ArtifactRepository localRepo;

    /**
     * Remote repositories used by the resolver
     */
    @Parameter( property = "project.remoteArtifactRepositories", readonly = true, required = true )
    protected List<ArtifactRepository> remoteRepos;

    /**
     * Plugin's groupId used for well known providers resolution
     */
    @Parameter( property = "plugin.groupId", readonly = true, required = true )
    private String pluginGroupId;

    /**
     * Plugin's version used for well known providers resolution
     */
    @Parameter( property = "plugin.version", readonly = true, required = true )
    private String pluginVersion;

    /**
     * Map of provider implementations. For now only zero or one allowed.
     */
    @Component( role = Play2Provider.class )
    private Map<String, Play2Provider> play2Providers;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !"pom".equals( project.getPackaging() ) )
        {
            try
            {
                long ts = System.currentTimeMillis();
                internalExecute();
                long te = System.currentTimeMillis();
                getLog().debug( String.format( "Mojo execution time: %d ms", Long.valueOf( te - ts ) ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "?", e );
            }
        }
    }

    protected abstract void internalExecute()
        throws MojoExecutionException, MojoFailureException, IOException;

    protected final BufferedReader createBufferedFileReader( File file, String encoding )
        throws FileNotFoundException, UnsupportedEncodingException
    {
        return new BufferedReader( new InputStreamReader( new FileInputStream( file ), encoding ) );
    }

    protected final BufferedWriter createBufferedFileWriter( File file, String encoding )
        throws FileNotFoundException, UnsupportedEncodingException
    {
        return new BufferedWriter( new OutputStreamWriter( new FileOutputStream( file ), encoding ) );
    }

    protected String readFileFirstLine( File file )
        throws IOException
    {
        String result = null;

        BufferedReader is = createBufferedFileReader( file, "UTF-8" );
        try
        {
            result = is.readLine();
        }
        finally
        {
            is.close();
        }
        return result;
    }

    protected void writeToFile( File file, String line )
        throws IOException
    {
        BufferedWriter writer = createBufferedFileWriter( file, "UTF-8" );
        try
        {
            writer.write( line );
        }
        finally
        {
            writer.flush();
            writer.close();
        }
    }

    protected void createDirectory( File directory, boolean overwrite )
        throws IOException
    {
        if ( directory.exists() )
        {
            if ( directory.isDirectory() )
            {
                if ( overwrite )
                {
                    FileUtils.cleanDirectory( directory );
                }
            }
            else
            // file if ( directory.isFile() )
            {
                getLog().info( String.format( "Deleting \"%s\" file", directory ) ); // TODO-more descriptive message
                if ( !directory.delete() )
                {
                    throw new IOException( String.format( "Cannot delete \"%s\" file", directory.getCanonicalPath() ) );
                }
            }
        }

        if ( !directory.exists() )
        {
            if ( !directory.mkdirs() )
            {
                throw new IOException( String.format( "Cannot create \"%s\" directory", directory.getCanonicalPath() ) );
            }
        }
    }

    // Cached classloaders
    private static final ConcurrentHashMap<String, ClassLoader> cachedClassLoaders = new ConcurrentHashMap<String, ClassLoader>( 2 );

    private static ClassLoader getCachedClassLoader( String providerId )
    {
        return cachedClassLoaders.get( providerId );
    }

    private static void setCachedClassLoader( String providerId, ClassLoader classLoader )
    {
        cachedClassLoaders.put( providerId, classLoader );
    }

    protected Play2Provider getProvider()
        throws MojoExecutionException
    {
        Play2Provider provider = null;
        if ( !play2Providers.isEmpty() )
        {
            provider = getDeclaredProvider();
        }
        else
        {
            provider = getWellKnownProvider();
        }
        return provider;
    }

    private Play2Provider getDeclaredProvider()
        throws MojoExecutionException
    {
        if ( play2Providers.size() > 1 )
        {
            throw new MojoExecutionException( "Too many Play! providers defined. A maximum of one allowed." );
        }

        Map.Entry<String, Play2Provider> providerEntry = play2Providers.entrySet().iterator().next();
        String providerId = providerEntry.getKey();
        Play2Provider provider = providerEntry.getValue();

        getLog().debug( String.format( "Using declared provider \"%s\".", providerId ) );

        return provider;
    }

    private Play2Provider getWellKnownProvider()
        throws MojoExecutionException
    {
        try
        {
            String providerId = Play2Providers.getDefaultProviderId( playVersion );
            ClassLoader providerClassLoader = getCachedClassLoader( providerId );
            if ( providerClassLoader == null )
            {
                getLog().debug( String.format( "Cached classloader for provider \"%s\" not available.", providerId ) );
            }
            else
            {
                if ( providerClassLoader.getParent() == Thread.currentThread().getContextClassLoader() )
                {
                    getLog().debug( String.format( "Using cached classloader for provider \"%s\".", providerId ) );
                }
                else
                {
                    getLog().debug( String.format( "Invalidated cached classloader for provider \"%s\". Parent classloader changed from %d to %d.",
                                                   providerId,
                                                   Integer.valueOf( providerClassLoader.getParent().hashCode() ),
                                                   Integer.valueOf( Thread.currentThread().getContextClassLoader().hashCode() ) ) );
                    providerClassLoader = null;
                }
            }

            if ( providerClassLoader == null )
            {
                Artifact providerArtifact =
                    getResolvedArtifact( pluginGroupId, "play2-provider-" + providerId, pluginVersion );

                Set<Artifact> providerDependencies = getAllDependencies( providerArtifact );
                List<File> classPathFiles = new ArrayList<File>( providerDependencies.size() + 2 );
                classPathFiles.add( providerArtifact.getFile() );
                for ( Artifact dependencyArtifact : providerDependencies )
                {
                    classPathFiles.add( dependencyArtifact.getFile() );
                }

                List<URL> classPathUrls = new ArrayList<URL>( classPathFiles.size() );
                for ( File classPathFile : classPathFiles )
                {
                    classPathUrls.add( new URL( classPathFile.toURI().toASCIIString() ) );
                }

                providerClassLoader =
                    new URLClassLoader( classPathUrls.toArray( new URL[classPathUrls.size()] ),
                                        Thread.currentThread().getContextClassLoader() );
                getLog().debug( "Setting cached classloader for " + providerId );
                setCachedClassLoader( providerId, providerClassLoader );
            }

            String providerClassName =
                String.format( "com.google.code.play2.provider.%s.%sProvider", providerId,
                               providerId.substring( 0, 1 ).toUpperCase( Locale.ROOT ) + providerId.substring( 1 ) );
            Play2Provider provider = (Play2Provider) providerClassLoader.loadClass( providerClassName ).newInstance();

            getLog().debug( String.format( "Using autodetected provider \"%s\".", providerId ) );

            return provider;
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( ClassNotFoundException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( IllegalAccessException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( InstantiationException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( "Provider autodetection failed", e );
        }
    }

    // Private utility methods

    protected/*private*/ Artifact getResolvedArtifact( String groupId, String artifactId, String version )
        throws ArtifactNotFoundException, ArtifactResolutionException
    {
        Artifact artifact = factory.createArtifact( groupId, artifactId, version, Artifact.SCOPE_RUNTIME, "jar" );
        resolver.resolve( artifact, remoteRepos, localRepo );
        return artifact;
    }

    protected/*private*/ Set<Artifact> getAllDependencies( Artifact artifact )
        throws ArtifactNotFoundException, ArtifactResolutionException, InvalidDependencyVersionException,
        ProjectBuildingException
    {
        Set<Artifact> result = new HashSet<Artifact>();
        MavenProject p = projectBuilder.buildFromRepository( artifact, remoteRepos, localRepo );
        Set<Artifact> d = resolveDependencyArtifacts( p );
        result.addAll( d );
        for ( Artifact dependency : d )
        {
            Set<Artifact> transitive = getAllDependencies( dependency );
            result.addAll( transitive );
        }
        return result;
    }

    /**
     * This method resolves the dependency artifacts from the project.
     * 
     * @param theProject The POM.
     * @return resolved set of dependency artifacts.
     * @throws ArtifactResolutionException
     * @throws ArtifactNotFoundException
     * @throws InvalidDependencyVersionException
     */
    private Set<Artifact> resolveDependencyArtifacts( MavenProject theProject )
        throws ArtifactNotFoundException, ArtifactResolutionException, InvalidDependencyVersionException
    {
        AndArtifactFilter filter = new AndArtifactFilter();
        filter.add( new ScopeArtifactFilter( Artifact.SCOPE_TEST ) );
        filter.add( new NonOptionalArtifactFilter() );
        // TODO follow the dependenciesManagement and override rules
        Set<Artifact> artifacts = theProject.createArtifacts( factory, Artifact.SCOPE_RUNTIME, filter );
        for ( Artifact artifact : artifacts )
        {
            resolver.resolve( artifact, remoteRepos, localRepo );
        }
        return artifacts;
    }

    private static class NonOptionalArtifactFilter
        implements ArtifactFilter
    {
        @Override
        public boolean include( Artifact artifact )
        {
            return !artifact.isOptional();
        }
    }

}
