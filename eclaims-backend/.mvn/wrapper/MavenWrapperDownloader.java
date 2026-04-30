/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Minimal Maven Wrapper downloader.
 *
 * Kept in-repo so the wrapper can bootstrap itself (download maven-wrapper.jar)
 * without requiring a global Maven installation.
 */
public class MavenWrapperDownloader {

    private static final String WRAPPER_PROPERTIES = ".mvn/wrapper/maven-wrapper.properties";
    private static final String WRAPPER_JAR_PATH = ".mvn/wrapper/maven-wrapper.jar";
    private static final String WRAPPER_URL_PROPERTY = "wrapperUrl";
    private static final String DEFAULT_WRAPPER_URL =
            "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar";

    public static void main(String[] args) throws Exception {
        final Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        final Path propertiesPath = projectDir.resolve(WRAPPER_PROPERTIES);
        final Path jarPath = projectDir.resolve(WRAPPER_JAR_PATH);

        final String wrapperUrl = readWrapperUrl(propertiesPath);

        Files.createDirectories(jarPath.getParent());
        if (Files.exists(jarPath)) {
            // already present
            return;
        }

        downloadTo(wrapperUrl, jarPath.toFile());
    }

    private static String readWrapperUrl(Path propertiesPath) {
        if (!Files.exists(propertiesPath)) {
            return DEFAULT_WRAPPER_URL;
        }
        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            props.load(in);
        } catch (IOException e) {
            return DEFAULT_WRAPPER_URL;
        }
        final String url = props.getProperty(WRAPPER_URL_PROPERTY);
        return (url == null || url.trim().isEmpty()) ? DEFAULT_WRAPPER_URL : url.trim();
    }

    private static void downloadTo(String urlString, File destination) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);

        final int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("Failed to download " + urlString + " (HTTP " + code + ")");
        }

        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) >= 0) {
                out.write(buf, 0, r);
            }
        }
    }
}

