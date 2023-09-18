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

package com.oltpbenchmark.benchmarks.hot;

import com.oltpbenchmark.api.Loader;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.catalog.Table;
import com.oltpbenchmark.util.SQLUtil;
import com.oltpbenchmark.util.TextGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

class HOTLoader extends Loader<HOTBenchmark> {
    private final int numRecords;
    private final PartitionHelper partitionHelper;
    private final int loadFrom, loadTo;

    public HOTLoader(HOTBenchmark benchmark, PartitionHelper partitionHelper) {
        super(benchmark);
        this.partitionHelper = partitionHelper;
        this.loadFrom = benchmark.loadFrom;
        this.loadTo = benchmark.loadTo == -1 ? 
            (int) Math.round(HOTConstants.RECORD_COUNT * this.scaleFactor) : benchmark.loadTo;
        this.numRecords = this.loadTo - this.loadFrom;
        if (LOG.isDebugEnabled()) {
            LOG.debug("# of RECORDS:  {}", this.numRecords);
        }
    }

    @Override
    public List<LoaderThread> createLoaderThreads() {
        List<LoaderThread> threads = new ArrayList<>();
        int count = this.loadFrom;
        while (count < this.loadTo) {
            final int start = count;
            final int stop = Math.min(start + HOTConstants.THREAD_BATCH_SIZE, this.loadTo);
            threads.add(new LoaderThread(this.benchmark) {
                @Override
                public void load(Connection conn) throws SQLException {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format("HOTLoadThread[%d, %d]", start, stop));
                    }
                    loadRecords(conn, start, stop);
                }
            });
            count = stop;
        }
        return (threads);
    }

    private void loadRecords(Connection conn, int start, int stop) throws SQLException {
        Table catalog_tbl = benchmark.getCatalog().getTable("USERTABLE");

        String sql = SQLUtil.getInsertSQL(catalog_tbl, this.getDatabaseType());
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            long total = 0;
            int batch = 0;
            for (int i = start; i < stop; i++) {
                int col = 1;
                stmt.setInt(col++, i);
                for (int j = 0; j < HOTConstants.NUM_FIELDS; j++) {
                    stmt.setString(col++, TextGenerator.randomStr(rng(), benchmark.fieldSize));
                }
                partitionHelper.setPartition(stmt, i);

                stmt.addBatch();
                total++;
                if (++batch >= workConf.getBatchSize()) {
                    stmt.executeBatch();
                    batch = 0;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Records Loaded %d / %d", total, this.numRecords));
                    }
                }
            }
            if (batch > 0) {
                stmt.executeBatch();
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Records Loaded %d / %d", total, this.numRecords));
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Finished loading {}", catalog_tbl.getName());
        }
    }

}
