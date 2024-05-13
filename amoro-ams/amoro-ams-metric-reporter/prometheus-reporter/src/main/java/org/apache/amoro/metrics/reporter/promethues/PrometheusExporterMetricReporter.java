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

package org.apache.amoro.metrics.reporter.promethues;

import io.prometheus.client.exporter.HTTPServer;
import org.apache.amoro.api.metrics.MetricReporter;
import org.apache.amoro.api.metrics.MetricSet;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** Prometheus exporter */
public class PrometheusExporterMetricReporter implements MetricReporter {

  public static final String PORT = "port";

  private int port;
  private HTTPServer server;

  @Override
  public void open(Map<String, String> properties) {
    this.port =
        Optional.ofNullable(properties.get(PORT))
            .map(Integer::valueOf)
            .orElseThrow(() -> new IllegalArgumentException("Lack required property: " + PORT));

    try {
      this.server = new HTTPServer(this.port);
    } catch (IOException e) {
      throw new RuntimeException("Start prometheus exporter server failed.", e);
    }
  }

  @Override
  public void close() {
    this.server.close();
  }

  @Override
  public String name() {
    return "prometheus-exporter";
  }

  @Override
  public void setGlobalMetricSet(MetricSet globalMetricSet) {
    MetricCollector collector = new MetricCollector(globalMetricSet);
    collector.register();
  }
}
