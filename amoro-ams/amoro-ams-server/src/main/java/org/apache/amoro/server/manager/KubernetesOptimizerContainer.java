/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.amoro.server.manager;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.amoro.api.resource.Resource;
import org.apache.commons.io.IOUtils;
import org.apache.curator.shaded.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/** Kubernetes Optimizer Container with Standalone Optimizer */
public class KubernetesOptimizerContainer extends AbstractResourceContainer {

  private static final Logger LOG = LoggerFactory.getLogger(KubernetesOptimizerContainer.class);

  public static final String MEMORY_PROPERTY = "memory";
  public static final String CPU_FACTOR_PROPERTY = "cpu.factor";
  public static final String NAMESPACE = "namespace";
  public static final String IMAGE = "image";
  public static final String KUBE_CONFIG_PATH = "kube-config-path";

  private static final String NAME_PREFIX = "amoro-optimizer-";

  private static final String KUBERNETES_NAME_PROPERTIES = "name";

  private KubernetesClient client;

  @Override
  public void init(String name, Map<String, String> containerProperties) {
    super.init(name, containerProperties);
    // start k8s job using k8s client
    String kubeConfigPath = checkAndGetProperty(containerProperties, KUBE_CONFIG_PATH);
    Config config = Config.fromKubeconfig(getKubeConfigContent(kubeConfigPath));
    this.client = new KubernetesClientBuilder().withConfig(config).build();
  }

  @Override
  protected Map<String, String> doScaleOut(Resource resource) {
    Map<String, String> groupProperties = Maps.newHashMap();
    groupProperties.putAll(getContainerProperties());
    groupProperties.putAll(resource.getProperties());

    // generate pod start args
    long memoryPerThread = Long.parseLong(checkAndGetProperty(groupProperties, MEMORY_PROPERTY));
    long memory = memoryPerThread * resource.getThreadCount();
    // point at amoro home in docker image
    String startUpArgs =
        String.format(
            "/entrypoint.sh optimizer %s %s",
            memory, super.buildOptimizerStartupArgsString(resource));
    LOG.info("Starting k8s optimizer using k8s client with start command : {}", startUpArgs);

    String namespace = groupProperties.getOrDefault(NAMESPACE, "default");
    String image = checkAndGetProperty(groupProperties, IMAGE);
    String cpuLimitFactorString = groupProperties.getOrDefault(CPU_FACTOR_PROPERTY, "1.0");
    double cpuLimitFactor = Double.parseDouble(cpuLimitFactorString);
    int cpuLimit = (int) (Math.ceil(cpuLimitFactor * resource.getThreadCount()));

    String resourceId = resource.getResourceId();
    String groupName = resource.getGroupName();
    String kubernetesName = NAME_PREFIX + resourceId;
    Deployment deployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withName(NAME_PREFIX + resourceId)
            .endMetadata()
            .withNewSpec()
            .withReplicas(1)
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", NAME_PREFIX + resourceId)
            .addToLabels("AmoroOptimizerGroup", groupName)
            .addToLabels("AmoroResourceId", resourceId)
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withName("optimizer")
            .withImage(image)
            .withCommand("sh", "-c", startUpArgs)
            .withResources(
                new ResourceRequirementsBuilder()
                    .withLimits(
                        ImmutableMap.of(
                            "memory",
                            new Quantity(memory + "Mi"),
                            "cpu",
                            new Quantity(cpuLimit + "")))
                    .withRequests(
                        ImmutableMap.of(
                            "memory",
                            new Quantity(memory + "Mi"),
                            "cpu",
                            new Quantity(cpuLimit + "")))
                    .build())
            .endContainer()
            .endSpec()
            .endTemplate()
            .withNewSelector()
            .addToMatchLabels("app", NAME_PREFIX + resourceId)
            .endSelector()
            .endSpec()
            .build();
    client.apps().deployments().inNamespace(namespace).resource(deployment).create();
    Map<String, String> startupProperties = Maps.newHashMap();
    startupProperties.put(NAMESPACE, namespace);
    startupProperties.put(KUBERNETES_NAME_PROPERTIES, kubernetesName);
    return startupProperties;
  }

  @Override
  public void releaseOptimizer(Resource resource) {
    String resourceId = resource.getResourceId();
    LOG.info("release Kubernetes Optimizer Container {}", resourceId);
    String namespace = resource.getProperties().get(NAMESPACE);
    String name = resource.getProperties().get(KUBERNETES_NAME_PROPERTIES);
    client.apps().deployments().inNamespace(namespace).withName(name).delete();
  }

  private static String checkAndGetProperty(Map<String, String> properties, String key) {
    Preconditions.checkState(
        properties != null && properties.containsKey(key), "Cannot find %s in properties", key);
    return properties.get(key);
  }

  private String getKubeConfigContent(String path) {
    try {
      return IOUtils.toString(Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
