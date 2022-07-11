/*
 * Copyright (c) 2022 VMware Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.observability.micrometer;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.Clock;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Simon Baslé
 */
class MicrometerObservationListenerTest {

	TestObservationRegistry                    registry;
	AtomicLong                                 virtualClockTime;
	Clock                                      virtualClock;
	MicrometerObservationListenerConfiguration configuration;
	ContextView                                subscriberContext;

	@BeforeEach
	void initRegistry() {
		registry = TestObservationRegistry.create();
		virtualClockTime = new AtomicLong();
		virtualClock = new Clock() {
			@Override
			public long wallTime() {
				return virtualClockTime.get();
			}

			@Override
			public long monotonicTime() {
				return virtualClockTime.get();
			}
		};
		configuration = new MicrometerObservationListenerConfiguration(
			"testName",
			KeyValues.of("testTag1", "testTagValue1","testTag2", "testTagValue2"),
			registry,
			false);
		subscriberContext = Context.of("contextKey", "contextValue");
	}

	@Test
	void whenStartedFluxWithDefaultName() {
		configuration = new MicrometerObservationListenerConfiguration(
			Micrometer.DEFAULT_METER_PREFIX,
			//note: "type" key is added by MicrometerObservationListenerConfiguration#fromFlux (which is tested separately)
			KeyValues.of("testTag1", "testTagValue1","testTag2", "testTagValue2"),
			registry,
			false);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		assertThat(listener.valued).as("valued").isFalse();
		assertThat(listener.subscribeToTerminalObservation)
			.as("subscribeToTerminalObservation field")
			.isNotNull();
		assertThat(registry).as("before start").doesNotHaveAnyObservation();

		listener.doFirst(); // forces observation start

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("reactor.observation.flow")
			.as("subscribeToTerminalObservation")
			.hasBeenStarted()
			.isNotStopped()
			.hasLowCardinalityKeyValue("testTag1", "testTagValue1")
			.hasLowCardinalityKeyValue("testTag2", "testTagValue2");
	}

	@Test
	void whenStartedFluxWithCustomName() {
		configuration = new MicrometerObservationListenerConfiguration(
			"testName",
			//note: "type" key is added by MicrometerObservationListenerConfiguration#fromFlux (which is tested separately)
			KeyValues.of("testTag1", "testTagValue1","testTag2", "testTagValue2"),
			registry,
			false);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		assertThat(listener.valued).as("valued").isFalse();
		assertThat(listener.subscribeToTerminalObservation)
			.as("subscribeToTerminalObservation field")
			.isNotNull();
		assertThat(registry).as("no observation started").doesNotHaveAnyObservation();

		listener.doFirst(); // forces observation start

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("testName.observation.flow")
			.as("subscribeToTerminalObservation")
			.hasBeenStarted()
			.isNotStopped()
			.hasLowCardinalityKeyValue("testTag1", "testTagValue1")
			.hasLowCardinalityKeyValue("testTag2", "testTagValue2");
	}

	@Test
	void whenStartedMono() {
		configuration = new MicrometerObservationListenerConfiguration(
			Micrometer.DEFAULT_METER_PREFIX,
			//note: "type" key is added by MicrometerObservationListenerConfiguration#fromMono (which is tested separately)
			KeyValues.of("testTag1", "testTagValue1","testTag2", "testTagValue2"),
			registry,
			true);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		assertThat(listener.valued).as("valued").isFalse();
		assertThat(listener.subscribeToTerminalObservation)
			.as("subscribeToTerminalObservation field")
			.isNotNull();
		assertThat(registry).as("no observation started").doesNotHaveAnyObservation();

		listener.doFirst(); // forces observation start

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("reactor.observation.flow")
			.as("subscribeToTerminalObservation")
			.hasBeenStarted()
			.isNotStopped()
			.hasLowCardinalityKeyValue("testTag1", "testTagValue1")
			.hasLowCardinalityKeyValue("testTag2", "testTagValue2");
	}

	@Test
	void tapFromFluxWithTags() {
		Flux<Integer> flux = Flux.just(1)
			.name("testFlux")
			.tag("testTag1", "testTagValue1")
			.tag("testTag2", "testTagValue2")
			.tap(new MicrometerObservationListenerFactory<>(registry));

		assertThat(registry).as("before subscription").doesNotHaveAnyObservation();

		flux.blockLast();

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("testFlux.observation.flow")
			.as("subscribeToTerminalObservation")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("testTag1", "testTagValue1")
			.hasLowCardinalityKeyValue("testTag2", "testTagValue2")
			.hasLowCardinalityKeyValue("type", "Flux")
			.hasLowCardinalityKeyValue("status", "completed")
			.hasKeyValuesCount(4);
	}

