/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 *******************************************************************************/
package com.rebaze.maven.payload.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Creates a bill of material for the reactor project.
 *
 * Since this is all about reproducing a build in a different context we may call this halo-maven-plugin.
 *
 * @goal deploy
 */
public class DeployPayloadMojo extends AbstractMojo
{
    /**
     * File to be created (fully qualified)
     *
     * @parameter property="bill" default-value="target/build.payload"
     */
    private File bill;

    /**
     * Target Repository ID
     *
     * @parameter property="targetRepositoryID"
     */
    private String targetRepositoryID;

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

    @Override public void execute() throws MojoExecutionException, MojoFailureException
    {
        // TODO: only invoke reactor.

        if ( project.isExecutionRoot() )
        {

            deployBill();
        }
    }

    private void deployBill()
    {
        RemoteRepository targetRepository = selectTargetRepo();
        List<String> sortedArtifacts = readInputBill();
        try
        {
            List<Artifact> listOfArtifacts = parseAndResolveArtifacts( sortedArtifacts, remoteRepos );
            DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository( targetRepository );

            for ( Artifact artifact : listOfArtifacts )
            {
                assert ( artifact.getFile() != null );
                deployRequest.addArtifact( artifact );
            }
            getLog().info( "Deployment of " + deployRequest.getArtifacts().size() + " artifacts .." );

            DeployResult result = repoSystem.deploy( repoSession, deployRequest );
            getLog().info( "Deployment Result: " + result.getArtifacts().size() );
        }
        catch ( DeploymentException e )
        {
            getLog().error( "Problem deploying set..!", e );
        }
        catch ( ArtifactResolutionException e )
        {
            getLog().error( "Problem resolving artifact(s)..!", e );
        }
    }

    private RemoteRepository selectTargetRepo()
    {
        for ( RemoteRepository repo : this.remoteRepos )
        {
            getLog().info( "Using repo: " + repo );
            if ( repo.getId().equals( targetRepositoryID ) )
            {
                return repo;
            }
        }
        throw new IllegalArgumentException( "Target Repository ID " + targetRepositoryID + " is unkown. Is it configured?" );
    }

    private List<String> readInputBill()
    {
        File input = bill;
        Set<String> artifacts = new HashSet<>();
        try ( BufferedReader reader = new BufferedReader( new FileReader( input ) ) )
        {
            String line = null;
            getLog().info( "Preparing deployment request.. " );
            while ( ( line = reader.readLine() ) != null )
            {
                artifacts.add( line );
            }
        }
        catch ( IOException e )
        {
            getLog().error( "Cannot parse bill: " + input.getAbsolutePath(), e );
            return null;
        }

        List<String> sortedArtifacts = new ArrayList<>( artifacts );
        sort( artifacts );
        return sortedArtifacts;
    }

    private List<Artifact> parseAndResolveArtifacts( Collection<String> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        List<Artifact> artifactList = new ArrayList<>();
        for ( String a : artifacts )
        {
            Artifact artifact = new DefaultArtifact( a );
            artifactList.add( artifact );
        }
        return resolve( artifactList, allowedRepositories );
    }

    private List<Artifact> resolve( Collection<Artifact> artifacts, List<RemoteRepository> allowedRepositories ) throws ArtifactResolutionException
    {
        Collection<ArtifactRequest> artifactRequests = new ArrayList<>();
        List<Artifact> result = new ArrayList<>( artifacts.size() );

        for ( Artifact a : artifacts )
        {
            ArtifactRequest request = new ArtifactRequest( a, allowedRepositories, null );
            artifactRequests.add( request );
            result.add( a );
        }

        List<ArtifactResult> reply = repoSystem.resolveArtifacts( repoSession, artifactRequests );
        for ( ArtifactResult res : reply )
        {
            if ( !res.isMissing() )
            {
                result.add( res.getArtifact() );
            }
            else
            {
                getLog().warn( "Artifact " + res.getArtifact() + " is still missing." );
            }

        }
        return result;
    }

    private List<String> sort( Set<String> artifacts )
    {
        List<String> sortedArtifacts = new ArrayList<>( artifacts );
        Collections.sort( sortedArtifacts );
        return sortedArtifacts;
    }
}
