package com.mx.tvmazemiddleware.util;

public class Utilerias {

    public static void main(String[] args) {

        System.out.println(describirDia("Lunes"));
        System.out.println(describirDia(null));
        System.out.println(describirDia(1));

    }


    /**
     * Los type patterns permiten hacer un match de un tipo
     * de dato y además asignarlo a una variable para poder usarlo dentro del bloque de código.
     *
     * when permite agregar condiciones adicionales al patrón de tipo.
     * @param dia
     * @return
     */
    static String describirDia(Object dia) {
        return switch (dia) {
            case String s when s.equalsIgnoreCase("Sabado") || s.equalsIgnoreCase("Domingo") ->
                    "Fin de semana (Texto): " + s;
            case String s -> "Día hábil (Texto): " + s;
            case Integer i when i >= 1 && i <= 5 -> "Día hábil (Numérico): " + i;
            case Integer i when i == 6 || i == 7 -> "Fin de semana (Numérico): " + i;
            case null, default -> "Día no válido";
        };
    }

}
