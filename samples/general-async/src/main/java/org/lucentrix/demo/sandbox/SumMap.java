package org.lucentrix.demo.sandbox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class SumMap {

    public static void main(String[] args) {

        Map<String, BiFunction<Integer, Integer, Integer>> functionMap = new HashMap<>();
        functionMap.put("sum", (x, y) -> x + y);
        functionMap.put("subtract", (x, y) -> x - y);

        int result = functionMap.get("sum").apply(10, 4);
        System.out.println(result);
    }
}
