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

package org.killbill.billing.util.nodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.killbill.billing.osgi.api.PluginState;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.nodes.json.NodeInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginServiceInfoModelJson;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestNodeInfoMapper extends UtilTestSuiteNoDB {

    @Inject
    protected NodeInfoMapper nodeInfoMapper;

    @Test(groups = "fast")
    public void testNodeInfoSerialization() throws Exception {

        final PluginServiceInfoModelJson svc = new PluginServiceInfoModelJson("typeName", "registrationName");

        final Set<PluginServiceInfoModelJson> services1 = new HashSet<PluginServiceInfoModelJson>();
        services1.add(svc);

        final List<PluginInfoModelJson> pluginInfos = new ArrayList<PluginInfoModelJson>();
        final PluginInfoModelJson info1 = new PluginInfoModelJson("sym1", "key1", "name1", "vers1", PluginState.STOPPED, true, services1);
        pluginInfos.add(info1);
        final NodeInfoModelJson input = new NodeInfoModelJson("nodeName", clock.getUTCNow(), clock.getUTCNow(), "1.0", "1.0", "1.0", "1.0", "1.0", pluginInfos);

        final String nodeInfoStr = nodeInfoMapper.serializeNodeInfo(input);

        final NodeInfoModelJson res = nodeInfoMapper.deserializeNodeInfo(nodeInfoStr);

        Assert.assertEquals(res, input);
    }

    @Test(groups = "fast")
    public void testNodeSystemCommandSerialization() throws Exception {

        final NodeCommandProperty prop = new NodeCommandProperty("something", "nothing");
        final PluginNodeCommandMetadata nodeCommandMetadata = new PluginNodeCommandMetadata("foo", "key1", "1.2.3", ImmutableList.<NodeCommandProperty>of(prop));

        final String nodeCmdStr = nodeInfoMapper.serializeNodeCommand(nodeCommandMetadata);

        final NodeCommandMetadata res = nodeInfoMapper.deserializeNodeCommand(nodeCmdStr, SystemNodeCommandType.START_PLUGIN.name());

        Assert.assertTrue(res instanceof PluginNodeCommandMetadata);
        Assert.assertEquals(((PluginNodeCommandMetadata) res).getPluginName(), nodeCommandMetadata.getPluginName());
        Assert.assertEquals(((PluginNodeCommandMetadata) res).getPluginVersion(), nodeCommandMetadata.getPluginVersion());
        Assert.assertEquals(res.getProperties().size(), 1);
        Assert.assertEquals(res.getProperties().get(0).getKey(), "something");
        Assert.assertEquals(res.getProperties().get(0).getValue(), "nothing");
    }

    @Test(groups = "fast")
    public void testNodeCommandSerialization() throws Exception {

        final NodeCommandProperty prop = new NodeCommandProperty("something", "nothing");
        final NodeCommandMetadata nodeCommandMetadata = new DefaultNodeCommandMetadata(ImmutableList.<NodeCommandProperty>of(prop));

        final String nodeCmdStr = nodeInfoMapper.serializeNodeCommand(nodeCommandMetadata);

        final NodeCommandMetadata res = nodeInfoMapper.deserializeNodeCommand(nodeCmdStr, "opaque type");

        Assert.assertTrue(res instanceof NodeCommandMetadata);
        Assert.assertEquals(res.getProperties().size(), 1);
        Assert.assertEquals(res.getProperties().get(0).getKey(), "something");
        Assert.assertEquals(res.getProperties().get(0).getValue(), "nothing");
    }
}
