package org.lucentrix.demo.async.basic;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RetryExample {
    public static void main(String[] args) {
        // Пример 1: Универсальный ретрай
        RetryExecutor retry = new RetryExecutor(3, 1000,
                IOException.class, TimeoutException.class);

        try {
            String result = retry.execute(() -> {
                // Имитация ненадежной операции
                if (Math.random() > 0.3) {
                    throw new IOException("Сетевая ошибка");
                }
                return "Успех!";
            });
            System.out.println("Результат: " + result);
        } catch (Exception e) {
            System.out.println("Финальная ошибка: " + e.getMessage());
        }

        // Пример 2: Экспоненциальная задержка
        ExponentialBackoffRetry backoffRetry = new ExponentialBackoffRetry(5, 100, 5000);

        try {
            backoffRetry.execute(() -> {
                // Имитация API вызова
                double chance = Math.random();
                if (chance > 0.8) {
                    return "Данные получены";
                } else if (chance > 0.4) {
                    throw new RuntimeException("Временная ошибка сервера");
                } else {
                    throw new RuntimeException("Сервис недоступен");
                }
            });
        } catch (Exception e) {
            System.out.println("Не удалось: " + e.getMessage());
        }
    }
}
