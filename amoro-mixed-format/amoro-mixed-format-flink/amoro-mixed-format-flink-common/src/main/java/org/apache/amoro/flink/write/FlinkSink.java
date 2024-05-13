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

package org.apache.amoro.flink.write;

import static org.apache.amoro.flink.FlinkSchemaUtil.getPhysicalSchema;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_EMIT_FILE;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_EMIT_MODE;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_THROUGHPUT_METRIC_ENABLE;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_THROUGHPUT_METRIC_ENABLE_DEFAULT;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_WRITE_MAX_OPEN_FILE_SIZE;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.ARCTIC_WRITE_MAX_OPEN_FILE_SIZE_DEFAULT;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.AUTO_EMIT_LOGSTORE_WATERMARK_GAP;
import static org.apache.amoro.flink.table.descriptors.ArcticValidator.SUBMIT_EMPTY_SNAPSHOTS;
import static org.apache.amoro.table.TableProperties.WRITE_DISTRIBUTION_HASH_MODE;
import static org.apache.amoro.table.TableProperties.WRITE_DISTRIBUTION_HASH_MODE_DEFAULT;
import static org.apache.amoro.table.TableProperties.WRITE_DISTRIBUTION_MODE;
import static org.apache.amoro.table.TableProperties.WRITE_DISTRIBUTION_MODE_DEFAULT;
import static org.apache.flink.table.factories.FactoryUtil.SINK_PARALLELISM;

import org.apache.amoro.flink.metric.MetricsGenerator;
import org.apache.amoro.flink.shuffle.RoundRobinShuffleRulePolicy;
import org.apache.amoro.flink.shuffle.ShuffleHelper;
import org.apache.amoro.flink.shuffle.ShuffleKey;
import org.apache.amoro.flink.shuffle.ShuffleRulePolicy;
import org.apache.amoro.flink.table.ArcticTableLoader;
import org.apache.amoro.flink.table.descriptors.ArcticValidator;
import org.apache.amoro.flink.util.ArcticUtils;
import org.apache.amoro.flink.util.CompatibleFlinkPropertyUtil;
import org.apache.amoro.flink.util.IcebergClassUtil;
import org.apache.amoro.flink.util.ProxyUtil;
import org.apache.amoro.table.DistributionHashMode;
import org.apache.amoro.table.MixedTable;
import org.apache.amoro.table.TableProperties;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.sink.DiscardingSink;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.connector.ProviderContext;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.sink.TaskWriterFactory;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.time.Duration;
import java.util.Properties;

/**
 * An util generates arctic sink operator including log writer, file writer and file committer
 * operators.
 */
public class FlinkSink {
  private static final Logger LOG = LoggerFactory.getLogger(FlinkSink.class);

  public static final String FILES_COMMITTER_NAME = "FilesCommitter";

  public static Builder forRowData(DataStream<RowData> input) {
    return new Builder().forRowData(input);
  }

  public static class Builder {
    private DataStream<RowData> rowDataInput = null;
    private ProviderContext context;
    private MixedTable table;
    private ArcticTableLoader tableLoader;
    private TableSchema flinkSchema;
    private Properties producerConfig;
    private String topic;
    private boolean overwrite = false;
    private final String branch = SnapshotRef.MAIN_BRANCH;
    private DistributionHashMode distributionMode = null;

    private Builder() {}

    private Builder forRowData(DataStream<RowData> newRowDataInput) {
      this.rowDataInput = newRowDataInput;
      return this;
    }

    public Builder context(ProviderContext context) {
      this.context = context;
      return this;
    }

    public Builder table(MixedTable table) {
      this.table = table;
      return this;
    }

    public Builder flinkSchema(TableSchema flinkSchema) {
      this.flinkSchema = flinkSchema;
      return this;
    }

    public Builder producerConfig(Properties producerConfig) {
      this.producerConfig = producerConfig;
      return this;
    }

