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

package fr.cnes.regards.modules.feature.service.settings;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.modules.feature.domain.settings.FeatureNotificationSettings;

/**
 * Service to handle optional notifications
 * @author Iliana Ghazali
 */

public interface IFeatureNotificationSettingsService {

    /**
     * Initialize new {@link FeatureNotificationSettings} in the repository
     */
    void initNotificationSettings();

    /**
     * Retrieve {@link FeatureNotificationSettings}. If they do not exist, new settings are created.
     */
    FeatureNotificationSettings retrieve();

    /**
     * Update {@link FeatureNotificationSettings}
     */
    FeatureNotificationSettings update(FeatureNotificationSettings pFeatureNotificationSettings) throws EntityNotFoundException;

    void resetSettings();
}