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

package com.netease.arctic;

import com.netease.arctic.ams.api.CatalogMeta;
import com.netease.arctic.ams.api.NoSuchObjectException;
import com.netease.arctic.ams.api.properties.CatalogMetaProperties;

import java.util.Map;
import java.util.function.Supplier;

public class UnifiedCatalogLoader {

  public static UnifiedCatalog loadUnifiedCatalog(
      String amsUri, String catalogName, Map<String, String> props) {
    AmsClient client = new PooledAmsClient(amsUri);
    Supplier<CatalogMeta> metaSupplier =
        () -> {
          try {
            CatalogMeta meta = client.getCatalog(catalogName);
            meta.putToCatalogProperties(CatalogMetaProperties.AMS_URI, amsUri);
            return meta;
          } catch (NoSuchObjectException e) {
            throw new IllegalStateException(
                "catalog not found, please check catalog name:" + catalogName, e);
          } catch (Exception e) {
            throw new IllegalStateException("failed when load catalog " + catalogName, e);
          }
        };

    return new CommonUnifiedCatalog(metaSupplier, props);
  }
}