    public Builder topic(String topic) {
      this.topic = topic;
      return this;
    }

    public Builder tableLoader(ArcticTableLoader tableLoader) {
      this.tableLoader = tableLoader;
      return this;
    }

    public Builder overwrite(boolean overwrite) {
      this.overwrite = overwrite;
      return this;
    }

    public Builder distribute(DistributionHashMode distributionMode) {
      this.distributionMode = distributionMode;
      return this;
    }

    DataStreamSink<?> withEmit(
        DataStream<RowData> input,
        ArcticLogWriter logWriter,
        ArcticFileWriter fileWriter,
        OneInputStreamOperator<WriteResult, Void> committer,
        int writeOperatorParallelism,
        MetricsGenerator metricsGenerator,
        String arcticEmitMode) {
      SingleOutputStreamOperator writerStream =
          input
              .transform(
                  ArcticWriter.class.getName(),
                  TypeExtractor.createTypeInfo(WriteResult.class),
                  new ArcticWriter<>(logWriter, fileWriter, metricsGenerator))
              .name(String.format("ArcticWriter %s(%s)", table.name(), arcticEmitMode))
              .setParallelism(writeOperatorParallelism);

      if (committer != null) {
        writerStream =
            writerStream
                .transform(FILES_COMMITTER_NAME, Types.VOID, committer)
                .setParallelism(1)
                .setMaxParallelism(1);
      }

      return writerStream
          .addSink(new DiscardingSink<>())
          .name(String.format("ArcticSink %s", table.name()))
          .setParallelism(1);
    }

    public DataStreamSink<?> build() {
      Preconditions.checkNotNull(tableLoader, "table loader can not be null");
      initTableIfNeeded();

      Configuration config = new Configuration();
      table.properties().forEach(config::setString);

      RowType flinkSchemaRowType =
          (RowType) getPhysicalSchema(flinkSchema).toRowDataType().getLogicalType();
      Schema writeSchema =
          TypeUtil.reassignIds(
              FlinkSchemaUtil.convert(getPhysicalSchema(flinkSchema)), table.schema());

      int writeOperatorParallelism =
          PropertyUtil.propertyAsInt(
              table.properties(),
              SINK_PARALLELISM.key(),
              rowDataInput.getExecutionEnvironment().getParallelism());

      DistributionHashMode distributionMode = getDistributionHashMode();
      LOG.info("take effect distribute mode: {}", distributionMode);
      ShuffleHelper helper = ShuffleHelper.build(table, writeSchema, flinkSchemaRowType);

      ShuffleRulePolicy<RowData, ShuffleKey> shufflePolicy =
          buildShuffleRulePolicy(
              helper, writeOperatorParallelism, distributionMode, overwrite, table);
      LOG.info(
          "shuffle policy config={}, actual={}",
          distributionMode,
          shufflePolicy == null ? DistributionMode.NONE : distributionMode.getDesc());

      String arcticEmitMode =
          table.properties().getOrDefault(ARCTIC_EMIT_MODE.key(), ARCTIC_EMIT_MODE.defaultValue());
      final boolean metricsEventLatency =
          CompatibleFlinkPropertyUtil.propertyAsBoolean(
              table.properties(),
              ArcticValidator.ARCTIC_LATENCY_METRIC_ENABLE,
              ArcticValidator.ARCTIC_LATENCY_METRIC_ENABLE_DEFAULT);

      final boolean metricsEnable =
          CompatibleFlinkPropertyUtil.propertyAsBoolean(
              table.properties(),
              ARCTIC_THROUGHPUT_METRIC_ENABLE,
              ARCTIC_THROUGHPUT_METRIC_ENABLE_DEFAULT);

      final Duration watermarkWriteGap = config.get(AUTO_EMIT_LOGSTORE_WATERMARK_GAP);

      ArcticFileWriter fileWriter =
          createFileWriter(
              table, shufflePolicy, overwrite, flinkSchemaRowType, arcticEmitMode, tableLoader);

      ArcticLogWriter logWriter =
          ArcticUtils.buildArcticLogWriter(
              table.properties(),
              producerConfig,
              topic,
              flinkSchema,
              arcticEmitMode,
              helper,
              tableLoader,
              watermarkWriteGap);

      MetricsGenerator metricsGenerator =
          ArcticUtils.getMetricsGenerator(
              metricsEventLatency, metricsEnable, table, flinkSchemaRowType, writeSchema);

      if (shufflePolicy != null) {
        rowDataInput =
            rowDataInput.partitionCustom(
                shufflePolicy.generatePartitioner(), shufflePolicy.generateKeySelector());
      }

      return withEmit(
          rowDataInput,
          logWriter,
          fileWriter,
          createFileCommitter(table, tableLoader, overwrite, branch, table.spec(), arcticEmitMode),
          writeOperatorParallelism,
          metricsGenerator,
          arcticEmitMode);
    }

