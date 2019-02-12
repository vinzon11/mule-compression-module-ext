/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.compression.api.strategy;

import org.mule.extension.compression.internal.error.exception.CompressionException;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.InputStream;
import java.util.Map;

/**
 * Provides the capability to archive a set of entries into a single archive in some compression format.
 *
 * @since 2.0
 */
public interface SecuredArchiverStrategy {

  /**
   * Receives a map with all the contents that needs to be compressed, creates a new archive with those contents and password then
   * returns the created archive as an InputStream
   */

  Result<InputStream, Void> archive(Map<String, TypedValue<InputStream>> entries, String password)
      throws CompressionException;
}
