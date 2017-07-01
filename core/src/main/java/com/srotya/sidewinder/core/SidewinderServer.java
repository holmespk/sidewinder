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
package com.srotya.sidewinder.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.srotya.sidewinder.core.api.DatabaseOpsApi;
import com.srotya.sidewinder.core.api.InfluxApi;
import com.srotya.sidewinder.core.api.MeasurementOpsApi;
import com.srotya.sidewinder.core.api.grafana.GrafanaQueryApi;
import com.srotya.sidewinder.core.health.RestAPIHealthCheck;
import com.srotya.sidewinder.core.ingress.binary.NettyBinaryIngestionServer;
import com.srotya.sidewinder.core.ingress.http.NettyHTTPIngestionServer;
import com.srotya.sidewinder.core.storage.StorageEngine;
import com.srotya.sidewinder.core.utils.BackgrounThreadFactory;

import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

/**
 * Main driver class for single-node sidewinder. It will register the REST APIs,
 * initialize the storage engine as well as start the network receivers for HTTP
 * and binary protocols for Sidewinder.
 * 
 * @author ambud
 *
 */
public class SidewinderServer extends Application<SidewinderConfig> {

	private static final Logger logger = Logger.getLogger(SidewinderServer.class.getName());
	private StorageEngine storageEngine;
	private static SidewinderServer sidewinderServer;

	@Override
	public void run(SidewinderConfig config, Environment env) throws Exception {
		final MetricRegistry registry = new MetricRegistry();

		Map<String, String> conf = new HashMap<>();
		String path = config.getConfigPath();
		if (path != null) {
			Properties props = new Properties();
			props.load(new FileInputStream(path));
			for (final String name : props.stringPropertyNames()) {
				conf.put(name, props.getProperty(name));
			}
		}

		String storageEngineClass = conf.getOrDefault("storage.engine",
				"com.srotya.sidewinder.core.storage.mem.MemStorageEngine");
		logger.info("Using Storage Engine:" + storageEngineClass);

		ScheduledExecutorService bgTasks = Executors.newScheduledThreadPool(2,
				new BackgrounThreadFactory("sidewinderbg-tasks"));

		storageEngine = (StorageEngine) Class.forName(storageEngineClass).newInstance();
		storageEngine.configure(conf, bgTasks);
		storageEngine.connect();
		ResourceMonitor.getInstance().init(storageEngine, bgTasks);
		env.jersey().register(new GrafanaQueryApi(storageEngine, registry));
		env.jersey().register(new MeasurementOpsApi(storageEngine, registry));
		env.jersey().register(new DatabaseOpsApi(storageEngine, registry));
		env.jersey().register(new InfluxApi(storageEngine, registry));
		env.healthChecks().register("restapi", new RestAPIHealthCheck());

		@SuppressWarnings("resource")
		SidewinderDropwizardReporter reporter = new SidewinderDropwizardReporter(registry, "request",
				new MetricFilter() {

					@Override
					public boolean matches(String name, Metric metric) {
						return true;
					}
				}, TimeUnit.SECONDS, TimeUnit.SECONDS, storageEngine);
		reporter.start(1, TimeUnit.SECONDS);

		if (Boolean.parseBoolean(conf.getOrDefault("server.netty.http.enabled", "false"))) {
			NettyHTTPIngestionServer server = new NettyHTTPIngestionServer();
			server.init(storageEngine, conf, registry);
			server.start();
		}

		if (Boolean.parseBoolean(conf.getOrDefault("server.netty.binary.enabled", "false"))) {
			NettyBinaryIngestionServer binServer = new NettyBinaryIngestionServer();
			binServer.init(storageEngine, conf);
			binServer.start();
		}

		Runtime.getRuntime().addShutdownHook(new Thread("shutdown-thread") {
			@Override
			public void run() {
				try {
					logger.info("Storage engine shutdown started");
					storageEngine.disconnect();
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Storage engine shutdown failure", e);
				}
			}
		});
	}

	/**
	 * Main method to launch dropwizard app
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		sidewinderServer = new SidewinderServer();
		sidewinderServer.run(args);
	}

	/**
	 * @return
	 */
	public static SidewinderServer getSidewinderServer() {
		return sidewinderServer;
	}

	/**
	 * @return
	 */
	public StorageEngine getStorageEngine() {
		return storageEngine;
	}

}
