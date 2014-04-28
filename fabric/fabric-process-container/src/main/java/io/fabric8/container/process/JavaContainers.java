/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.container.process;

import io.fabric8.agent.download.DownloadManager;
import io.fabric8.agent.download.DownloadManagers;
import io.fabric8.agent.mvn.Parser;
import io.fabric8.agent.utils.AgentUtils;
import io.fabric8.api.FabricService;
import io.fabric8.api.Profile;
import io.fabric8.common.util.Objects;
import io.fabric8.deployer.dto.DependencyDTO;
import io.fabric8.deployer.dto.DtoHelper;
import io.fabric8.deployer.dto.ProjectRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

/**
 */
public class JavaContainers {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(JavaContainers.class);
    
    public static Map<String, Parser> getJavaContainerArtifacts(FabricService fabric, List<Profile> profileList, ExecutorService downloadExecutor) throws Exception {
        Map<String, Parser> artifacts = new TreeMap<String, Parser>();
        for (Profile profile : profileList) {
            DownloadManager downloadManager = DownloadManagers.createDownloadManager(fabric, profile, downloadExecutor);
            Map<String, Parser> profileArtifacts = AgentUtils.getProfileArtifacts(downloadManager, profile);
            artifacts.putAll(profileArtifacts);
            appendMavenDependencies(artifacts, profile);
        }
        return artifacts;
    }

    protected static void appendMavenDependencies(Map<String, Parser> artifacts, Profile profile) {
        List<String> configurationFileNames = profile.getConfigurationFileNames();
        for (String configurationFileName : configurationFileNames) {
            if (configurationFileName.startsWith("modules/") && configurationFileName.endsWith("-requirements.json")) {
                byte[] data = profile.getFileConfiguration(configurationFileName);
                try {
                    ProjectRequirements requirements = DtoHelper.getMapper().readValue(data, ProjectRequirements.class);
                    if (requirements != null) {
                        DependencyDTO rootDependency = requirements.getRootDependency();
                        if (rootDependency != null) {
                            addMavenDependencies(artifacts, rootDependency);
                        }
                    }

                } catch (IOException e) {
                    LOGGER.error("Failed to parse project requirements from " + configurationFileName + ". " + e, e);
                }
            }
        }
    }

    protected static void addMavenDependencies(Map<String, Parser> artifacts, DependencyDTO dependency) throws MalformedURLException {
        String url = dependency.toBundleUrl();
        Parser parser = Parser.parsePathWithSchemePrefix(url);
        String scope = dependency.getScope();
        if (!artifacts.containsKey(url) && !artifacts.containsValue(parser) && !(Objects.equal("test", scope))) {
            LOGGER.debug("Adding url: " + url + " parser: " + parser);
            artifacts.put(url, parser);
        }
        List<DependencyDTO> children = dependency.getChildren();
        if (children != null) {
            for (DependencyDTO child : children) {
                addMavenDependencies(artifacts, child);
            }
        }
    }

}