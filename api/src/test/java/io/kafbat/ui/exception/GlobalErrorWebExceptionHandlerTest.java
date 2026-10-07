package io.kafbat.ui.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

import io.kafbat.ui.model.ErrorResponseDTO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GlobalErrorWebExceptionHandlerTest {

  @Test
  void excludesStackTracesByDefault() {
    var response = errorResponse(null, "/unexpected", HttpStatus.INTERNAL_SERVER_ERROR);

    assertThat(response.getStackTrace()).isEqualTo("REDACTED FOR SECURITY REASONS");
    assertThat(response.getMessage()).isEqualTo("test failure");
    assertThat(response.getRequestId()).isNotEmpty();
  }

  @Test
  void explicitTrueExcludesStackTraces() {
    var response = errorResponse(true, "/unexpected", HttpStatus.INTERNAL_SERVER_ERROR);

    assertThat(response.getStackTrace()).isEqualTo("REDACTED FOR SECURITY REASONS");
  }

  @Test
  void explicitFalsePreservesDiagnosticStackTraces() {
    var response = errorResponse(false, "/unexpected", HttpStatus.INTERNAL_SERVER_ERROR);

    assertThat(response.getStackTrace()).contains("java.lang.IllegalStateException: test failure");
  }

  @Test
  void excludesStackTracesFromStatusErrorsByDefault() {
    var response = errorResponse(null, "/bad-request", HttpStatus.BAD_REQUEST);

    assertThat(response.getStackTrace()).isEqualTo("REDACTED FOR SECURITY REASONS");
    assertThat(response.getMessage()).isEqualTo("invalid request");
  }

  private ErrorResponseDTO errorResponse(Boolean excludeStackTraces, String path, HttpStatus status) {
    try (var context = new AnnotationConfigApplicationContext()) {
      if (excludeStackTraces != null) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
            Map.of("http.error.excludeStackTraces", excludeStackTraces.toString())));
      }
      context.register(TestConfiguration.class);
      context.refresh();
      return WebTestClient.bindToApplicationContext(context).build()
          .get().uri(path).exchange()
          .expectStatus().isEqualTo(status)
          .expectBody(ErrorResponseDTO.class).returnResult().getResponseBody();
    }
  }

  @Configuration
  @EnableWebFlux
  @Import(GlobalErrorWebExceptionHandler.class)
  static class TestConfiguration {

    @Bean
    static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
      return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    ErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes();
    }

    @Bean
    RouterFunction<ServerResponse> routes() {
      return RouterFunctions.route(GET("/unexpected"), request -> Mono.error(new IllegalStateException("test failure")))
          .andRoute(GET("/bad-request"),
              request -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid request")));
    }
  }
}
