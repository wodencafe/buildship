/*
 * Copyright (c) 2017 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.buildship.ui.console;

import java.util.regex.Pattern;

import com.google.common.base.Optional;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.console.ProcessDescription;
import org.eclipse.buildship.core.scan.BuildScanCreatedEvent;
import org.eclipse.buildship.core.util.string.PatternUtils;

/**
 * Finds build scan URL console output.
 *
 * @author Donat Csikos
 */
public final class BuildScanPatternMatchListener implements IPatternMatchListener {

    private GradleConsole console;

    @Override
    public void connect(TextConsole console) {
        this.console = (GradleConsole) console;
    }

    @Override
    public void disconnect() {
        this.console = null;
    }

    @Override
    public void matchFound(PatternMatchEvent event) {
        try {
            int offset = event.getOffset();
            int length = event.getLength();
            String buildScansInfo = this.console.getDocument().get(offset, length);
            String buildScanUrl = buildScansInfo.substring(buildScansInfo.indexOf("http"));
            Optional<ProcessDescription> description = this.console.getProcessDescription();
            if (description.isPresent()) {
                CorePlugin.listenerRegistry().dispatch(new BuildScanCreatedEvent(buildScanUrl, description.get()));
            }
        } catch (BadLocationException e) {
        }
    }

    @Override
    public String getPattern() {
        return "Publishing build [information|scan].*\\s+" + PatternUtils.WEB_URL_PATTERN;
    }

    @Override
    public int getCompilerFlags() {
        return Pattern.MULTILINE;
    }

    @Override
    public String getLineQualifier() {
        return null;
    }
}
