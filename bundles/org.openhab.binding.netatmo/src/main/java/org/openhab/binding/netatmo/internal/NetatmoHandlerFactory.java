/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.netatmo.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.netatmo.internal.api.data.ModuleType;
import org.openhab.binding.netatmo.internal.config.BindingConfiguration;
import org.openhab.binding.netatmo.internal.deserialization.NADeserializer;
import org.openhab.binding.netatmo.internal.handler.ApiBridgeHandler;
import org.openhab.binding.netatmo.internal.handler.CommonInterface;
import org.openhab.binding.netatmo.internal.handler.DeviceHandler;
import org.openhab.binding.netatmo.internal.handler.ModuleHandler;
import org.openhab.binding.netatmo.internal.handler.capability.AirCareCapability;
import org.openhab.binding.netatmo.internal.handler.capability.CameraCapability;
import org.openhab.binding.netatmo.internal.handler.capability.Capability;
import org.openhab.binding.netatmo.internal.handler.capability.ChannelHelperCapability;
import org.openhab.binding.netatmo.internal.handler.capability.DeviceCapability;
import org.openhab.binding.netatmo.internal.handler.capability.DoorbellCapability;
import org.openhab.binding.netatmo.internal.handler.capability.HomeCapability;
import org.openhab.binding.netatmo.internal.handler.capability.MeasureCapability;
import org.openhab.binding.netatmo.internal.handler.capability.PersonCapability;
import org.openhab.binding.netatmo.internal.handler.capability.PresenceCapability;
import org.openhab.binding.netatmo.internal.handler.capability.RoomCapability;
import org.openhab.binding.netatmo.internal.handler.capability.SmokeCapability;
import org.openhab.binding.netatmo.internal.handler.capability.WeatherCapability;
import org.openhab.binding.netatmo.internal.handler.channelhelper.ChannelHelper;
import org.openhab.binding.netatmo.internal.providers.NetatmoDescriptionProvider;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NetatmoHandlerFactory} is responsible for creating things and
 * thing handlers.
 *
 * @author Gaël L'hopital - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.netatmo")
public class NetatmoHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(NetatmoHandlerFactory.class);

    private final BindingConfiguration configuration = new BindingConfiguration();
    private final NetatmoDescriptionProvider stateDescriptionProvider;
    private final NADeserializer deserializer;
    private final HttpClient httpClient;
    private final HttpService httpService;

    @Activate
    public NetatmoHandlerFactory(@Reference NetatmoDescriptionProvider stateDescriptionProvider,
            @Reference HttpClientFactory factory, @Reference NADeserializer deserializer,
            @Reference HttpService httpService, Map<String, @Nullable Object> config) {
        this.stateDescriptionProvider = stateDescriptionProvider;
        this.httpClient = factory.getCommonHttpClient();
        this.deserializer = deserializer;
        this.httpService = httpService;
        configChanged(config);
    }

    @Modified
    public void configChanged(Map<String, @Nullable Object> config) {
        BindingConfiguration newConf = ConfigParser.configurationAs(config, BindingConfiguration.class);
        if (newConf != null) {
            configuration.update(newConf);
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return ModuleType.AS_SET.stream().anyMatch(mt -> mt.thingTypeUID.equals(thingTypeUID));
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        return ModuleType.AS_SET.stream().filter(mt -> mt.thingTypeUID.equals(thingTypeUID)).findFirst()
                .map(mt -> buildHandler(thing, mt)).orElse(null);
    }

    private BaseThingHandler buildHandler(Thing thing, ModuleType moduleType) {
        if (ModuleType.ACCOUNT.equals(moduleType)) {
            return new ApiBridgeHandler((Bridge) thing, httpClient, deserializer, configuration, httpService);
        }
        CommonInterface handler = moduleType.isABridge() ? new DeviceHandler((Bridge) thing) : new ModuleHandler(thing);

        List<ChannelHelper> helpers = new ArrayList<>();
        moduleType.channelGroups
                .forEach(channelGroup -> channelGroup.getHelperInstance().ifPresent(helper -> helpers.add(helper)));

        moduleType.capabilities.forEach(capability -> {
            Capability newCap = null;
            if (capability == DeviceCapability.class) {
                newCap = new DeviceCapability(handler);
            } else if (capability == AirCareCapability.class) {
                newCap = new AirCareCapability(handler);
            } else if (capability == HomeCapability.class) {
                newCap = new HomeCapability(handler, stateDescriptionProvider);
            } else if (capability == WeatherCapability.class) {
                newCap = new WeatherCapability(handler);
            } else if (capability == RoomCapability.class) {
                newCap = new RoomCapability(handler);
            } else if (capability == DoorbellCapability.class) {
                newCap = new DoorbellCapability(handler, stateDescriptionProvider, helpers);
            } else if (capability == PersonCapability.class) {
                newCap = new PersonCapability(handler, stateDescriptionProvider, helpers);
            } else if (capability == CameraCapability.class) {
                newCap = new CameraCapability(handler, stateDescriptionProvider, helpers);
            } else if (capability == SmokeCapability.class) {
                newCap = new SmokeCapability(handler, stateDescriptionProvider, helpers);
            } else if (capability == PresenceCapability.class) {
                newCap = new PresenceCapability(handler, stateDescriptionProvider, helpers);
            } else if (capability == MeasureCapability.class) {
                newCap = new MeasureCapability(handler, helpers);
            } else if (capability == ChannelHelperCapability.class) {
                newCap = new ChannelHelperCapability(handler, helpers);
            }
            if (newCap != null) {
                handler.getCapabilities().put(newCap);
            } else {
                logger.warn("No factory entry defined to create Capability : {}", capability);
            }
        });

        return (BaseThingHandler) handler;
    }
}
