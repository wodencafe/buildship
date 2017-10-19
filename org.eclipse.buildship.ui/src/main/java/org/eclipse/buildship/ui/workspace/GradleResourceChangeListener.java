/*
 * Copyright (c) 2016 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.buildship.ui.workspace;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.buildship.ui.UiPluginConstants;

/**
 * An {@link IResourceChangeListener} implementation which requests refresh of the
 * project when important Gradle files change, via {@link CorePlugin#listenerRegistry()}.
 *
 * @author Christopher Bryan Boyd
 *
 */
public class GradleResourceChangeListener implements IResourceChangeListener {

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        IResourceDelta delta = event.getDelta();
        try {
            final AtomicBoolean isGradleFile = new AtomicBoolean();
            IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) {
                   //only interested in changed resources (not added or removed)
                   if (delta.getKind() != IResourceDelta.CHANGED) {
                    return true;
                }
                   //only interested in content changes
                   if ((delta.getFlags() & IResourceDelta.CONTENT) == 0) {
                    return true;
                }
                   IResource resource = delta.getResource();
                   //only interested in files with the "txt" extension
                   if (resource.getType() == IResource.FILE) {
                       IProject project = resource.getProject();
                       if (GradleProjectNature.isPresentOn(project)) {
                           if ( resource instanceof IProject || "gradle".equals(resource.getFileExtension()) || "gradle.properties".equals(resource.getName())) {
                               isGradleFile.set(true);
                               return false;
                           }

                       }

                   }
                   return true;
                }
             };
            System.out.println("foobar");
            try {
                delta.accept(visitor);
             } catch (CoreException e) {
                //open error dialog with syncExec or print to plugin log file
             }
             if (isGradleFile.get()) {
                 getHandlerService().executeCommand(UiPluginConstants.REFRESH_PROJECT_COMMAND_ID, null);
             }



        } catch (Throwable e) {
            CorePlugin.logger().warn("Failed to detect project changes", e);
        }
    }
    public static GradleResourceChangeListener createAndRegister() {
        GradleResourceChangeListener listener = new GradleResourceChangeListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
        return listener;
    }

    public void close() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
    }

    @SuppressWarnings({"cast", "RedundantCast"})
    private IHandlerService getHandlerService() {
        return (IHandlerService) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class);
    }
}
