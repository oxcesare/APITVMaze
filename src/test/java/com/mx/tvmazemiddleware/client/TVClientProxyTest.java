package com.mx.tvmazemiddleware.client;

import com.mx.tvmazemiddleware.exception.TvMazeUnavailableException;
import com.mx.tvmazemiddleware.repository.RatingRepository;
import com.mx.tvmazemiddleware.repository.ShowRepository;
import com.mx.tvmazemiddleware.service.TVMazeService;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TVClientProxyTest {

    private MockRestServiceServer server;
    private RestClient restClient;
    private ApplicationContextRunner runner;
    private ShowRepository showRepository;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        showRepository = mock(ShowRepository.class);

        runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class, RetryAutoConfiguration.class))
                .withUserConfiguration(ResilienceConfig.class)
                .withBean(RestClient.class, () -> restClient)
                .withBean(TVClient.class)
                .withBean(ShowRepository.class, () -> showRepository)
                .withBean(RatingRepository.class, () -> mock(RatingRepository.class))
                .withBean(TVMazeService.class)
                .withPropertyValues(
                        "resilience4j.retry.instances.tvmaze.max-attempts=3",
                        "resilience4j.retry.instances.tvmaze.retry-exceptions[0]=com.mx.tvmazemiddleware.exception.TvMazeTimeoutException",
                        "resilience4j.retry.instances.tvmaze.retry-exceptions[1]=com.mx.tvmazemiddleware.exception.TvMazeUnavailableException",
                        "tvmaze.retry.initial-interval=10ms",
                        "tvmaze.retry.multiplier=2.0",
                        "tvmaze.retry.jitter=0.0");
    }


    @Test
    void elClienteEsUnProxyQueReintentaDesdeElServicio() {

        org.mockito.Mockito.when(showRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Expectativas en el servidor simulado para /shows/1
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString("/shows/1")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString("/shows/1")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"id\":1,\"name\":\"Girls\"}", MediaType.APPLICATION_JSON));

        runner.run(context -> {
            assertThat(context).hasNotFailed();

            TVClient tvClientBean = context.getBean(TVClient.class);
            assertThat(AopUtils.isAopProxy(tvClientBean)).isTrue();
            assertThat(AopUtils.getTargetClass(tvClientBean)).isEqualTo(TVClient.class);

            TVMazeService tvMazeService = context.getBean(TVMazeService.class);
            var show = tvMazeService.getShowById(1L);

            assertThat(show).isNotNull();
            assertThat(show.id()).isEqualTo(1L);

            server.verify();
        });
    }




    @Test
    void unClienteCreadoConNewNoReintenta() {
        // Control: demustra que una instancia manual con 'new' no aplica el reintento del proxy AOP
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString("/shows/1")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        TVClient manualClient = new TVClient(restClient);

        assertThatThrownBy(() -> manualClient.getShowById(1L))
                .isInstanceOf(TvMazeUnavailableException.class);

        server.verify();
    }
}
