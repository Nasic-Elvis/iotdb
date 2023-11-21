/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.analyze.partition;

import org.apache.iotdb.common.rpc.thrift.TConsensusGroupId;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.common.rpc.thrift.TSeriesPartitionSlot;
import org.apache.iotdb.common.rpc.thrift.TTimePartitionSlot;
import org.apache.iotdb.commons.client.exception.ClientManagerException;
import org.apache.iotdb.commons.exception.IoTDBException;
import org.apache.iotdb.commons.partition.DataPartition;
import org.apache.iotdb.commons.partition.DataPartitionQueryParam;
import org.apache.iotdb.commons.partition.SchemaNodeManagementPartition;
import org.apache.iotdb.commons.partition.SchemaPartition;
import org.apache.iotdb.commons.partition.executor.SeriesPartitionExecutor;
import org.apache.iotdb.commons.path.PathPatternTree;
import org.apache.iotdb.confignode.rpc.thrift.TDataPartitionReq;
import org.apache.iotdb.confignode.rpc.thrift.TDataPartitionTableResp;
import org.apache.iotdb.confignode.rpc.thrift.TSchemaNodeManagementReq;
import org.apache.iotdb.confignode.rpc.thrift.TSchemaNodeManagementResp;
import org.apache.iotdb.confignode.rpc.thrift.TSchemaPartitionReq;
import org.apache.iotdb.confignode.rpc.thrift.TSchemaPartitionTableResp;
import org.apache.iotdb.confignode.rpc.thrift.TTimeSlotList;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.sql.StatementAnalyzeException;
import org.apache.iotdb.db.protocol.client.ConfigNodeClient;
import org.apache.iotdb.db.queryengine.plan.analyze.IPartitionFetcher;
import org.apache.iotdb.db.queryengine.plan.analyze.cache.partition.PartitionCache;
import org.apache.iotdb.mpp.rpc.thrift.TRegionRouteReq;
import org.apache.iotdb.rpc.TSStatusCode;

