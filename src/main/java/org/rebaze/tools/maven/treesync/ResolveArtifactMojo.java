/*******************************************************************************
 * Copyright (c) 2015 Rebaze GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.rebaze.tools.maven.treesync;

import java.rmi.Remote;
import java.util.*;

import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Syncronizes Project dependencies (plugin, reporting and build time deps) with a selected remote repository.
 * Executor needs write access to the deploy target repo.
 *
 * @goal sync-tree
 */
public class ResolveArtifactMojo
        extends AbstractMojo {

    /**
     * The {@code targetRepo} to be synced. User needs deployment rights.
     *
     * @parameter expression="${sync-tree.targetRepo}"
     */
    private String targetRepo;

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
    //@Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    public void execute()
            throws MojoExecutionException, MojoFailureException {
        Set<Artifact> topTreashold = getTopTreashold();
        try {
            addTransitive(topTreashold);
        } catch (DependencyCollectionException e) {
            throw new MojoExecutionException("Problem collecting dependencies.", e);
        }

        getLog().info(" Found Plugins: " + topTreashold.size());

        // select target repo:
        RemoteRepository targetRepo = null;
        for (RemoteRepository candidateRepo : remoteRepos ) {
            getLog().info("Candidate for sync: " + candidateRepo.getId());
            if (candidateRepo.getId().equals(this.targetRepo)) {
                targetRepo = candidateRepo;
                break;
            }
        }

        if (targetRepo != null ) {
            for (Artifact a : topTreashold) {
                // then  resolve & deploy:
                if (!isAlreadyAvailable(a)) {
                    //getLog().info(" + New Artifact to be deployed: " + a.toString());
                    try {
                        deploy(a,targetRepo);
                        getLog().info("Successfully deployed new artifact: " + a.toString() + " to " + targetRepo.getId());
                    } catch (DeploymentException e) {
                        getLog().warn("Deployment of " + a.toString() + " failed with " + e.getMessage());
                    }
                } else {
                    getLog().info("Artifact + " + a.toString() + " already availeble.");
                }
                getLog().info(" + " + a.toString());
            }
        }else {
            getLog().warn("Target repo " + targetRepo + " is not among configured repositories.");
        }
    }

    private void deploy(Artifact a, RemoteRepository distRepo) throws DeploymentException {
        DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(a);
        deployRequest.setRepository(distRepo);
        repoSystem.deploy(repoSession, deployRequest);
    }

    private boolean isAlreadyAvailable(Artifact a) {
        // TODO fix this
        return true;
    }

    private void addTransitive(Set<Artifact> topTreashold) throws DependencyCollectionException {
        DependencyFlatDumper lister = new DependencyFlatDumper();

        for (Artifact artifact : topTreashold) {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(artifact, "compile"));
            collectRequest.setRepositories(remoteRepos);
            CollectResult collectResult = repoSystem.collectDependencies(repoSession, collectRequest);
            collectResult.getRoot().accept(lister);
        }
        for (Artifact a : lister) {
            topTreashold.add(a);
        }

    }

    private org.eclipse.aether.artifact.Artifact toAetherArtifact( org.apache.maven.artifact.Artifact a ) {
        return new org.eclipse.aether.artifact.DefaultArtifact(a.getGroupId(),a.getArtifactId(),a.getType(),a.getVersion());
    }
/**
    private org.apache.maven.artifact.Artifact toMavenArtifact( org.eclipse.aether.artifact.Artifact a ){
        return new org.apache.maven.artifact.DefaultArtifact(a.getGroupId(),a.getArtifactId(),VersionRange.createFromVersion(a.getVersion()),"compile",a.getExtension(),"",handler);
    }
**/
    private void legacy() throws MojoFailureException, MojoExecutionException {
        Artifact artifact;
        try {
            artifact = new DefaultArtifact("");
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(project.getRemoteArtifactRepositories());

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        getLog().info("Resolved artifact " + artifact + " to " + result.getArtifact().getFile() + " from "
                + result.getRepository());
    }

    private Set<Artifact> getTopTreashold() {
        Set<Artifact> artifacts = new HashSet<Artifact>();

        final Set<org.apache.maven.artifact.Artifact> plugins = project.getPluginArtifacts();
        mapToAetherArtifacts(artifacts, plugins);
        final Set<org.apache.maven.artifact.Artifact> reports = project.getReportArtifacts();
        mapToAetherArtifacts(artifacts, reports);

        final Set<org.apache.maven.artifact.Artifact> deps = project.getDependencyArtifacts();
        mapToAetherArtifacts(artifacts, deps);

        return artifacts;
    }

    private void mapToAetherArtifacts(Set<Artifact> artifacts, Set<org.apache.maven.artifact.Artifact> plugins) {
        for (org.apache.maven.artifact.Artifact a : plugins) {
            artifacts.add(toAetherArtifact(a));
        }
    }

    class DependencyFlatDumper
            implements DependencyVisitor, Iterable<Artifact> {

        private List<Artifact> flatDependencyList = new ArrayList<Artifact>();

        public boolean visitEnter(DependencyNode node) {
            Artifact a = node.getArtifact();
            Dependency d = node.getDependency();
            flatDependencyList.add(a);
            return true;
        }

        public boolean visitLeave(DependencyNode node) {

            return true;
        }

        @Override
        public Iterator<Artifact> iterator() {
            return flatDependencyList.iterator();
        }
    }
}
