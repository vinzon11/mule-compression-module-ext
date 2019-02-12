/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.compression.internal;

import static java.lang.System.getProperty;
import static org.mule.extension.compression.internal.CompressionExtension.ZIP_MEDIA_TYPE;
import static org.mule.runtime.api.metadata.DataType.INPUT_STREAM;
import static org.mule.runtime.core.api.util.FileUtils.copyStreamToFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.Random;

import javax.inject.Inject;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.mule.extension.compression.internal.error.exception.CompressionException;
import org.mule.extension.compression.internal.zip.TempZipFile;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.transformation.TransformationService;
import org.mule.runtime.extension.api.runtime.operation.Result;

import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

/**
 * Manages resources necessary for performing compression operations
 *
 * @since 2.0.2
 */
public class CompressionManager implements Startable, Stoppable {

  private static final File TEMP_DIR = new File(getProperty("java.io.tmpdir"));
  private static final Random RANDOM = new Random();

  @Inject
  private SchedulerService schedulerService;

  @Inject
  private TransformationService transformationService;

  private Scheduler compressionScheduler;

  @Override
  public void start() throws MuleException {
    compressionScheduler = schedulerService.cpuIntensiveScheduler();
  }

  @Override
  public void stop() throws MuleException {
    compressionScheduler.stop();
    compressionScheduler = null;
  }

  public Result<InputStream, Void> asyncArchive(Map<String, TypedValue<InputStream>> entries) {
    try {
      PipedInputStream pipe = new PipedInputStream();
      PipedOutputStream out = new PipedOutputStream(pipe);

      compressionScheduler.submit(() -> archive(entries, out));

      return Result.<InputStream, Void>builder()
          .output(pipe)
          .mediaType(ZIP_MEDIA_TYPE)
          .build();
    } catch (CompressionException e) {
      throw e;
    } catch (Throwable t) {
      throw new CompressionException(t);
    }
  }

  public Result<InputStream, Void> asyncSecuredArchive(Map<String, TypedValue<InputStream>> entries, String password) {
    try {
      PipedInputStream pipe = new PipedInputStream();
      PipedOutputStream out = new PipedOutputStream(pipe);

      compressionScheduler.submit(() -> securedArchive(entries, out, password));

      return Result.<InputStream, Void>builder()
          .output(pipe)
          .mediaType(ZIP_MEDIA_TYPE)
          .build();
    } catch (CompressionException e) {
      throw e;
    } catch (Throwable t) {
      throw new CompressionException(t);
    }
  }

  /**
   * Receives a ZIP content and creates a temporal physical {@link TempZipFile file} for it
   *
   * @param inputStream the zip content
   * @return a {@link TempZipFile}
   * @throws IOException in case of IO issues
   */
  public TempZipFile toTempZip(InputStream inputStream) throws IOException {
    return new TempZipFile(toTempFile(inputStream));
  }

  private File toTempFile(InputStream inputStream) throws IOException {
    String fileName = "mule-compression-buffer" + RANDOM.nextLong() + ".tmp";
    File file = new File(TEMP_DIR, fileName);
    copyStreamToFile(inputStream, file);

    return file;
  }

  /**
   * Creates an archive of the given entries
   *
   * @param entries the entries to archive
   * @param out the {@link OutputStream} in which the compressed content is going to be written
   */
  private void archive(Map<String, TypedValue<InputStream>> entries, OutputStream out) {
    try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
      entries.forEach((name, content) -> addEntry(zip, name, content, transformationService));
    } catch (IOException e) {
      throw new CompressionException(e);
    }
  }


  private void securedArchive(Map<String, TypedValue<InputStream>> entries, OutputStream out, String password) {
    ZipOutputStream zipOutputStream = new ZipOutputStream(out);

    ZipParameters parameters = new ZipParameters();

    parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
    parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
    parameters.setEncryptFiles(true);
    parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD);

    parameters.setPassword(password);

    try (ZipOutputStream zip = zipOutputStream) {
      entries.forEach((name, content) -> addSecuredEntry(zip, name, content, parameters, transformationService));
    } catch (IOException e) {
      throw new CompressionException(e);
    }
  }

  private void addSecuredEntry(ZipOutputStream zip,
                               String name,
                               TypedValue<InputStream> entryContent, ZipParameters zipParameters,
                               TransformationService transformationService) {
    try {

      zipParameters.setSourceExternalStream(true);
      zipParameters.setFileNameInZip(name);

      zip.putNextEntry(null, zipParameters);

      byte[] buffer = new byte[1024];
      int length;
      InputStream content = getContent(name, entryContent, transformationService);

      while ((length = content.read(buffer)) >= 0) {
        zip.write(buffer, 0, length);
      }
      zip.closeEntry();
      zip.finish();
    } catch (Exception e) {
      throw new CompressionException(e);
    }
  }

  private void addEntry(ZipArchiveOutputStream zip,
                        String name,
                        TypedValue<InputStream> entryContent,
                        TransformationService transformationService) {
    try {
      ZipArchiveEntry newEntry = new ZipArchiveEntry(name);
      zip.putArchiveEntry(newEntry);

      byte[] buffer = new byte[1024];
      int length;
      InputStream content = getContent(name, entryContent, transformationService);

      while ((length = content.read(buffer)) >= 0) {
        zip.write(buffer, 0, length);
      }
      zip.closeArchiveEntry();
    } catch (Exception e) {
      throw new CompressionException(e);
    }
  }

  private InputStream getContent(String name, TypedValue<?> entryContent, TransformationService transformationService) {
    try {
      Object value = entryContent.getValue();
      if (value instanceof InputStream) {
        return (InputStream) value;
      }
      return (InputStream) transformationService.transform(value, entryContent.getDataType(), INPUT_STREAM);
    } catch (Exception e) {
      throw new CompressionException("cannot archive entry [" + name + "], content cannot be transformed to InputStream");
    }
  }

}
