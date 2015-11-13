/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.info;

import java.io.IOException;
import java.util.List;

import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.util.info.dao.NodeInfoDao;
import org.killbill.billing.util.info.dao.NodeInfoModelDao;
import org.killbill.billing.util.info.json.NodeInfoModelJson;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultKillbillInfoApi implements KillbillInfoApi {

    private final NodeInfoDao nodeInfoDao;
    private final NodeInfoMapper mapper;

    @Inject
    public DefaultKillbillInfoApi(final NodeInfoDao nodeInfoDao, final NodeInfoMapper mapper) {
        this.nodeInfoDao = nodeInfoDao;
        this.mapper = mapper;
    }

    @Override
    public Iterable<NodeInfo> getNodesInfo() {
        final List<NodeInfoModelDao> allNodes = nodeInfoDao.getAll();

        final Iterable<NodeInfoModelJson> allModelNodes = Iterables.transform(allNodes, new Function<NodeInfoModelDao, NodeInfoModelJson>() {
            @Override
            public NodeInfoModelJson apply(final NodeInfoModelDao input) {
                try {
                    return mapper.deserialize(input.getNodeInfo());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return Iterables.transform(allModelNodes, new Function<NodeInfoModelJson, NodeInfo>() {
            @Override
            public NodeInfo apply(final NodeInfoModelJson input) {
                return new DefaultNodeInfo(input);
            }
        });
    }

    @Override
    public void updatePluginInfo(final Iterable<PluginInfo> plugins) {

    }
}
