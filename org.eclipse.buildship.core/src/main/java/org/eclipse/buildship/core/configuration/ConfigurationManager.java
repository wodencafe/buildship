/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.buildship.core.configuration;

import java.io.File;
import java.util.List;

import com.gradleware.tooling.toolingclient.GradleDistribution;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchConfiguration;

/**
 * Defines how to access a hierarchy of preferences for the Gradle projects in the workspace.
 *
 * @author Donat Csikos
 */
public interface ConfigurationManager {

    WorkspaceConfiguration loadWorkspaceConfiguration();

    void saveWorkspaceConfiguration(WorkspaceConfiguration configuration);

    BuildConfiguration createBuildConfiguration(File rootProjectDirectory, boolean overrideWorkspaceSettings,
                                                GradleDistribution gradleDistribution, File gradleUserHome,
                                                boolean buildScansEnabled, boolean offlineMode, boolean autoRefresh);

    BuildConfiguration loadBuildConfiguration(File rootProject);

    void saveBuildConfiguration(BuildConfiguration configuration);

    ProjectConfiguration createProjectConfiguration(BuildConfiguration configuration, File projectDir);

    ProjectConfiguration tryLoadProjectConfiguration(IProject project);

    ProjectConfiguration loadProjectConfiguration(IProject project);

    void saveProjectConfiguration(ProjectConfiguration configuration);

    void deleteProjectConfiguration(IProject project);

    RunConfiguration loadRunConfiguration(ILaunchConfiguration launchConfiguration);

    RunConfiguration createDefaultRunConfiguration(BuildConfiguration configuration);

    RunConfiguration createRunConfiguration(BuildConfiguration configuration, List<String> tasks,
                                            File javaHome, List<String> jvmArguments,
                                            List<String> arguments, boolean showConsoleView,
                                            boolean showExecutionsView, boolean overrideBuildSettings,
                                            GradleDistribution gradleDistribution, File gradleUserHome,
                                            boolean buildScansEnabled, boolean offlineMode, boolean autoRefresh);
}
