/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.configuration.internal;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import com.gradleware.tooling.toolingclient.GradleDistribution;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.configuration.BuildConfiguration;
import org.eclipse.buildship.core.configuration.ConfigurationManager;
import org.eclipse.buildship.core.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.configuration.RunConfiguration;
import org.eclipse.buildship.core.configuration.WorkspaceConfiguration;
import org.eclipse.buildship.core.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.core.util.file.RelativePathUtils;

/**
 * Default implementation for {@link ConfigurationManager}.
 */
public class DefaultConfigurationManager implements ConfigurationManager {

    WorkspaceConfigurationPersistence workspaceConfigurationPersistence = new WorkspaceConfigurationPersistence();
    BuildConfigurationPersistence buildConfigurationPersistence = new BuildConfigurationPersistence();

    @Override
    public WorkspaceConfiguration loadWorkspaceConfiguration() {
        return this.workspaceConfigurationPersistence.readWorkspaceConfig();
    }

    @Override
    public void saveWorkspaceConfiguration(WorkspaceConfiguration config) {
        this.workspaceConfigurationPersistence.saveWorkspaceConfiguration(config);
    }

    @Override
    public BuildConfiguration createBuildConfiguration(
            File rootProjectDirectory, boolean overrideWorkspaceSettings, GradleDistribution gradleDistribution,
            File gradleUserHome,
            boolean buildScansEnabled, boolean offlineMode, boolean autoRefresh) {
        DefaultBuildConfigurationProperties persistentBuildConfigProperties = new DefaultBuildConfigurationProperties(rootProjectDirectory,
                                                                                                        gradleDistribution,
                                                                                                        gradleUserHome,
                                                                                                        overrideWorkspaceSettings,
                                                                                                        buildScansEnabled,
                                                                                                        offlineMode,
                                                                                                        autoRefresh);
        return new DefaultBuildConfiguration(persistentBuildConfigProperties, loadWorkspaceConfiguration());
    }

    @Override
    public BuildConfiguration loadBuildConfiguration(File rootDir) {
        Preconditions.checkNotNull(rootDir);
        Preconditions.checkArgument(rootDir.exists());
        Optional<IProject> projectCandidate = CorePlugin.workspaceOperations().findProjectByLocation(rootDir);
        DefaultBuildConfigurationProperties buildConfigProperties;
        if (projectCandidate.isPresent() && projectCandidate.get().isAccessible()) {
            IProject project = projectCandidate.get();
            try {
                buildConfigProperties = this.buildConfigurationPersistence.readBuildConfiguratonProperties(project);
            } catch (Exception e) {
                // when the project is being imported, the configuration file might not be visible from the
                // Eclipse resource API; in that case we fall back to raw IO operations
                // a similar approach is used in JDT core to load the .classpath file
                // see org.eclipse.jdt.internal.core.JavaProject.readFileEntriesWithException(Map)
                buildConfigProperties = this.buildConfigurationPersistence.readBuildConfiguratonProperties(project.getLocation().toFile());
            }
        } else {
            buildConfigProperties = this.buildConfigurationPersistence.readBuildConfiguratonProperties(rootDir);
        }
        return new DefaultBuildConfiguration(buildConfigProperties, loadWorkspaceConfiguration());
    }

    @Override
    public void saveBuildConfiguration(BuildConfiguration configuration) {
        Preconditions.checkArgument(configuration instanceof DefaultBuildConfiguration, "Unknow configuration type: ", configuration.getClass());
        DefaultBuildConfigurationProperties properties = ((DefaultBuildConfiguration)configuration).getProperties();
        File rootDir = configuration.getRootProjectDirectory();
        Optional<IProject> rootProject = CorePlugin.workspaceOperations().findProjectByLocation(rootDir);
        if (rootProject.isPresent() && rootProject.get().isAccessible()) {
            this.buildConfigurationPersistence.saveBuildConfiguration(rootProject.get(), properties);
        } else {
            this.buildConfigurationPersistence.saveBuildConfiguration(rootDir, properties);
        }
    }

    @Override
    public ProjectConfiguration createProjectConfiguration(BuildConfiguration configuration, File projectDir) {
        return new DefaultProjectConfiguration(projectDir, configuration);
    }

    @Override
    public ProjectConfiguration loadProjectConfiguration(IProject project) {
        String pathToRoot = this.buildConfigurationPersistence.readPathToRoot(project.getLocation().toFile());
        File rootDir = relativePathToProjectRoot(project.getLocation(), pathToRoot);
        BuildConfiguration buildConfig = loadBuildConfiguration(rootDir);
        return new DefaultProjectConfiguration(project.getLocation().toFile(), buildConfig);
    }

    @Override
    public ProjectConfiguration tryLoadProjectConfiguration(IProject project) {
        try {
            return loadProjectConfiguration(project);
        } catch(RuntimeException e) {
            CorePlugin.logger().debug("Cannot load configuration for project " + project.getName(), e);
            return null;
        }
    }

