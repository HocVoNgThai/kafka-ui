package io.kafbat.ui.service;

import static io.kafbat.ui.service.ReactiveAdminClient.toMonoWithExceptionFilter;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ReactiveAdminClientAuthorizationTest {

  @Test
  void skipsUnauthorizedGroupsAndPreservesAccessibleResults() {
    var denied = new KafkaFutureImpl<String>();
    denied.completeExceptionally(new GroupAuthorizationException("restricted-group"));

    Map<String, KafkaFuture<String>> results = Map.of(
        "restricted-group", denied,
        "visible-group", KafkaFuture.completedFuture("description")
    );

    StepVerifier.create(toMonoWithExceptionFilter(results, GroupAuthorizationException.class))
        .assertNext(result -> assertThat(result).containsExactlyEntriesOf(
            Map.of("visible-group", "description")))
        .verifyComplete();
  }

  @Test
  void returnsEmptyMapWhenAllGroupsAreUnauthorized() {
    var denied = new KafkaFutureImpl<String>();
    denied.completeExceptionally(new GroupAuthorizationException("restricted-group"));

    StepVerifier.create(toMonoWithExceptionFilter(
        Map.of("restricted-group", denied), GroupAuthorizationException.class))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }

  @Test
  void propagatesUnrelatedFailures() {
    var failure = new TimeoutException("request timed out");
    var failed = new KafkaFutureImpl<String>();
    failed.completeExceptionally(failure);

    StepVerifier.create(toMonoWithExceptionFilter(
        Map.of("visible-group", failed), GroupAuthorizationException.class))
        .expectErrorMatches(error -> error == failure)
        .verify();
  }
}
