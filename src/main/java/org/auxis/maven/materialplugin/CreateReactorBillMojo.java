/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 * <p/>
 * Contributors:
 * Apache Community - initial mojo (dependency plugin)
 * Rebaze - collect them all.
 *******************************************************************************/
package org.auxis.maven.materialplugin;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Creates a bill of material for the reactor project.
 *
 * @goal create-bill
 */
public class CreateReactorBillMojo extends AbstractMojo
{

    private static final String BILL_CACHE = "BILL";

    /**
     * File to be created (fully qualified)
     *
     * @parameter property="bill"
     */
    private File bill;

    /**
     * @parameter property="ignoreSnapshots" default-value=true
     */
    private boolean ignoreSnapshots;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    /**
     * @parameter default-value="${project}"
     * @readonly
     */
    protected MavenProject project;

    /**
     * @component
     */
    protected List<ArtifactRepository> reps;

    /**
     * @component
     */
    protected ArtifactHandlerManager artifactHandlerManager;

    /**
     * @component
     */
    private ProjectBuilder mavenProjectBuilder;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Set<Artifact> billSet = readBillCache();
        Set<Artifact> projectDeps = getProjectDepedencies();
        Set<Artifact> transitiveDeps = getProjectDepedencies();
        billSet.add( toAetherArtifact( project.getArtifact() ) );
        if (project.getParentArtifact() != null)
        {
            billSet.add( toAetherArtifact( project.getParentArtifact() ) );
        }

