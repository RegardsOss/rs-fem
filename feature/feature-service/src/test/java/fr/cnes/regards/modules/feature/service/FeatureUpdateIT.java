/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.feature.dao.IFeatureUpdateRequestRepository;
import fr.cnes.regards.modules.feature.domain.FeatureEntity;
import fr.cnes.regards.modules.feature.domain.request.FeatureDeletionRequest;
import fr.cnes.regards.modules.feature.domain.request.FeatureRequestStep;
import fr.cnes.regards.modules.feature.domain.request.FeatureUpdateRequest;
import fr.cnes.regards.modules.feature.domain.request.ILightFeatureUpdateRequest;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.PriorityLevel;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureCreationRequestEvent;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureDeletionRequestEvent;
import fr.cnes.regards.modules.feature.dto.event.in.FeatureUpdateRequestEvent;
import fr.cnes.regards.modules.feature.dto.event.out.RequestState;
import fr.cnes.regards.modules.feature.dto.urn.FeatureIdentifier;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author kevin
 *
 */
@TestPropertySource(
        properties = { "spring.jpa.properties.hibernate.default_schema=feature_update", "regards.amqp.enabled=true",
                "regards.feature.metrics.enabled=true" },
        locations = { "classpath:regards_perf.properties", "classpath:batch.properties",
                "classpath:metrics.properties" })
