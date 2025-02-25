/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.model.test;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.wildfly.common.xml.XMLInputFactoryUtil;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
final class MavenSettings {
    private static final Object settingLoaderMutex = new Object();

    private static volatile MavenSettings mavenSettings;

    private Path localRepository = null;
    private final List<String> remoteRepositories = new LinkedList<>();
    private final Map<String, Profile> profiles = new HashMap<>();
    private final List<String> activeProfileNames = new LinkedList<>();

    MavenSettings() {
        configureDefaults();
    }

    static MavenSettings getSettings() {
        if (mavenSettings != null) {
            return mavenSettings;
        }
        synchronized (settingLoaderMutex) {
            if (mavenSettings != null) {
                return mavenSettings;
            }

            return mavenSettings = doIo(() -> {
                MavenSettings settings = new MavenSettings();

                Path m2 = Paths.get(System.getProperty("user.home"), ".m2");
                Path settingsPath = m2.resolve("settings.xml");

                if (Files.notExists(settingsPath)) {
                    String mavenHome = System.getenv("M2_HOME");
                    if (mavenHome != null) {
                        settingsPath = Paths.get(mavenHome, "conf", "settings.xml");
                    }
                }
                if (Files.exists(settingsPath)) {
                    parseSettingsXml(settingsPath, settings);
                }
                if (settings.getLocalRepository() == null) {
                    Path repository = m2.resolve("repository");
                    settings.setLocalRepository(repository);
                }
                settings.resolveActiveSettings();
                return settings;
            });
        }
    }

    private static <T> T doIo(PrivilegedExceptionAction<T> action) throws RuntimeException {
        try {
            return AccessController.doPrivileged(action);
        } catch (PrivilegedActionException e) {
            try {
                throw e.getCause();
            } catch (IOException | RuntimeException | Error e1) {
                throw new RuntimeException(e1);
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }
    }

    static MavenSettings parseSettingsXml(Path settings, MavenSettings mavenSettings) throws IOException {
        try {


            //reader.setFeature(FEATURE_PROCESS_NAMESPACES, false);
            InputStream source = Files.newInputStream(settings, StandardOpenOption.READ);
            XMLStreamReader reader = XMLInputFactoryUtil.create().createXMLStreamReader(source);

            int eventType;
            while ((eventType = reader.next()) != END_DOCUMENT) {
                switch (eventType) {
                    case START_ELEMENT: {
                        switch (reader.getLocalName()) {
                            case "settings": {
                                parseSettings(reader, mavenSettings);
                                break;
                            }
                        }
                    }
                    default: {
                        break;
                    }
                }
            }
            return mavenSettings;
        } catch (XMLStreamException e) {
            throw new IOException("Could not parse maven settings.xml", e);
        }

    }

    private static void parseSettings(final XMLStreamReader reader, MavenSettings mavenSettings) throws XMLStreamException, IOException {
        int eventType;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            switch (eventType) {
                case END_ELEMENT: {
                    return;
                }
                case START_ELEMENT: {
                    switch (reader.getLocalName()) {
                        case "localRepository": {
                            String localRepository = reader.getElementText();
                            if (localRepository != null && !localRepository.trim().isEmpty()) {
                                mavenSettings.setLocalRepository(Paths.get(localRepository));
                            }
                            break;
                        }
                        case "profiles": {
                            while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                                if (eventType == START_ELEMENT) {
                                    switch (reader.getLocalName()) {
                                        case "profile": {
                                            parseProfile(reader, mavenSettings);
                                            break;
                                        }
                                    }
                                } else {
                                    break;
                                }
                            }
                            break;
                        }
                        case "activeProfiles": {
                            while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                                if (eventType == START_ELEMENT) {
                                    switch (reader.getLocalName()) {
                                        case "activeProfile": {
                                            mavenSettings.addActiveProfile(reader.getElementText());
                                            break;
                                        }
                                    }
                                } else {
                                    break;
                                }

                            }
                            break;
                        }
                        default: {
                            skip(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw new XMLStreamException("Unexpected content", reader.getLocation());
                }
            }
        }
        throw new XMLStreamException("Unexpected end of document", reader.getLocation());
    }

    private static void parseProfile(final XMLStreamReader reader, MavenSettings mavenSettings) throws XMLStreamException, IOException {
        int eventType;
        Profile profile = new Profile();
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "id": {
                        profile.setId(reader.getElementText());
                        break;
                    }
                    case "repositories": {
                        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
                            if (eventType == START_ELEMENT) {
                                switch (reader.getLocalName()) {
                                    case "repository": {
                                        parseRepository(reader, profile);
                                        break;
                                    }
                                }
                            } else {
                                break;
                            }

                        }
                        break;
                    }
                    default: {
                        skip(reader);
                    }
                }
            } else {
                break;
            }
        }
        mavenSettings.addProfile(profile);
    }

    private static void parseRepository(final XMLStreamReader reader, Profile profile) throws XMLStreamException, IOException {
        int eventType;
        while ((eventType = reader.nextTag()) != END_DOCUMENT) {
            if (eventType == START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "url": {
                        profile.addRepository(reader.getElementText());
                        break;
                    }
                    default: {
                        skip(reader);
                    }
                }
            } else {
                break;
            }

        }
    }

    private static void skip(XMLStreamReader parser) throws XMLStreamException, IOException {
        if (parser.getEventType() != XMLStreamReader.START_ELEMENT) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XMLStreamReader.END_ELEMENT:
                    depth--;
                    break;
                case XMLStreamReader.START_ELEMENT:
                    depth++;
                    break;
            }
        }
    }

    private void configureDefaults() {
        //always add maven central
        remoteRepositories.add("https://repo1.maven.org/maven2/");
        String localRepositoryPath = System.getProperty("localRepository");
        if (localRepositoryPath != null && !localRepositoryPath.trim().isEmpty()) {
            localRepository = Paths.get(localRepositoryPath);
        }
        localRepositoryPath = System.getProperty("maven.repo.local");
        if (localRepositoryPath != null && !localRepositoryPath.trim().isEmpty()) {
            localRepository = Paths.get(localRepositoryPath);
        }
        String remoteRepository = System.getProperty("remote.maven.repo");
        if (remoteRepository != null) {
            for (String repo : remoteRepository.split(",")) {
                if (!repo.endsWith("/")) {
                    repo += "/";
                }
                remoteRepositories.add(repo);
            }
        }
    }

    private void setLocalRepository(Path localRepository) {
        this.localRepository = localRepository;
    }

    Path getLocalRepository() {
        return localRepository;
    }

    List<String> getRemoteRepositories() {
        return remoteRepositories;
    }

    private void addProfile(Profile profile) {
        this.profiles.put(profile.getId(), profile);
    }

    private void addActiveProfile(String profileName) {
        activeProfileNames.add(profileName);
    }

    private void resolveActiveSettings() {
        for (String name : activeProfileNames) {
            Profile p = profiles.get(name);
            if (p != null) {
                remoteRepositories.addAll(p.getRepositories());
            }
        }
    }


    static final class Profile {
        private String id;
        final List<String> repositories = new LinkedList<>();

        Profile() {

        }

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        void addRepository(String url) {
            if (!url.endsWith("/")) {
                url += "/";
            }
            repositories.add(url);
        }

        List<String> getRepositories() {
            return repositories;
        }
    }
}
