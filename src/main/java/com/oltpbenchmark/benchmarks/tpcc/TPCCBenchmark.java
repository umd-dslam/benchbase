/*
 * Copyright 2020 by OLTPBenchmark Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.oltpbenchmark.benchmarks.tpcc;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.BenchmarkModule;
import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.Worker;
import com.oltpbenchmark.benchmarks.tpcc.procedures.NewOrder;

import org.apache.commons.configuration2.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TPCCBenchmark extends BenchmarkModule {
    private static final Logger LOG = LoggerFactory.getLogger(TPCCBenchmark.class);

    final int region;
    final boolean loadAll;

    public TPCCBenchmark(WorkloadConfiguration workConf) {
        super(workConf);

        int region = 0;
        boolean loadAll = false;

        XMLConfiguration xmlConfig = workConf.getXmlConfig();
        if (xmlConfig != null) {
            if (xmlConfig.containsKey("region")) {
                region = xmlConfig.getInt("region");
            }

            if (xmlConfig.containsKey("loadall")) {
                loadAll = xmlConfig.getBoolean("loadall");
            }
        }

        this.region = region;
        this.loadAll = loadAll;
    }

    @Override
    protected Package getProcedurePackageImpl() {
        return (NewOrder.class.getPackage());
    }

    @Override
    protected List<Worker<? extends BenchmarkModule>> makeWorkersImpl() {
        List<Worker<? extends BenchmarkModule>> workers = new ArrayList<>();

        try {
            PartitionHelper partitionHelper = new PartitionHelper(this);

            LOG.info("Number of partitions: {}", partitionHelper.getPartitions().size());

            List<TPCCWorker> terminals = createTerminals(partitionHelper);
            workers.addAll(terminals);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }

        return workers;
    }

    @Override
    protected Loader<TPCCBenchmark> makeLoaderImpl() {
        try {
            PartitionHelper partitionHelper = new PartitionHelper(this);
            String[] loadedPartitions;

            if (this.loadAll) {
                loadedPartitions = partitionHelper.getPartitions().toArray(String[]::new);
            } else {
                loadedPartitions = new String[] { partitionHelper.getPartition(this.region) };
            }

            return new TPCCLoader(this, loadedPartitions);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<TPCCWorker> createTerminals(PartitionHelper partitions) throws SQLException {
        if (this.region == 0) {
            return new ArrayList<>();
        }

        TPCCWorker[] terminals = new TPCCWorker[workConf.getTerminals()];

        String partition = partitions.getPartition(this.region);
        int numWarehouses = (int) workConf.getScaleFactor();
        if (numWarehouses <= 0) {
            numWarehouses = 1;
        }

        int numTerminals = workConf.getTerminals();

        // We distribute terminals evenly across the warehouses
        // Eg. if there are 10 terminals across 7 warehouses, they
        // are distributed as
        // 1, 1, 2, 1, 2, 1, 2
        final double terminalsPerWarehouse = (double) numTerminals / numWarehouses;
        int workerId = 0;

        for (int w = 0; w < numWarehouses; w++) {
            // Compute the number of terminals in *this* warehouse
            int lowerTerminalId = (int) (w * terminalsPerWarehouse);
            int upperTerminalId = (int) ((w + 1) * terminalsPerWarehouse);
            // protect against double rounding errors
            PartitionedWId w_id = new PartitionedWId(partition, w + 1);
            if (w_id.id == numWarehouses) {
                upperTerminalId = numTerminals;
            }
            int numWarehouseTerminals = upperTerminalId - lowerTerminalId;

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("w_id %d = %d terminals [lower=%d / upper%d]", w_id, numWarehouseTerminals,
                        lowerTerminalId, upperTerminalId));
            }

            final double districtsPerTerminal = TPCCConfig.configDistPerWhse / (double) numWarehouseTerminals;
            for (int terminalId = 0; terminalId < numWarehouseTerminals; terminalId++) {
                int lowerDistrictId = (int) (terminalId * districtsPerTerminal);
                int upperDistrictId = (int) ((terminalId + 1) * districtsPerTerminal);
                if (terminalId + 1 == numWarehouseTerminals) {
                    upperDistrictId = TPCCConfig.configDistPerWhse;
                }
                lowerDistrictId += 1;

                TPCCWorker terminal = new TPCCWorker(this, workerId++, w_id, lowerDistrictId,
                        upperDistrictId,
                        partitions);
                terminals[lowerTerminalId + terminalId] = terminal;
            }

        }

        return Arrays.asList(terminals);
    }

}
