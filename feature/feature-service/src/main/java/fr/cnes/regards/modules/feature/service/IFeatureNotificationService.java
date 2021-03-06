/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.feature.service;

import java.util.List;
import java.util.Set;

import fr.cnes.regards.modules.feature.domain.request.AbstractFeatureRequest;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureNotificationRequestEvent;

/**
 * Service for notify {@link Feature}
 * @author Kevin Marchois
 *
 */
public interface IFeatureNotificationService extends IAbstractFeatureService {

    /**
     * Register notification requests in database for further processing from incoming request events
     */
    int registerRequests(List<FeatureNotificationRequestEvent> events);

    int sendToNotifier();

    void handleNotificationSuccess(Set<AbstractFeatureRequest> success);

    void handleNotificationError(Set<AbstractFeatureRequest> errorRequest);
}
