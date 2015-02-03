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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Creates a bill of material for the reactor project.
 *
 * @goal create-bill
 */
public class CreateReactorBillMojo extends AbstractMojo {

    private static final String BILL_CACHE = "BILL";

    /**
     * File to be created (fully qualified)
     *
     * @parameter expression="${bill}"
     */
    private File bill;

    /**
     * @parameter expression="${ignoreSnapshots}" default-value=true
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

    public void execute() throws MojoExecutionException, MojoFailureException {

        Set<Artifact> billSet = readBillCache();
        Set<Artifact> projectDeps = getProjectDepedencies();
        Set<Artifact> transitiveDeps = getProjectDepedencies();
        for (Artifact a : projectDeps) {
            if (a.isSnapshot() && ignoreSnapshots) {
                // skip snapshot
            }
            if (billSet.contains(a)) {
                // do nothing
            } else {
                try {
                    addTransitive(transitiveDeps);
                } catch (DependencyCollectionException e) {
                    getLog().error("Problem collection deps.", e);
                }
                billSet.addAll(billSet);
                billSet.addAll(transitiveDeps);
            }
        }
        getLog().info("Total bill size : " + billSet.size() + " artifacts. This project: " + projectDeps.size() + ".");
        if (bill == null) {
            bill = new File(project.getExecutionProject().getBasedir(), "/target/bill.txt");
        }
        writeBillToDisk(billSet);
    }

    private void writeBillToDisk(Set<Artifact> billSet) {
        List<String> feed = new ArrayList<String>();
        for (Artifact a : billSet) {
            feed.add(a.toString());
        }
        Collections.sort(feed);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(this.bill));
            for (String line : feed) {
                writer.write(line);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            getLog().error("Problem writing bill to " + bill.getAbsolutePath() + ".");
        }
        getLog().info("Bill updated : " + bill.getAbsolutePath() + ".");
    }

    private Set<Artifact> readBillCache() {
        Set<Artifact> billSet = (Set<Artifact>) repoSession.getCache().get(repoSession, BILL_CACHE);
        if (billSet == null) {
            billSet = new HashSet<Artifact>();
        }
        return billSet;
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

    private org.eclipse.aether.artifact.Artifact toAetherArtifact(org.apache.maven.artifact.Artifact a) {
        return new org.eclipse.aether.artifact.DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getArtifactHandler().getExtension(),
            a.getVersion());
    }

    private Set<Artifact> getProjectDepedencies() {
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

    class DependencyFlatDumper implements DependencyVisitor, Iterable<Artifact> {

        private List<Artifact> flatDependencyList = new ArrayList<Artifact>();

        public boolean visitEnter(DependencyNode node) {
            Artifact a = node.getArtifact();
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