    private ProjectConfiguration loadProjectConfiguration(File projectDir) {
        String pathToRoot = this.buildConfigurationPersistence.readPathToRoot(projectDir);
        File rootDir = relativePathToProjectRoot(new Path(projectDir.getAbsolutePath()), pathToRoot);
        BuildConfiguration buildConfig = loadBuildConfiguration(rootDir);
        return new DefaultProjectConfiguration(canonicalize(projectDir), buildConfig);
    }

    @Override
    public void saveProjectConfiguration(ProjectConfiguration projectConfiguration) {
        BuildConfiguration buildConfiguration = projectConfiguration.getBuildConfiguration();
        File projectDir = projectConfiguration.getProjectDir();
        File rootDir = buildConfiguration.getRootProjectDirectory();
        String pathToRoot = projectRootToRelativePath(projectDir, rootDir);

        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(projectDir);
        if (project.isPresent() && project.get().isAccessible()) {
            this.buildConfigurationPersistence.savePathToRoot(project.get(), pathToRoot);
        } else {
            this.buildConfigurationPersistence.savePathToRoot(projectDir, pathToRoot);
        }
        saveBuildConfiguration(buildConfiguration);
    }

    @Override
    public void deleteProjectConfiguration(IProject project) {
        if (project.isAccessible()) {
            this.buildConfigurationPersistence.deletePathToRoot(project);
        } else {
            this.buildConfigurationPersistence.deletePathToRoot(project.getLocation().toFile());
        }
    }

    @Override
    public RunConfiguration loadRunConfiguration(ILaunchConfiguration launchConfiguration) {
        GradleRunConfigurationAttributes attributes = GradleRunConfigurationAttributes.from(launchConfiguration);
        ProjectConfiguration projectConfiguration;
        try {
            projectConfiguration = loadProjectConfiguration(attributes.getWorkingDir());
        } catch (Exception e) {
            CorePlugin.logger().debug("Can't load build config from " + attributes.getWorkingDir(), e);
            DefaultBuildConfigurationProperties buildConfigProperties = new DefaultBuildConfigurationProperties(attributes.getWorkingDir(),
                    attributes.getGradleDistribution(),
                    attributes.getGradleUserHome(),
                    attributes.isOverrideBuildSettings(),
                    attributes.isBuildScansEnabled(),
                    attributes.isOffline(),
                    attributes.isAutoRefresh());
            BuildConfiguration buildConfiguration = new DefaultBuildConfiguration(buildConfigProperties, loadWorkspaceConfiguration());
            projectConfiguration = new DefaultProjectConfiguration(canonicalize(attributes.getWorkingDir()), buildConfiguration);
        }
        RunConfigurationProperties runConfigProperties = new RunConfigurationProperties(attributes.getTasks(),
                  attributes.getGradleDistribution(),
                  attributes.getGradleUserHome(),
                  attributes.getJavaHome(),
                  attributes.getJvmArguments(),
                  attributes.getArguments(),
                  attributes.isShowConsoleView(),
                  attributes.isShowExecutionView(),
                  attributes.isOverrideBuildSettings(),
                  attributes.isBuildScansEnabled(),
                  attributes.isOffline(),
                  attributes.isAutoRefresh());
        return new DefaultRunConfiguration(projectConfiguration, runConfigProperties);
    }

    @Override
    public RunConfiguration createDefaultRunConfiguration(BuildConfiguration configuration) {
        return createRunConfiguration(configuration,
                Collections.<String>emptyList(),
                null,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                false,
                false,
                false,
                GradleDistribution.fromBuild(),
                null,
                false,
                false,
                false);
    }

    @Override
    public RunConfiguration createRunConfiguration(BuildConfiguration buildConfiguration, List<String> tasks, File javaHome, List<String> jvmArguments, List<String> arguments, boolean showConsoleView,
            boolean showExecutionsView, boolean overrideBuildSettings, GradleDistribution gradleDistribution, File gradleUserHome, boolean buildScansEnabled, boolean offlineMode, boolean autoRefresh) {
        Preconditions.checkArgument(buildConfiguration instanceof DefaultBuildConfiguration, "Unknow configuration type: ", buildConfiguration.getClass());
        ProjectConfiguration projectConfiguration = new DefaultProjectConfiguration(buildConfiguration.getRootProjectDirectory(), buildConfiguration);
        RunConfigurationProperties runConfig = new RunConfigurationProperties(tasks,
                gradleDistribution,
                gradleUserHome,
                javaHome,
                jvmArguments,
                arguments,
                showConsoleView,
                showExecutionsView,
                overrideBuildSettings,
                buildScansEnabled,
                offlineMode,
                autoRefresh);
        return new DefaultRunConfiguration(projectConfiguration, runConfig);
    }

    private static File relativePathToProjectRoot(IPath projectPath, String path) {
        IPath pathToRoot = new Path(path);
        IPath absolutePathToRoot = pathToRoot.isAbsolute() ? pathToRoot : RelativePathUtils.getAbsolutePath(projectPath, pathToRoot);
        return canonicalize(absolutePathToRoot.toFile());
    }

    private static File canonicalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String projectRootToRelativePath(File projectDir, File rootDir) {
        IPath rootProjectPath = new Path(rootDir.getPath());
        IPath projectPath = new Path(projectDir.getPath());
        return RelativePathUtils.getRelativePath(projectPath, rootProjectPath).toPortableString();
    }
}
