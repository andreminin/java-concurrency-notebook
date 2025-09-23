package org.lucentrix.demo.async.modern;

import java.sql.SQLException;

public class CallStackDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread thread = new Thread(CallStackDemo::processOrder);
        thread.setName("mcj-thread");
        thread.start();
        thread.join();
    }

    static void processOrder() {
        validateOrderDetails();
    }

    static void validateOrderDetails() {
        checkInventory();
    }

    static void checkInventory() {
        updateDatabase();
    }

    static void updateDatabase() {
        try {
            throw new SQLException("Database connection error");
        } catch (SQLException e) {
            throw new InventoryUpdateException("Database Error: " +
                    "Unable to update inventory", e);
        }
    }

    static class InventoryUpdateException extends RuntimeException {
        public InventoryUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


