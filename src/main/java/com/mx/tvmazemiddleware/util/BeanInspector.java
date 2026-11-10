package com.mx.tvmazemiddleware.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@ConditionalOnProperty(name = "app.bean-inspector.enabled", havingValue = "true", matchIfMissing = false)
public class BeanInspector {

    @Bean
    CommandLineRunner run(ApplicationContext ctx) {
        return args -> {
            System.out.println("=== LISTA DE BEANS EN EL CONTENEDOR ===");
            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            int total = 0;
            for (String beanName : beanNames) {
                Class<?> type = ctx.getType(beanName);
                // Sin tipo resoluble (null) no se puede afirmar que sea propio: se omite
                if (type == null) {
                    continue;
                }
                // El bean "run" (lambda de este paquete) sí se cuenta: es una clase de mi paquete
                if (type.getName().startsWith("com.mx.tvmazemiddleware")) {
                    System.out.println("Bean: " + beanName);
                    total++;
                }
            }
            System.out.println("Total de beans propios filtrados: " + total);
        };
    }
}