    private void initTableIfNeeded() {
      if (table == null) {
        table = ArcticUtils.loadArcticTable(tableLoader);
      }
    }

    /**
     * Transform {@link org.apache.iceberg.TableProperties#WRITE_DISTRIBUTION_MODE} to
     * ShufflePolicyType
     */
    private DistributionHashMode getDistributionHashMode() {
      if (distributionMode != null) {
        return distributionMode;
      }

      String modeName =
          PropertyUtil.propertyAsString(
              table.properties(), WRITE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_DEFAULT);

      DistributionMode mode = DistributionMode.fromName(modeName);
      switch (mode) {
        case NONE:
          return DistributionHashMode.NONE;
        case HASH:
          String hashMode =
              PropertyUtil.propertyAsString(
                  table.properties(),
                  WRITE_DISTRIBUTION_HASH_MODE,
                  WRITE_DISTRIBUTION_HASH_MODE_DEFAULT);
          return DistributionHashMode.valueOfDesc(hashMode);
        case RANGE:
          LOG.warn(
              "Fallback to use 'none' distribution mode, because {}={} is not supported in flink now",
              WRITE_DISTRIBUTION_MODE,
              DistributionMode.RANGE.modeName());
          return DistributionHashMode.NONE;
        default:
          return DistributionHashMode.AUTO;
      }
    }

    @Nullable
    public static ShuffleRulePolicy<RowData, ShuffleKey> buildShuffleRulePolicy(
        ShuffleHelper helper,
        int writeOperatorParallelism,
        DistributionHashMode distributionHashMode,
        boolean overwrite,
        MixedTable table) {
      if (distributionHashMode == DistributionHashMode.AUTO) {
        distributionHashMode =
            DistributionHashMode.autoSelect(
                helper.isPrimaryKeyExist(), helper.isPartitionKeyExist());
      }
      if (distributionHashMode == DistributionHashMode.NONE) {
        return null;
      } else {
        if (distributionHashMode.mustByPrimaryKey() && !helper.isPrimaryKeyExist()) {
          throw new IllegalArgumentException(
              "illegal shuffle policy "
                  + distributionHashMode.getDesc()
                  + " for table without primary key");
        }
        if (distributionHashMode.mustByPartition() && !helper.isPartitionKeyExist()) {
          throw new IllegalArgumentException(
              "illegal shuffle policy "
                  + distributionHashMode.getDesc()
                  + " for table without partition");
        }
        int writeFileSplit;
        if (ArcticUtils.isToBase(overwrite)) {
          writeFileSplit =
              PropertyUtil.propertyAsInt(
                  table.properties(),
                  TableProperties.BASE_FILE_INDEX_HASH_BUCKET,
                  TableProperties.BASE_FILE_INDEX_HASH_BUCKET_DEFAULT);
        } else {
          writeFileSplit =
              PropertyUtil.propertyAsInt(
                  table.properties(),
                  TableProperties.CHANGE_FILE_INDEX_HASH_BUCKET,
                  TableProperties.CHANGE_FILE_INDEX_HASH_BUCKET_DEFAULT);
        }

        return new RoundRobinShuffleRulePolicy(
            helper, writeOperatorParallelism, writeFileSplit, distributionHashMode);
      }
    }
  }

