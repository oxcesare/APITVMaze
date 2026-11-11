package com.mx.tvmazemiddleware.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAnnotationsGuardTest {

    @Test
    void soloLasTresLecturasDeTVClientTienenRetry() throws ClassNotFoundException {
        Set<String> metodosConRetry = new HashSet<>();

        for (Class<?> clase : clasesDeProduccion()) {
            // Guardia: Ninguna clase debe tener @Retry a nivel de clase
            assertThat(AnnotatedElementUtils.hasAnnotation(clase, Retry.class))
                    .as("La clase %s no debe tener @Retry a nivel de clase", clase.getSimpleName())
                    .isFalse();

            for (Method method : clase.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue; // Ignorar métodos generados por el compilador/lambdas
                }
                if (AnnotatedElementUtils.hasAnnotation(method, Retry.class)) {
                    metodosConRetry.add(clase.getSimpleName() + "#" + method.getName());
                }
            }
        }

        // Validación exacta de arquitectura
        assertThat(metodosConRetry)
                .as("El conjunto de métodos con @Retry debe coincidir exactamente con las tres lecturas permitidas")
                .containsExactlyInAnyOrder(
                        "TVClient#getShowById",
                        "TVClient#searchShows",
                        "TVClient#getEpisodeById"
                );
    }

    private static Set<Class<?>> clasesDeProduccion() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        Set<Class<?>> clases = new HashSet<>();

        for (BeanDefinition bd : scanner.findCandidateComponents("com.mx.tvmazemiddleware")) {
            Class<?> clase = Class.forName(bd.getBeanClassName());
            if (!clase.getSimpleName().endsWith("Test")) {
                clases.add(clase);
            }
        }
        return clases;
    }

    @ParameterizedTest(name = "El método {0}.{1} no debe tener @Retry por seguridad de escritura")
    @CsvSource({
            "com.mx.tvmazemiddleware.service.RatingService, saveRating",
            "com.mx.tvmazemiddleware.service.RatingService, deleteById",
            "com.mx.tvmazemiddleware.security.AuthService, login",
            "com.mx.tvmazemiddleware.service.TokenService, issue"
    })
    void ningunaEscrituraTieneRetry(String className, String methodName) throws Exception {
        Class<?> clazz = Class.forName(className);

        // 1. Validar que la clase completa no tenga @Retry a nivel de tipo
        assertThat(AnnotatedElementUtils.hasAnnotation(clazz, Retry.class))
                .as("La clase de escritura %s no debe tener @Retry a nivel de clase", clazz.getSimpleName())
                .isFalse();

        // 2. Validar todas las sobrecargas del método
        List<Method> metodos = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .toList();

        assertThat(metodos)
                .as("El método %s debe existir en la clase %s", methodName, clazz.getSimpleName())
                .isNotEmpty();

        // 3. Validar explícitamente que ninguno tenga @Retry
        assertThat(metodos)
                .as("¡ALERTA DE ARQUITECTURA! Las escrituras no se reintentan. El método crítico '%s' en '%s' tiene @Retry y causaría duplicidad de datos.",
                        methodName, clazz.getSimpleName())
                .noneMatch(m -> AnnotatedElementUtils.hasAnnotation(m, Retry.class));
    }

    @Test
    void ningunaClaseLoLlevaANivelDeClase() throws ClassNotFoundException {
        for (Class<?> clase : clasesDeProduccion()) {
            assertThat(AnnotatedElementUtils.hasAnnotation(clase, Retry.class))
                    .as("¡ALERTA DE ARQUITECTURA! La clase '%s' tiene @Retry a nivel de clase, lo cual aplicaría reintentos de forma implícita a todos sus métodos.",
                            clase.getSimpleName())
                    .isFalse();
        }
    }
}