import org.apache.thrift.TException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class BasicPartitionFetcher implements IPartitionFetcher {

  private static final String ERR_GET_DATA_PARTITION =
      "An error occurred when executing getDataPartition():";
  private static final String ERR_GET_OR_CREATE_DATA_PARTITION =
      "An error occurred when executing getOrCreateDataPartition():";

  protected static final IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();

  protected final SeriesPartitionExecutor partitionExecutor;

  protected final PartitionCache partitionCache;

  protected BasicPartitionFetcher(int seriesPartitionSlotNum, PartitionCache partitionCache) {
    this.partitionExecutor =
        SeriesPartitionExecutor.getSeriesPartitionExecutor(
            config.getSeriesPartitionExecutorClass(), seriesPartitionSlotNum);
    this.partitionCache = partitionCache;
  }

  protected abstract ConfigNodeClient getClient() throws ClientManagerException, TException;

  @Override
  public SchemaPartition getSchemaPartition(PathPatternTree patternTree) {
    try (ConfigNodeClient client = getClient()) {
      patternTree.constructTree();
      List<String> devicePaths = patternTree.getAllDevicePatterns();
      Map<String, List<String>> storageGroupToDeviceMap =
          partitionCache.getStorageGroupToDevice(devicePaths, true, false, null);
      SchemaPartition schemaPartition = partitionCache.getSchemaPartition(storageGroupToDeviceMap);
      if (null == schemaPartition) {
        TSchemaPartitionTableResp schemaPartitionTableResp =
            client.getSchemaPartitionTable(constructSchemaPartitionReq(patternTree));
        if (schemaPartitionTableResp.getStatus().getCode()
            == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
          schemaPartition = parseSchemaPartitionTableResp(schemaPartitionTableResp);
          partitionCache.updateSchemaPartitionCache(
              schemaPartitionTableResp.getSchemaPartitionTable());
        } else {
          throw new IllegalStateException(
              new IoTDBException(
                  schemaPartitionTableResp.getStatus().getMessage(),
                  schemaPartitionTableResp.getStatus().getCode()));
        }
      }
      return schemaPartition;
    } catch (ClientManagerException | TException e) {
      throw new StatementAnalyzeException(
          "An error occurred when executing getSchemaPartition():" + e.getMessage());
    }
  }

  @Override
  public SchemaPartition getOrCreateSchemaPartition(PathPatternTree patternTree, String userName) {
    try (ConfigNodeClient client = getClient()) {
      patternTree.constructTree();
      List<String> devicePaths = patternTree.getAllDevicePatterns();
      Map<String, List<String>> storageGroupToDeviceMap =
          partitionCache.getStorageGroupToDevice(devicePaths, true, true, userName);
      SchemaPartition schemaPartition = partitionCache.getSchemaPartition(storageGroupToDeviceMap);
      if (null == schemaPartition) {
        TSchemaPartitionTableResp schemaPartitionTableResp =
            client.getOrCreateSchemaPartitionTable(constructSchemaPartitionReq(patternTree));
        if (schemaPartitionTableResp.getStatus().getCode()
            == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
          schemaPartition = parseSchemaPartitionTableResp(schemaPartitionTableResp);
          partitionCache.updateSchemaPartitionCache(
              schemaPartitionTableResp.getSchemaPartitionTable());
        } else {
          throw new IllegalStateException(
              new IoTDBException(
                  schemaPartitionTableResp.getStatus().getMessage(),
                  schemaPartitionTableResp.getStatus().getCode()));
        }
      }
      return schemaPartition;
    } catch (ClientManagerException | TException e) {
      throw new StatementAnalyzeException(
          "An error occurred when executing getOrCreateSchemaPartition():" + e.getMessage());
    }
  }

  @Override
  public SchemaNodeManagementPartition getSchemaNodeManagementPartitionWithLevel(
      PathPatternTree patternTree, PathPatternTree scope, Integer level) {
    try (ConfigNodeClient client = getClient()) {
      patternTree.constructTree();
      TSchemaNodeManagementResp schemaNodeManagementResp =
          client.getSchemaNodeManagementPartition(
              constructSchemaNodeManagementPartitionReq(patternTree, scope, level));

      return parseSchemaNodeManagementPartitionResp(schemaNodeManagementResp);
    } catch (ClientManagerException | TException e) {
      throw new StatementAnalyzeException(
          "An error occurred when executing getSchemaNodeManagementPartition():" + e.getMessage());
    }
  }

  @Override
  public DataPartition getDataPartition(
      Map<String, List<DataPartitionQueryParam>> sgNameToQueryParamsMap) {
    DataPartition dataPartition = partitionCache.getDataPartition(sgNameToQueryParamsMap);
    if (null == dataPartition) {
      try (ConfigNodeClient client = getClient()) {
        TDataPartitionTableResp dataPartitionTableResp =
            client.getDataPartitionTable(constructDataPartitionReqForQuery(sgNameToQueryParamsMap));
        if (dataPartitionTableResp.getStatus().getCode()
            == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
          dataPartition = parseDataPartitionResp(dataPartitionTableResp);
          partitionCache.updateDataPartitionCache(dataPartitionTableResp.getDataPartitionTable());
        } else {
          throw new StatementAnalyzeException(
              ERR_GET_DATA_PARTITION + dataPartitionTableResp.getStatus().getMessage());
        }
      } catch (ClientManagerException | TException e) {
        throw new StatementAnalyzeException(ERR_GET_DATA_PARTITION + e.getMessage());
      }
    }
    return dataPartition;
  }

  @Override
  public DataPartition getDataPartitionWithUnclosedTimeRange(
      Map<String, List<DataPartitionQueryParam>> sgNameToQueryParamsMap) {
    // In this method, we must fetch from config node because it contains -oo or +oo
    // and there is no need to update cache because since we will never fetch it from cache, the
    // update operation will be only time waste
    try (ConfigNodeClient client = getClient()) {
      TDataPartitionTableResp dataPartitionTableResp =
          client.getDataPartitionTable(constructDataPartitionReqForQuery(sgNameToQueryParamsMap));
      if (dataPartitionTableResp.getStatus().getCode()
          == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
        return parseDataPartitionResp(dataPartitionTableResp);
      } else {
        throw new StatementAnalyzeException(
            ERR_GET_DATA_PARTITION + dataPartitionTableResp.getStatus().getMessage());
      }
    } catch (ClientManagerException | TException e) {
      throw new StatementAnalyzeException(ERR_GET_DATA_PARTITION + e.getMessage());
    }
  }

  @Override
  public DataPartition getOrCreateDataPartition(
      Map<String, List<DataPartitionQueryParam>> sgNameToQueryParamsMap) {
    DataPartition dataPartition = partitionCache.getDataPartition(sgNameToQueryParamsMap);
    if (null == dataPartition) {
      // Do not use data partition cache
      try (ConfigNodeClient client = getClient()) {
        TDataPartitionTableResp dataPartitionTableResp =
            client.getOrCreateDataPartitionTable(constructDataPartitionReq(sgNameToQueryParamsMap));
        if (dataPartitionTableResp.getStatus().getCode()
            == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
          dataPartition = parseDataPartitionResp(dataPartitionTableResp);
          partitionCache.updateDataPartitionCache(dataPartitionTableResp.getDataPartitionTable());
        } else {
          throw new StatementAnalyzeException(
              ERR_GET_OR_CREATE_DATA_PARTITION + dataPartitionTableResp.getStatus().getMessage());
        }
      } catch (ClientManagerException | TException e) {
        throw new StatementAnalyzeException(ERR_GET_OR_CREATE_DATA_PARTITION + e.getMessage());
      }
    }
    return dataPartition;
  }

  @Override
  public DataPartition getOrCreateDataPartition(
      List<DataPartitionQueryParam> dataPartitionQueryParams, String userName) {

    Map<String, List<DataPartitionQueryParam>> splitDataPartitionQueryParams =
        splitDataPartitionQueryParam(dataPartitionQueryParams, true, userName);
    DataPartition dataPartition = partitionCache.getDataPartition(splitDataPartitionQueryParams);

    if (null == dataPartition) {
      try (ConfigNodeClient client = getClient()) {
        TDataPartitionReq req = constructDataPartitionReq(splitDataPartitionQueryParams);
        TDataPartitionTableResp dataPartitionTableResp = client.getOrCreateDataPartitionTable(req);

        if (dataPartitionTableResp.getStatus().getCode()
            == TSStatusCode.SUCCESS_STATUS.getStatusCode()) {
          dataPartition = parseDataPartitionResp(dataPartitionTableResp);
          partitionCache.updateDataPartitionCache(dataPartitionTableResp.getDataPartitionTable());
        } else {
          throw new IllegalStateException(
              new IoTDBException(
                  dataPartitionTableResp.getStatus().getMessage(),
                  dataPartitionTableResp.getStatus().getCode()));
        }
      } catch (ClientManagerException | TException e) {
        throw new StatementAnalyzeException(ERR_GET_OR_CREATE_DATA_PARTITION + e.getMessage());
      }
    }
    return dataPartition;
  }

  @Override
  public boolean updateRegionCache(TRegionRouteReq req) {
    return partitionCache.updateGroupIdToReplicaSetMap(req.getTimestamp(), req.getRegionRouteMap());
  }

  @Override
  public void invalidAllCache() {
    partitionCache.invalidAllCache();
  }

  /** split data partition query param by database */
  private Map<String, List<DataPartitionQueryParam>> splitDataPartitionQueryParam(
      List<DataPartitionQueryParam> dataPartitionQueryParams,
      boolean isAutoCreate,
      String userName) {
    List<String> devicePaths = new ArrayList<>();
    for (DataPartitionQueryParam dataPartitionQueryParam : dataPartitionQueryParams) {
      devicePaths.add(dataPartitionQueryParam.getDevicePath());
    }
    Map<String, String> deviceToStorageGroupMap =
        partitionCache.getDeviceToStorageGroup(devicePaths, true, isAutoCreate, userName);
    Map<String, List<DataPartitionQueryParam>> result = new HashMap<>();
    for (DataPartitionQueryParam dataPartitionQueryParam : dataPartitionQueryParams) {
      String devicePath = dataPartitionQueryParam.getDevicePath();
      if (deviceToStorageGroupMap.containsKey(devicePath)) {
        String storageGroup = deviceToStorageGroupMap.get(devicePath);
        result.computeIfAbsent(storageGroup, key -> new ArrayList<>()).add(dataPartitionQueryParam);
      }
    }
    return result;
  }

  private TSchemaPartitionReq constructSchemaPartitionReq(PathPatternTree patternTree) {
    try {
      return new TSchemaPartitionReq(patternTree.serialize());
    } catch (IOException e) {
      throw new StatementAnalyzeException("An error occurred when serializing pattern tree");
    }
  }

  private TSchemaNodeManagementReq constructSchemaNodeManagementPartitionReq(
      PathPatternTree patternTree, PathPatternTree scope, Integer level) {
    try {
      TSchemaNodeManagementReq schemaNodeManagementReq =
          new TSchemaNodeManagementReq(patternTree.serialize());
      schemaNodeManagementReq.setScopePatternTree(scope.serialize());
      if (null == level) {
        schemaNodeManagementReq.setLevel(-1);
      } else {
        schemaNodeManagementReq.setLevel(level);
      }
      return schemaNodeManagementReq;
    } catch (IOException e) {
      throw new StatementAnalyzeException("An error occurred when serializing pattern tree");
    }
  }

  private static class ComplexTimeSlotList {

    Set<TTimePartitionSlot> timeSlotList;
    boolean needLeftAll;
    boolean needRightAll;

    private ComplexTimeSlotList(boolean needLeftAll, boolean needRightAll) {
      timeSlotList = new HashSet<>();
      this.needLeftAll = needLeftAll;
      this.needRightAll = needRightAll;
    }

    private void putTimeSlot(List<TTimePartitionSlot> slotList) {
      timeSlotList.addAll(slotList);
    }
  }

  private TDataPartitionReq constructDataPartitionReq(
      Map<String, List<DataPartitionQueryParam>> sgNameToQueryParamsMap) {
    Map<String, Map<TSeriesPartitionSlot, TTimeSlotList>> partitionSlotsMap = new HashMap<>();
    for (Map.Entry<String, List<DataPartitionQueryParam>> entry :
        sgNameToQueryParamsMap.entrySet()) {
      // for each sg
      Map<TSeriesPartitionSlot, TTimeSlotList> deviceToTimePartitionMap = new HashMap<>();

      Map<TSeriesPartitionSlot, ComplexTimeSlotList> seriesSlotTimePartitionMap = new HashMap<>();

      for (DataPartitionQueryParam queryParam : entry.getValue()) {
        seriesSlotTimePartitionMap
            .computeIfAbsent(
                partitionExecutor.getSeriesPartitionSlot(queryParam.getDevicePath()),
                k ->
                    new ComplexTimeSlotList(
                        queryParam.isNeedLeftAll(), queryParam.isNeedRightAll()))
            .putTimeSlot(queryParam.getTimePartitionSlotList());
      }
      seriesSlotTimePartitionMap.forEach(
          (k, v) ->
              deviceToTimePartitionMap.put(
                  k,
                  new TTimeSlotList(
                      new ArrayList<>(v.timeSlotList), v.needLeftAll, v.needRightAll)));
      partitionSlotsMap.put(entry.getKey(), deviceToTimePartitionMap);
    }
    return new TDataPartitionReq(partitionSlotsMap);
  }

  /** For query, DataPartitionQueryParam is shared by each device */
  private TDataPartitionReq constructDataPartitionReqForQuery(
      Map<String, List<DataPartitionQueryParam>> sgNameToQueryParamsMap) {
    Map<String, Map<TSeriesPartitionSlot, TTimeSlotList>> partitionSlotsMap = new HashMap<>();
    TTimeSlotList sharedTTimeSlotList = null;
    for (Map.Entry<String, List<DataPartitionQueryParam>> entry :
        sgNameToQueryParamsMap.entrySet()) {
      // for each sg
      Map<TSeriesPartitionSlot, TTimeSlotList> deviceToTimePartitionMap = new HashMap<>();

      for (DataPartitionQueryParam queryParam : entry.getValue()) {
        if (sharedTTimeSlotList == null) {
          sharedTTimeSlotList =
              new TTimeSlotList(
                  queryParam.getTimePartitionSlotList(),
                  queryParam.isNeedLeftAll(),
                  queryParam.isNeedRightAll());
        }
        deviceToTimePartitionMap.putIfAbsent(
            partitionExecutor.getSeriesPartitionSlot(queryParam.getDevicePath()),
            sharedTTimeSlotList);
      }
      partitionSlotsMap.put(entry.getKey(), deviceToTimePartitionMap);
    }
    return new TDataPartitionReq(partitionSlotsMap);
  }

  private SchemaPartition parseSchemaPartitionTableResp(
      TSchemaPartitionTableResp schemaPartitionTableResp) {
    Map<String, Map<TSeriesPartitionSlot, TRegionReplicaSet>> regionReplicaMap = new HashMap<>();
    for (Map.Entry<String, Map<TSeriesPartitionSlot, TConsensusGroupId>> entry1 :
        schemaPartitionTableResp.getSchemaPartitionTable().entrySet()) {
      Map<TSeriesPartitionSlot, TRegionReplicaSet> result1 =
          regionReplicaMap.computeIfAbsent(entry1.getKey(), k -> new HashMap<>());
      for (Map.Entry<TSeriesPartitionSlot, TConsensusGroupId> entry2 :
          entry1.getValue().entrySet()) {
        TSeriesPartitionSlot seriesPartitionSlot = entry2.getKey();
        TConsensusGroupId consensusGroupId = entry2.getValue();
        result1.put(seriesPartitionSlot, partitionCache.getRegionReplicaSet(consensusGroupId));
      }
    }

    return new SchemaPartition(
        regionReplicaMap,
        IoTDBDescriptor.getInstance().getConfig().getSeriesPartitionExecutorClass(),
        IoTDBDescriptor.getInstance().getConfig().getSeriesPartitionSlotNum());
  }

  private SchemaNodeManagementPartition parseSchemaNodeManagementPartitionResp(
      TSchemaNodeManagementResp schemaNodeManagementResp) {
    return new SchemaNodeManagementPartition(
        schemaNodeManagementResp.getSchemaRegionMap(),
        IoTDBDescriptor.getInstance().getConfig().getSeriesPartitionExecutorClass(),
        IoTDBDescriptor.getInstance().getConfig().getSeriesPartitionSlotNum(),
        schemaNodeManagementResp.getMatchedNode());
  }

  private DataPartition parseDataPartitionResp(TDataPartitionTableResp dataPartitionTableResp) {
    Map<String, Map<TSeriesPartitionSlot, Map<TTimePartitionSlot, List<TRegionReplicaSet>>>>
        regionReplicaSet = new HashMap<>();
    for (Map.Entry<
            String, Map<TSeriesPartitionSlot, Map<TTimePartitionSlot, List<TConsensusGroupId>>>>
        entry1 : dataPartitionTableResp.getDataPartitionTable().entrySet()) {
      Map<TSeriesPartitionSlot, Map<TTimePartitionSlot, List<TRegionReplicaSet>>> result1 =
          regionReplicaSet.computeIfAbsent(entry1.getKey(), k -> new HashMap<>());
      for (Map.Entry<TSeriesPartitionSlot, Map<TTimePartitionSlot, List<TConsensusGroupId>>>
          entry2 : entry1.getValue().entrySet()) {
        Map<TTimePartitionSlot, List<TRegionReplicaSet>> result2 =
            result1.computeIfAbsent(entry2.getKey(), k -> new HashMap<>());
        for (Map.Entry<TTimePartitionSlot, List<TConsensusGroupId>> entry3 :
            entry2.getValue().entrySet()) {
          List<TRegionReplicaSet> regionReplicaSets = new LinkedList<>();
          for (TConsensusGroupId consensusGroupId : entry3.getValue()) {
            regionReplicaSets.add(partitionCache.getRegionReplicaSet(consensusGroupId));
          }
          result2.put(entry3.getKey(), regionReplicaSets);
        }
      }
    }

    return new DataPartition(
        regionReplicaSet,
        config.getSeriesPartitionExecutorClass(),
        config.getSeriesPartitionSlotNum());
  }
}