  public static ArcticFileWriter createFileWriter(
      MixedTable mixedTable,
      ShuffleRulePolicy shufflePolicy,
      boolean overwrite,
      RowType flinkSchema,
      ArcticTableLoader tableLoader) {
    return createFileWriter(
        mixedTable, shufflePolicy, overwrite, flinkSchema, ARCTIC_EMIT_FILE, tableLoader);
  }

  public static ArcticFileWriter createFileWriter(
      MixedTable mixedTable,
      ShuffleRulePolicy shufflePolicy,
      boolean overwrite,
      RowType flinkSchema,
      String emitMode,
      ArcticTableLoader tableLoader) {
    if (!ArcticUtils.arcticFileWriterEnable(emitMode)) {
      return null;
    }
    long maxOpenFilesSizeBytes =
        PropertyUtil.propertyAsLong(
            mixedTable.properties(),
            ARCTIC_WRITE_MAX_OPEN_FILE_SIZE,
            ARCTIC_WRITE_MAX_OPEN_FILE_SIZE_DEFAULT);
    LOG.info(
        "with maxOpenFilesSizeBytes = {}MB, close biggest/earliest file to avoid OOM",
        maxOpenFilesSizeBytes >> 20);

    int minFileSplitCount =
        PropertyUtil.propertyAsInt(
            mixedTable.properties(),
            TableProperties.CHANGE_FILE_INDEX_HASH_BUCKET,
            TableProperties.CHANGE_FILE_INDEX_HASH_BUCKET_DEFAULT);

    boolean upsert =
        mixedTable.isKeyedTable()
            && PropertyUtil.propertyAsBoolean(
                mixedTable.properties(),
                TableProperties.UPSERT_ENABLED,
                TableProperties.UPSERT_ENABLED_DEFAULT);
    boolean submitEmptySnapshot =
        PropertyUtil.propertyAsBoolean(
            mixedTable.properties(),
            SUBMIT_EMPTY_SNAPSHOTS.key(),
            SUBMIT_EMPTY_SNAPSHOTS.defaultValue());

    return new ArcticFileWriter(
        shufflePolicy,
        createTaskWriterFactory(mixedTable, overwrite, flinkSchema),
        minFileSplitCount,
        tableLoader,
        upsert,
        submitEmptySnapshot);
  }

  private static TaskWriterFactory<RowData> createTaskWriterFactory(
      MixedTable mixedTable, boolean overwrite, RowType flinkSchema) {
    return new ArcticRowDataTaskWriterFactory(mixedTable, flinkSchema, overwrite);
  }

  public static OneInputStreamOperator<WriteResult, Void> createFileCommitter(
      MixedTable mixedTable,
      ArcticTableLoader tableLoader,
      boolean overwrite,
      String branch,
      PartitionSpec spec) {
    return createFileCommitter(mixedTable, tableLoader, overwrite, branch, spec, ARCTIC_EMIT_FILE);
  }

  public static OneInputStreamOperator<WriteResult, Void> createFileCommitter(
      MixedTable mixedTable,
      ArcticTableLoader tableLoader,
      boolean overwrite,
      String branch,
      PartitionSpec spec,
      String emitMode) {
    if (!ArcticUtils.arcticFileWriterEnable(emitMode)) {
      return null;
    }
    tableLoader.switchLoadInternalTableForKeyedTable(ArcticUtils.isToBase(overwrite));
    return (OneInputStreamOperator)
        ProxyUtil.getProxy(
            IcebergClassUtil.newIcebergFilesCommitter(
                tableLoader, overwrite, branch, spec, mixedTable.io()),
            mixedTable.io());
  }
}
