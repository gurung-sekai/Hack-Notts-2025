package com.hacknotts.extractor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class AcceptanceTestRunner {
    private AcceptanceTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        List<Class<?>> testClasses = List.of(ExtractorAcceptanceTest.class);
        List<String> failures = new ArrayList<>();
        for (Class<?> testClass : testClasses) {
            Object instance = testClass.getDeclaredConstructor().newInstance();
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Test.class)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (InvocationTargetException ex) {
                        Throwable cause = ex.getCause();
                        failures.add(testClass.getSimpleName() + "." + method.getName() + " -> " + cause);
                    }
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Acceptance tests failed:\n" + String.join("\n", failures));
        }
    }
}
