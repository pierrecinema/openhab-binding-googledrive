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
package org.openhab.binding.googledrive.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link TokenProvider} supplies a valid OAuth2 access token for requests to the Drive API.
 *
 * @author Pierrecinema - Initial contribution
 */
@NonNullByDefault
public interface TokenProvider {

    /**
     * Returns an access token.
     *
     * @param forceRefresh true to request a new token from Google even if the stored one has not expired yet
     * @return the access token
     * @throws GoogleDriveAuthException if there is no login or Google no longer accepts it
     * @throws GoogleDriveException on communication errors
     */
    String getAccessToken(boolean forceRefresh) throws GoogleDriveException;
}
