/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.feature.service.flow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.amqp.batch.IBatchHandler;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.feature.dto.RequestInfo;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureUpdateRequestEvent;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.framework.amqp.event.IRequestDeniedService;
import fr.cnes.regards.modules.feature.service.IFeatureUpdateService;
import fr.cnes.regards.modules.feature.service.conf.FeatureConfigurationProperties;

/**
 * This handler absorbs the incoming creation request flow
 *
 * @author Marc SORDI
 *
 */
@Component
@Profile("!nohandler")
public class FeatureUpdateRequestEventHandler extends AbstractFeatureRequestEventHandler<FeatureUpdateRequestEvent>
        implements IBatchHandler<FeatureUpdateRequestEvent>, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureUpdateRequestEventHandler.class);

    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private FeatureConfigurationProperties confProperties;

    @Autowired
    private ISubscriber subscriber;

    @Autowired
    private IFeatureUpdateService featureService;

    public FeatureUpdateRequestEventHandler() {
        super(FeatureUpdateRequestEvent.class);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        subscriber.subscribeTo(FeatureUpdateRequestEvent.class, this);
    }

    @Override
    public boolean validate(String tenant, FeatureUpdateRequestEvent message) {
        // FIXME
        return true;
    }

    @Override
    public void handleBatch(String tenant, List<FeatureUpdateRequestEvent> messages) {
        try {
            runtimeTenantResolver.forceTenant(tenant);
            long start = System.currentTimeMillis();
            RequestInfo<FeatureUniformResourceName> requestInfo = featureService.registerRequests(messages);
            LOGGER.info("{} granted request(s) and {} denied update request(s) registered in {} ms",
                        requestInfo.getGranted().size(), requestInfo.getDenied().keySet().size(),
                        System.currentTimeMillis() - start);
        } finally {
            runtimeTenantResolver.clearTenant();
        }
    }

    @Override
    public int getBatchSize() {
        return confProperties.getBatchSize();
    }

    @Override
    public long getReceiveTimeout() {
        return confProperties.getBatchReceiveTimeout();
    }

    @Override
    public IRequestDeniedService getFeatureService() {
        return featureService;
    }

}
