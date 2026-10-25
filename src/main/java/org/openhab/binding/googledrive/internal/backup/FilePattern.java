/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.googledrive.internal.backup;

import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link FilePattern} matches file names against a simple glob pattern ({@code *} and {@code ?}), independent
 * of the operating system. It is used for local files and for file names in Google Drive.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public class FilePattern {

    private final Pattern regex;

    public FilePattern(String glob) {
        String pattern = glob.isBlank() ? "*" : glob.trim();
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*' || c == '?') {
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
        }
        this.regex = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public boolean matches(String fileName) {
        return regex.matcher(fileName).matches();
    }
}
