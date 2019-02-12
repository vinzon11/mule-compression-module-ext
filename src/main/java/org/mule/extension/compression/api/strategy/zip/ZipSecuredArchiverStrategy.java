/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.compression.api.strategy.zip;

import java.io.InputStream;
import java.util.Map;

import javax.inject.Inject;

import org.mule.extension.compression.api.strategy.SecuredArchiverStrategy;
import org.mule.extension.compression.internal.CompressionManager;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.runtime.operation.Result;

/**
 * Zip format archiver
 *
 * @since 2.0
 */
@DisplayName("Zip")
@Alias("zip-secured-archiver")
public class ZipSecuredArchiverStrategy implements SecuredArchiverStrategy {

  @Inject
  private CompressionManager compressionManager;

  /**
   * {@inheritDoc}
   */
  @Override
  public Result<InputStream, Void> archive(Map<String, TypedValue<InputStream>> entries, String password) {
    return compressionManager.asyncSecuredArchive(entries, password);
  }
}
