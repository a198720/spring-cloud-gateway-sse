/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.config.DynamicIntProperty;
import com.netflix.discovery.EurekaClient;
import com.netflix.netty.common.accesslog.AccessLogPublisher;
import com.netflix.netty.common.channel.config.ChannelConfig;
import com.netflix.netty.common.channel.config.CommonChannelConfigKeys;
import com.netflix.netty.common.metrics.EventLoopGroupMetrics;
import com.netflix.netty.common.proxyprotocol.StripUntrustedProxyHeadersHandler;
import com.netflix.netty.common.ssl.ServerSslConfig;
import com.netflix.netty.common.status.ServerStatusManager;
import com.netflix.spectator.api.Registry;
import com.netflix.zuul.context.SessionContextDecorator;
import com.netflix.zuul.netty.server.*;
import com.netflix.zuul.netty.server.http2.Http2SslChannelInitializer;
import com.netflix.zuul.netty.server.push.PushConnectionRegistry;
import com.netflix.zuul.netty.ssl.BaseSslContextFactory;
import com.netflix.zuul.push.SamplePushMessageSenderInitializer;
import com.netflix.zuul.push.SampleSSEPushChannelInitializer;
import com.netflix.zuul.push.SampleWebSocketPushChannelInitializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.ClientAuth;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Sample Server Startup - class that configures the Netty server startup settings.
 */
@Singleton
public class SampleServerStartup extends BaseServerStartup {
    private final PushConnectionRegistry pushConnectionRegistry;
    private final SamplePushMessageSenderInitializer pushSenderInitializer;

    @Inject
    public SampleServerStartup(ServerStatusManager serverStatusManager, FilterLoader filterLoader,
                                SessionContextDecorator sessionCtxDecorator, FilterUsageNotifier usageNotifier,
                                RequestCompleteHandler reqCompleteHandler, Registry registry,
                                DirectMemoryMonitor directMemoryMonitor, EventLoopGroupMetrics eventLoopGroupMetrics,
                                EurekaClient discoveryClient, ApplicationInfoManager applicationInfoManager,
                                AccessLogPublisher accessLogPublisher, PushConnectionRegistry pushConnectionRegistry,
                                SamplePushMessageSenderInitializer pushSenderInitializer) {

        super(serverStatusManager, filterLoader, sessionCtxDecorator, usageNotifier, reqCompleteHandler, registry,
                directMemoryMonitor, eventLoopGroupMetrics, discoveryClient, applicationInfoManager, accessLogPublisher);
        this.pushConnectionRegistry = pushConnectionRegistry;
        this.pushSenderInitializer = pushSenderInitializer;
    }

    @Override
    protected Map<SocketAddress, ChannelInitializer<?>> chooseAddrsAndChannels(ChannelGroup clientChannels) {
        Map<SocketAddress, ChannelInitializer<?>> addrsToChannels = new HashMap<>();
        SocketAddress socketAddress;
        String metricId;
        {
            int port = new DynamicIntProperty("zuul.server.port.main", 8085).get();
            socketAddress = new SocketAddressProperty("zuul.server.addr.main", "=" + port).getValue();
            if (socketAddress instanceof InetSocketAddress) {
                metricId = String.valueOf(((InetSocketAddress) socketAddress).getPort());
            } else {
                // Just pick something.   This would likely be a UDS addr or a LocalChannel addr.
                metricId = socketAddress.toString();
            }
        }

        SocketAddress pushSockAddress;
        {
            int pushPort = new DynamicIntProperty("zuul.server.port.http.push", 7008).get();
            pushSockAddress = new SocketAddressProperty(
                    "zuul.server.addr.http.push", "=" + pushPort).getValue();
        }
        String mainListenAddressName = "main";
        ChannelConfig channelConfig = defaultChannelConfig(mainListenAddressName);
        ChannelConfig channelDependencies = defaultChannelDependencies(mainListenAddressName);

        // Settings to be used when SSE client connection is needed

        channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.ALWAYS);
        channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, false);
        channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
        channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, false);

        channelDependencies.set(ZuulDependencyKeys.pushConnectionRegistry, pushConnectionRegistry);

        addrsToChannels.put(socketAddress, new SampleSSEPushChannelInitializer(metricId, channelConfig, channelDependencies, clientChannels));

        logAddrConfigured(socketAddress);

        // port to accept push message from the backend, should be accessible on internal network only.
        addrsToChannels.put(pushSockAddress, pushSenderInitializer);
        logAddrConfigured(pushSockAddress);

        //Settings to be used when websocket client connection is needed

//        channelConfig.set(CommonChannelConfigKeys.allowProxyHeadersWhen, StripUntrustedProxyHeadersHandler.AllowWhen.ALWAYS);
//        channelConfig.set(CommonChannelConfigKeys.preferProxyProtocolForClientIp, false);
//        channelConfig.set(CommonChannelConfigKeys.isSSlFromIntermediary, false);
//        channelConfig.set(CommonChannelConfigKeys.withProxyProtocol, false);
//
//        channelDependencies.set(ZuulDependencyKeys.pushConnectionRegistry, pushConnectionRegistry);
//
//        portsToChannels.put(port, new SiSuiteWebSocketPushChannelInitializer(port, channelConfig, channelDependencies, clientChannels));
//        logPortConfigured(port, null);
//
//        // port to accept push message from the backend, should be accessible on internal network only.
//        portsToChannels.put(pushPort, pushSenderInitializer);
//        logPortConfigured(pushPort, null);

        return Collections.unmodifiableMap(addrsToChannels);
    }

}
