/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.compression.internal;

import static java.util.Collections.singletonMap;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

import java.io.InputStream;

import org.mule.extension.compression.api.strategy.SecuredArchiverStrategy;
import org.mule.extension.compression.internal.error.exception.CompressionException;
import org.mule.extension.compression.internal.error.providers.ArchiveErrorProvider;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.dsl.xml.ParameterDsl;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.Content;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.operation.Result;

/**
 * Secured archive operations
 *
 * @since 2.0
 */
public class SecuredArchivingOperations {

  /**
   * Compresses a given content using and secure it with password
   * <p>
   * If a problem occur while compressing the content a COULD_NOT_COMPRESS error will be thrown.
   *
   * @param content the content to be compressed
   * @param archiver the archiving strategy
   * @param filename the name of the file
   * @param password the password to protect the compressed file
   */
  @MediaType(value = ANY, strict = false)
  @Summary("Compresses and protect with password a set of entries into a new file in the specified archive format")
  @Throws(ArchiveErrorProvider.class)
  public Result<InputStream, Void> secure(@Content TypedValue<InputStream> content,
                                          @ParameterDsl(
                                              allowReferences = false) @Expression(NOT_SUPPORTED) SecuredArchiverStrategy secureArchiver,
                                          @DisplayName("Filename") String filename, @DisplayName("Password") String password) {

    if (content.getValue() == null) {
      throw new CompressionException("cannot compress null content");
    }

    if (filename == null) {
      filename = "data";
    }

    return secureArchiver.archive(singletonMap(filename, content), password);
  }
}