	@Test
	void tapFromMonoWithTags() {
		Mono<Integer> mono = Mono.just(1)
			.name("testMono")
			.tag("testTag1", "testTagValue1")
			.tag("testTag2", "testTagValue2")
			.tap(new MicrometerObservationListenerFactory<>(registry));

		assertThat(registry).as("before subscription").doesNotHaveAnyObservation();

		mono.block();

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("testMono.observation.flow")
			.as("subscribeToTerminalObservation")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("testTag1", "testTagValue1")
			.hasLowCardinalityKeyValue("testTag2", "testTagValue2")
			.hasLowCardinalityKeyValue("type", "Mono")
			.hasLowCardinalityKeyValue("status", "completed")
			.hasKeyValuesCount(4);
	}

	@Test
	void observationStoppedByCancellation() {
		configuration = new MicrometerObservationListenerConfiguration(
			"flux",
			KeyValues.of("forcedType", "Flux"),
			registry,
			false);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		listener.doFirst(); // forces observation start
		listener.doOnCancel(); // stops via cancellation

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("flux.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("forcedType", "Flux")
			.hasLowCardinalityKeyValue("status", "cancelled")
			.hasKeyValuesCount(2)
			.doesNotHaveError();
	}

	@Test
	void observationStoppedByCompleteEmpty() {
		configuration = new MicrometerObservationListenerConfiguration(
			"emptyFlux",
			KeyValues.of("forcedType", "Flux"),
			registry,
			false);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		listener.doFirst(); // forces observation start
		listener.doOnComplete(); // stops via completion (empty)

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("emptyFlux.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("forcedType", "Flux")
			.hasLowCardinalityKeyValue("status", "completedEmpty")
			.hasKeyValuesCount(2)
			.doesNotHaveError();
	}

	@Test
	void observationStoppedByCompleteWithValues() {
		configuration = new MicrometerObservationListenerConfiguration(
			"flux",
			KeyValues.of("forcedType", "Flux"),
			registry,
			false);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		listener.doFirst(); // forces observation start
		listener.doOnNext(1);
		listener.doOnComplete(); // stops via completion

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("flux.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("forcedType", "Flux")
			.hasLowCardinalityKeyValue("status", "completed")
			.hasKeyValuesCount(2)
			.doesNotHaveError();
	}

	@Test
	void observationMonoStoppedByOnNext() {
		configuration = new MicrometerObservationListenerConfiguration(
			"valuedMono",
			KeyValues.of("forcedType", "Mono"),
			registry,
			true);

		final String expectedStatus = "completedOnNext";

		//we use a test-oriented constructor to force the onNext completion case to have a different tag value
		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration, expectedStatus);

		listener.doFirst(); // forces observation start
		listener.doOnNext(1); // emulates onNext, should stop observation

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("valuedMono.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("forcedType", "Mono")
			.hasLowCardinalityKeyValue("status", expectedStatus)
			.doesNotHaveError();

		listener.doOnComplete();

		//if the test in doOnComplete was bugged, the status key would be associated with "completed" now
		assertThat(registry)
			.hasSingleObservationThat()
			.as("post-doOnComplete")
			.hasLowCardinalityKeyValue("status", expectedStatus);
	}

	@Test
	void observationEmptyMonoStoppedByOnComplete() {
		configuration = new MicrometerObservationListenerConfiguration(
			"emptyMono",
			KeyValues.of("forcedType", "Mono"),
			registry,
			true);

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		listener.doFirst(); // forces observation start
		listener.doOnComplete(); // stops via completion (empty)

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("emptyMono.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("forcedType", "Mono")
			.hasLowCardinalityKeyValue("status", "completedEmpty")
			.doesNotHaveError();
	}

	@Test
	void observationStoppedByError() {
		configuration = new MicrometerObservationListenerConfiguration(
			"errorFlux",
			KeyValues.of("forcedType", "Flux"),
			registry,
			false);
		IllegalStateException exception = new IllegalStateException("observationStoppedByError");

		MicrometerObservationListener<Integer> listener = new MicrometerObservationListener<>(subscriberContext, configuration);

		listener.doFirst(); // forces observation start
		listener.doOnError(exception); // stops via onError

		assertThat(registry)
			.hasSingleObservationThat()
			.hasNameEqualTo("errorFlux.observation.flow")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasOnlyKeys("forcedType", "status")
			.hasLowCardinalityKeyValue("forcedType", "Flux")
			.hasLowCardinalityKeyValue("status", "error")
			.hasError(exception);
	}
}