        for ( Artifact a : projectDeps )
        {
            if ( a.isSnapshot() && ignoreSnapshots )
            {
                // skip snapshot
            }
            if ( billSet.contains( a ) )
            {
                // do nothing
            }
            else
            {
                try
                {
                    addTransitive( transitiveDeps );
                }
                catch ( Exception e )
                {
                    getLog().error( "Problem collection deps.", e );
                }
                billSet.addAll( transitiveDeps );
            }
        }
        getLog().info( "Total bill size : " + billSet.size() + " artifacts. This project: " + projectDeps.size() + "." );
        if ( bill == null )
        {
            bill = new File( project.getBasedir(), "/target/bill.txt" );
        }
        writeBillToDisk( billSet );
    }

    private void writeBillToDisk( Set<Artifact> billSet )
    {
        Set<String> dedup = new HashSet<String>(billSet.size());
        for ( Artifact a : billSet )
        {
            if (!a.isSnapshot())
            {
                dedup.add( a.toString() );
            }
        }
        List<String> feed = new ArrayList<String>(dedup);

        Collections.sort( feed );
        try
        {
            BufferedWriter writer = new BufferedWriter( new FileWriter( this.bill ) );
            for ( String line : feed )
            {
                writer.write( line );
                writer.newLine();
            }
            writer.close();
        }
        catch ( IOException e )
        {
            getLog().error( "Problem writing bill to " + bill.getAbsolutePath() + "." );
        }
        getLog().info( "Done. Bill updated : " + bill.getAbsolutePath() + "." );
    }

    private Set<Artifact> readBillCache()
    {
        Set<Artifact> billSet = ( Set<Artifact> ) repoSession.getCache().get( repoSession, BILL_CACHE );
        if ( billSet == null )
        {
            billSet = new HashSet<Artifact>();
        }
        return billSet;
    }

    private void addTransitive( Set<Artifact> topTreashold )
    {
        DependencyFlatDumper lister = new DependencyFlatDumper();
        Set<Artifact> input = new HashSet<>( topTreashold );
        for ( Artifact artifact : input )
        {
            try
            {
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot( new Dependency( artifact, "compile" ) );
                collectRequest.setRepositories( remoteRepos );

                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact( artifact );
                artifactRequest.setRepositories( remoteRepos );

                // ArtifactResult res = repoSystem.resolveArtifact( repoSession, artifactRequest );
                DependencyRequest depReq = new DependencyRequest();
                depReq.setCollectRequest( collectRequest );
                DependencyResult resDeps = repoSystem.resolveDependencies( repoSession, depReq );

                for ( ArtifactResult ar : resDeps.getArtifactResults() )
                {
                    try
                    {
                        topTreashold.add( ar.getArtifact() );
                        ProjectBuildingRequest reqProjectBuilder = new DefaultProjectBuildingRequest();
                        reqProjectBuilder.setRepositorySession( repoSession );
                        reqProjectBuilder.setRemoteRepositories( project.getRemoteArtifactRepositories() );
                        MavenProject subProject = mavenProjectBuilder.build( toMavenArtifact( artifact ), reqProjectBuilder ).getProject();
                        if ( subProject.getParentArtifact() != null )
                        {
                            topTreashold.add( toAetherArtifact( subProject.getParentArtifact() ) );
                        }
                    }
                    catch ( Exception e )
                    {
                        getLog().warn( "Problem resolving " + ar.getArtifact() + " (" + e.getMessage() + ")" );
                    }
                }
                CollectResult collectResult = repoSystem.collectDependencies( repoSession, collectRequest );
                collectResult.getRoot().accept( lister );
            }
            catch ( Exception e )
            {
                getLog().warn( "Problem resolving " + artifact + " (" + e.getMessage() + ")" );
            }
        }
        for ( Artifact a : lister )
        {
            //topTreashold.add(a);
        }
    }

    private List<ArtifactRepository> toMavenRepos( List<RemoteRepository> remoteRepos )
    {
        return null;
    }

    private org.eclipse.aether.artifact.Artifact toAetherArtifact( org.apache.maven.artifact.Artifact a )
    {
        return new org.eclipse.aether.artifact.DefaultArtifact( a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getArtifactHandler().getExtension(),
            a.getVersion() );
    }

    private org.apache.maven.artifact.Artifact toMavenArtifact( org.eclipse.aether.artifact.Artifact a )
    {
        return new org.apache.maven.artifact.DefaultArtifact( a.getGroupId(), a.getArtifactId(), a.getVersion(), "compile", a.getExtension(), a.getClassifier(),
            artifactHandlerManager.getArtifactHandler( a.getExtension() ) );
    }

    private Set<Artifact> getProjectDepedencies()
    {
        Set<Artifact> artifacts = new HashSet<Artifact>();
        final Set<org.apache.maven.artifact.Artifact> plugins = project.getPluginArtifacts();
        mapToAetherArtifacts( artifacts, plugins );
        final Set<org.apache.maven.artifact.Artifact> reports = project.getReportArtifacts();
        mapToAetherArtifacts( artifacts, reports );
        mapToAetherArtifacts( artifacts, project.getAttachedArtifacts() );
        final Set<org.apache.maven.artifact.Artifact> deps = project.getDependencyArtifacts();
        mapToAetherArtifacts( artifacts, deps );
        if ( project.getDependencyManagement() != null )
        {
            for ( org.apache.maven.model.Dependency dep : project.getDependencyManagement().getDependencies() )
            {
                if ( dep.getVersion() != null )
                {
                    artifacts.add( new org.eclipse.aether.artifact.DefaultArtifact( dep.getGroupId(), dep.getArtifactId(), null,
                        dep.getVersion() ) );
                }
            }
        }

        return artifacts;
    }

    private void mapToAetherArtifacts( Set<Artifact> artifacts, Collection<org.apache.maven.artifact.Artifact> plugins )
    {
        for ( org.apache.maven.artifact.Artifact a : plugins )
        {
            artifacts.add( toAetherArtifact( a ) );
        }
    }

    class DependencyFlatDumper implements DependencyVisitor, Iterable<Artifact>
    {

        private List<Artifact> flatDependencyList = new ArrayList<Artifact>();

        public boolean visitEnter( DependencyNode node )
        {
            Artifact a = node.getArtifact();
            flatDependencyList.add( a );
            return true;
        }

        public boolean visitLeave( DependencyNode node )
        {
            return true;
        }

        @Override
        public Iterator<Artifact> iterator()
        {
            return flatDependencyList.iterator();
        }
    }
}