@ActiveProfiles(value = { "testAmqp", "noscheduler", "nohandler" })
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class FeatureUpdateIT extends AbstractFeatureMultitenantServiceTest {

    @Autowired
    private IFeatureUpdateService featureUpdateService;

    @Autowired
    private IFeatureDeletionService featureDeletionService;

    @Autowired
    private IFeatureUpdateRequestRepository featureUpdateRequestRepository;

    private boolean isToNotify;

    @Override
    public void doInit() {
        // initialize notification
        this.isToNotify = initDefaultNotificationSettings();
    }

    /**
     * Test update scheduler we will create 4 {@link FeatureUpdateRequest}
     * fur1, fur2, fur3, fur4
     * fur2 is already scheduled and fur3 is on the same {@link Feature} that fur2
     * fur4 has a {@link FeatureDeletionRequest} scheduled with the same {@link Feature}
     * When we will call the scheduler it will schedule fur1 only
     * @throws InterruptedException
     */
    @Test
    public void testSchedulerSteps() throws InterruptedException {
        // create features
        List<FeatureCreationRequestEvent> events = super.initFeatureCreationRequestEvent(3, true);
        this.featureCreationService.registerRequests(events);

        this.featureCreationService.scheduleRequests();
        int cpt = 0;
        long featureNumberInDatabase;
        do {
            featureNumberInDatabase = this.featureRepo.count();
            Thread.sleep(1000);
            cpt++;
        } while ((cpt < 100) && (featureNumberInDatabase != 3));
        List<FeatureEntity> entities = super.featureRepo.findAll();
        // features have been created. now lets simulate a deletion of one of them
        FeatureEntity toDelete = entities.get(2);
        FeatureDeletionRequestEvent featureDeletionRequest = FeatureDeletionRequestEvent
                .build("TEST", toDelete.getUrn(), PriorityLevel.NORMAL);
        this.featureDeletionService.registerRequests(Lists.list(featureDeletionRequest));
        this.featureDeletionService.scheduleRequests();
        // simulate an update request on a feature being deleted
        FeatureUpdateRequest fur4 = FeatureUpdateRequest.build(UUID.randomUUID().toString(),
                                                               "owner",
                                                               OffsetDateTime.now(),
                                                               RequestState.GRANTED,
                                                               null,
                                                               toDelete.getFeature(),
                                                               PriorityLevel.NORMAL,
                                                               FeatureRequestStep.LOCAL_DELAYED);

        // simulate an update request
        FeatureEntity toUpdate = entities.get(0);
        FeatureUpdateRequest fur1 = FeatureUpdateRequest.build(UUID.randomUUID().toString(),
                                                               "owner",
                                                               OffsetDateTime.now(),
                                                               RequestState.GRANTED,
                                                               null,
                                                               toUpdate.getFeature(),
                                                               PriorityLevel.NORMAL,
                                                               FeatureRequestStep.LOCAL_DELAYED);

        // simulate an update request that has already been scheduled
        FeatureEntity updatingByScheduler = entities.get(1);
        FeatureUpdateRequest fur2 = FeatureUpdateRequest.build(UUID.randomUUID().toString(),
                                                               "owner",
                                                               OffsetDateTime.now(),
                                                               RequestState.GRANTED,
                                                               null,
                                                               updatingByScheduler.getFeature(),
                                                               PriorityLevel.NORMAL,
                                                               FeatureRequestStep.LOCAL_SCHEDULED);
        // Simulate one more update request that just arrived.
        // this update cannot be scheduled because fur2 is already scheduled and on the same feature
        FeatureUpdateRequest fur3 = FeatureUpdateRequest.build(UUID.randomUUID().toString(),
                                                               "owner",
                                                               OffsetDateTime.now(),
                                                               RequestState.GRANTED,
                                                               null,
                                                               updatingByScheduler.getFeature(),
                                                               PriorityLevel.NORMAL,
                                                               FeatureRequestStep.LOCAL_DELAYED);

        // bypass registration to help simulate the state we want
        fur1 = super.featureUpdateRequestRepo.save(fur1);
        fur2 = super.featureUpdateRequestRepo.save(fur2);
        fur3 = super.featureUpdateRequestRepo.save(fur3);
        fur4 = super.featureUpdateRequestRepo.save(fur4);

        // wait 5 second to delay so that deletion job can be executed
        Thread.sleep(properties.getDelayBeforeProcessing() * 1000);

        this.featureUpdateService.scheduleRequests();

        List<FeatureUpdateRequest> updateRequests = this.featureUpdateRequestRepo.findAll();

        // fur1 and fur2 should be scheduled
        assertEquals(2,
                     updateRequests.stream()
                             .filter(request -> request.getStep().equals(FeatureRequestStep.LOCAL_SCHEDULED)).count());
        // fur3 stay delayed cause a update on the same feature is scheduled and fur4 concern a feature in deletion
        assertEquals(2,
                     updateRequests.stream()
                             .filter(request -> request.getStep().equals(FeatureRequestStep.LOCAL_DELAYED)).count());

        fur4 = super.featureUpdateRequestRepo.findById(fur4.getId()).get();

        assertEquals(RequestState.ERROR, fur4.getState());
    }

    /**
     * Test priority level for feature update we will schedule properties.getMaxBulkSize() {@link FeatureUpdateRequestEvent}
     * with priority set to average plus properties.getMaxBulkSize() /2 {@link FeatureUpdateRequestEvent} with {@link PriorityLevel}
     * to average
     * @throws InterruptedException
     */
    @Test
    public void testFeaturePriority() throws InterruptedException {
        // create features
        int featureToCreateNumber = properties.getMaxBulkSize() + (properties.getMaxBulkSize() / 2);
        List<FeatureCreationRequestEvent> events = prepareCreationTestData(true,
                                                                           featureToCreateNumber,
                                                                           this.isToNotify,
                                                                           true);

        // create update requests
        List<FeatureUpdateRequestEvent> updateEvents = new ArrayList<>();
        updateEvents = events.stream()
                .map(event -> FeatureUpdateRequestEvent.build("test", event.getMetadata(), event.getFeature()))
                .collect(Collectors.toList());

        // we will set all priority to low for the (properties.getMaxBulkSize() / 2) last event
        for (int i = properties.getMaxBulkSize();
             i < (properties.getMaxBulkSize() + (properties.getMaxBulkSize() / 2)); i++) {
            updateEvents.get(i).getMetadata().setPriority(PriorityLevel.HIGH);
        }

        updateEvents.stream().forEach(event -> {
            event.getFeature().getProperties().clear();
            event.getFeature().setUrn(FeatureUniformResourceName.build(FeatureIdentifier.FEATURE,
                                                                       EntityType.DATA,
                                                                       getDefaultTenant(),
                                                                       UUID.nameUUIDFromBytes(event.getFeature().getId()
                                                                                                      .getBytes()),
                                                                       1));
            event.getFeature().addProperty(IProperty.buildObject("file_characterization",
                                                                 IProperty.buildBoolean("valid", Boolean.FALSE),
                                                                 IProperty.buildDate("invalidation_date",
                                                                                     OffsetDateTime.now())));
        });
        this.featureUpdateService.registerRequests(updateEvents);

        // we wait for delay before schedule
        Thread.sleep((this.properties.getDelayBeforeProcessing() * 1000) + 1000);
        this.featureUpdateService.scheduleRequests();

        // in case notification are active, mock their successes
        if (this.isToNotify) {
            // wait until request are in state LOCAL_TO_BE_NOTIFIED
            int cpt = 0;
            while (cpt < 10 && featureUpdateRequestRepository
                    .findByStepAndRequestDateLessThanEqual(FeatureRequestStep.LOCAL_TO_BE_NOTIFIED,
                                                           OffsetDateTime.now().plusDays(1),
                                                           PageRequest.of(0, properties.getMaxBulkSize())).getSize()
                    < properties.getMaxBulkSize() / 2) {
                Thread.sleep(1000);
                cpt++;
            }
            if (cpt == 10) {
                fail("Update request where not handled in less than 10_000 ms");
            }
            mockNotificationSuccess();
        }

        List<ILightFeatureUpdateRequest> scheduled = this.featureUpdateRequestRepository.findRequestsToSchedule(
                FeatureRequestStep.LOCAL_DELAYED,
                OffsetDateTime.now(),
                PageRequest.of(0, properties.getMaxBulkSize()),
                OffsetDateTime.now()).getContent();
        // half of scheduled should be with priority HIGH
        assertEquals(properties.getMaxBulkSize().intValue() / 2, scheduled.size());
        // check that remaining FeatureUpdateRequest all their their priority not to high
        assertFalse(scheduled.stream().anyMatch(request -> PriorityLevel.HIGH.equals(request.getPriority())));

    }
}
