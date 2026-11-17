package com.luany.inventory.service;

import java.nio.charset.StandardCharsets;

public final class PoliticaPassword {

    private PoliticaPassword() {
    }

    public static boolean cumple(String password) {
        if (password == null || password.length() < 12 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return password.codePoints().anyMatch(Character::isUpperCase)
                && password.codePoints().anyMatch(Character::isLowerCase)
                && password.codePoints().anyMatch(Character::isDigit)
                && password.codePoints().anyMatch(caracter ->
                        !Character.isLetterOrDigit(caracter) && !Character.isWhitespace(caracter));
    }

    public static void validar(String password) {
        if (!cumple(password)) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener entre 12 y 72 caracteres e incluir mayúsculas, "
                            + "minúsculas, números y símbolos.");
        }
    }
}
