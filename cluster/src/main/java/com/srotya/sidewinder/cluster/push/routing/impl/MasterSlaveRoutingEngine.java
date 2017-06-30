/**
 * Copyright 2017 Ambud Sharma
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.srotya.sidewinder.cluster.push.routing.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.srotya.sidewinder.cluster.connectors.ClusterConnector;
import com.srotya.sidewinder.cluster.push.routing.Node;
import com.srotya.sidewinder.cluster.push.routing.RoutingEngine;
import com.srotya.sidewinder.core.rpc.Point;

/**
 * {@link RoutingEngine} implementation for a Leader Follower design for
 * Sidewinder cluster.
 * 
 * When this {@link RoutingEngine} is used, the cluster has only a single Master
 * which is responsible for accepting all writes. Slaves are read-only replicas
 * of the Master.
 * 
 * @author ambud
 */
public class MasterSlaveRoutingEngine extends RoutingEngine {

	private List<Node> nodeSet;

	public MasterSlaveRoutingEngine() {
		nodeSet = Collections.synchronizedList(new ArrayList<>());
	}

	@Override
	public void init(Map<String, String> conf, ClusterConnector connector) {
		super.init(conf, connector);
		connector.initializeRouterHooks(this);
	}

	@Override
	public List<Node> routeData(Point point, int replicationFactor) {
		return nodeSet.subList(0, replicationFactor < nodeSet.size() ? replicationFactor : nodeSet.size());
	}

	@Override
	public void nodeAdded(Node node) {
		nodeSet.add(node);
	}

	@Override
	public void nodeDeleted(Node node) {
		nodeSet.add(node);
	}

}
