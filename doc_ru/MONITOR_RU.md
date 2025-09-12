# Примеры использования monitorenter и monitorexit в байткоде

## 1. Базовый пример synchronized блока

### Исходный Java код


```java
public class SynchronizedExample {
    private final Object lock = new Object();
    private int counter = 0;
    
    public void increment() {
        synchronized(lock) {
            counter++;
            System.out.println("Counter: " + counter);
        }
    }
    
    public synchronized void synchronizedMethod() {
        counter--;
        System.out.println("Counter: " + counter);
    }
}
```

### Эквивалентный код с явным использованием монитора

```java
public class ExplicitMonitorExample {
    private final Object lock = new Object();
    private int counter = 0;
    
    public void increment() {
        // Эквивалент synchronized(lock) {
        monitorenter(lock);
        try {
            counter++;
            System.out.println("Counter: " + counter);
        } finally {
            monitorexit(lock);
        }
    }
    
    // Вспомогательные методы для эмуляции байткод инструкций
    private static void monitorenter(Object monitor) {
        // В реальности это байткод инструкция, но мы эмулируем поведение
        synchronized(monitor) {
            // Ничего не делаем здесь, просто захватываем монитор
        }
    }
    
    private static void monitorexit(Object monitor) {
        // Освобождение происходит автоматически при выходе из блока
    }
}
```

## 2. Пример с генерацией байткода через ASM

### Генерация класса с monitorenter/monitorexit

```java
import org.objectweb.asm.*;

public class BytecodeGenerator extends ClassLoader {
    
    public Class<?> generateSynchronizedClass() throws Exception {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        // Создаем класс
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "GeneratedSyncClass", null, 
                "java/lang/Object", null);
        
        // Поле counter
        FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE, "counter", "I", null, null);
        fv.visitEnd();
        
        // Конструктор
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        
        // Метод с synchronized блоком
        generateSynchronizedMethod(cw);
        
        cw.visitEnd();
        
        byte[] bytecode = cw.toByteArray();
        return defineClass("GeneratedSyncClass", bytecode, 0, bytecode.length);
    }
    
    private void generateSynchronizedMethod(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "incrementSync", "()V", null, null);
        mv.visitCode();
        
        // monitorenter - захват монитора this
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETFIELD, "GeneratedSyncClass", "counter", "I");
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "GeneratedSyncClass", "counter", "I");
        
        // monitorenter (байткод инструкция 0xC2)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.MONITORENTER);
        
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label finallyHandler = new Label();
        
        mv.visitTryCatchBlock(tryStart, tryEnd, finallyHandler, null);
        
        mv.visitLabel(tryStart);
        // Инкремент counter внутри synchronized блока
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETFIELD, "GeneratedSyncClass", "counter", "I");
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitFieldInsn(Opcodes.PUTFIELD, "GeneratedSyncClass", "counter", "I");
        
        // Вызов System.out.println
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mv.visitLdcInsn("Counter: ");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", 
                          "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "GeneratedSyncClass", "counter", "I");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", 
                          "(I)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", 
                          "()Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", 
                          "(Ljava/lang/String;)V", false);
        
        mv.visitLabel(tryEnd);
        
        // monitorexit при нормальном завершении (байткод инструкция 0xC3)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.MONITOREXIT);
        mv.visitJumpInsn(Opcodes.GOTO, finallyHandler);
        
        // Обработчик исключений
        mv.visitLabel(finallyHandler);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
        
        // monitorexit при исключении
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.MONITOREXIT);
        mv.visitInsn(Opcodes.ATHROW); // Повторно бросаем исключение
        
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(4, 1);
        mv.visitEnd();
    }
}
```

## 3. Анализ байткода существующих классов

### Инструмент для анализа synchronized блоков

```java
import java.lang.reflect.Method;
import javassist.*;
import javassist.bytecode.*;

public class BytecodeAnalyzer {
    
    public static void analyzeSynchronizedMethods(Class<?> clazz) throws Exception {
        System.out.println("Analyzing class: " + clazz.getName());
        
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.get(clazz.getName());
        ClassFile classFile = ctClass.getClassFile();
        MethodInfo[] methods = classFile.getMethods();
        
        for (MethodInfo methodInfo : methods) {
            analyzeMethodForMonitors(clazz, methodInfo);
        }
    }
    
    private static void analyzeMethodForMonitors(Class<?> clazz, MethodInfo methodInfo) {
        CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
        if (codeAttribute == null) return;
        
        CodeIterator iterator = codeAttribute.iterator();
        while (iterator.hasNext()) {
            int pos = iterator.next();
            int opcode = iterator.byteAt(pos);
            
            if (opcode == Opcode.MONITORENTER) {
                System.out.println("MONITORENTER found in method: " + methodInfo.getName() + " at position: " + pos);
            } else if (opcode == Opcode.MONITOREXIT) {
                System.out.println("MONITOREXIT found in method: " + methodInfo.getName() + " at position: " + pos);
            }
        }
    }
    
    public static boolean hasSynchronizedBlocks(Method method) {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(method.getDeclaringClass().getName());
            CtMethod ctMethod = ctClass.getDeclaredMethod(method.getName());
            CodeAttribute codeAttribute = ctMethod.getMethodInfo().getCodeAttribute();
            
            if (codeAttribute == null) return false;
            
            CodeIterator iterator = codeAttribute.iterator();
            while (iterator.hasNext()) {
                int pos = iterator.next();
                int opcode = iterator.byteAt(pos);
                
                if (opcode == Opcode.MONITORENTER || opcode == Opcode.MONITOREXIT) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
```

## 4. Практический пример с ручным управлением монитором

### Эмуляция низкоуровневого управления монитором

```java
public class ManualMonitorControl {
    private final Object monitor = new Object();
    private int sharedResource = 0;
    private boolean monitorEntered = false;
    
    // Аналог monitorenter
    public void enterMonitor() {
        synchronized(monitor) {
            monitorEntered = true;
            System.out.println("Monitor entered by: " + Thread.currentThread().getName());
        }
    }
    
    // Аналог monitorexit
    public void exitMonitor() {
        synchronized(monitor) {
            if (monitorEntered) {
                monitorEntered = false;
                System.out.println("Monitor exited by: " + Thread.currentThread().getName());
                monitor.notify(); // Пробуждаем ожидающие потоки
            }
        }
    }
    
    // Работа с разделяемым ресурсом
    public void accessResource() {
        enterMonitor();
        try {
            // Критическая секция
            sharedResource++;
            System.out.println("Resource accessed: " + sharedResource + " by " + 
                             Thread.currentThread().getName());
            Thread.sleep(100); // Имитация работы
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exitMonitor();
        }
    }
    
    // Ожидание на мониторе (аналог wait())
    public void waitOnMonitor() throws InterruptedException {
        synchronized(monitor) {
            while (!monitorEntered) {
                System.out.println("Waiting for monitor...");
                monitor.wait();
            }
            System.out.println("Wait completed for: " + Thread.currentThread().getName());
        }
    }
}
```

## 5. Пример использования с многопоточностью

### Тестирование synchronized поведения

```java
public class MonitorTest {
    public static void main(String[] args) throws Exception {
        ManualMonitorControl monitorControl = new ManualMonitorControl();
        
        // Создаем несколько потоков для тестирования
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    monitorControl.accessResource();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Thread-" + i);
        }
        
        // Запускаем все потоки
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Ожидаем завершения
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("Final resource value: " + monitorControl.sharedResource);
    }
}
```

## 5. Пример использования с многопоточностью

### Тестирование synchronized поведения

```java
public class MonitorTest {
    public static void main(String[] args) throws Exception {
        ManualMonitorControl monitorControl = new ManualMonitorControl();
        
        // Создаем несколько потоков для тестирования
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 3; j++) {
                    monitorControl.accessResource();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Thread-" + i);
        }
        
        // Запускаем все потоки
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Ожидаем завершения
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("Final resource value: " + monitorControl.sharedResource);
    }
}
```

## 6. Визуализация байткода synchronized методов

### Утилита для дизассемблирования


```java
import java.io.*;
import java.nio.file.*;

public class BytecodeDisassembler {
    
    public static void disassembleClass(Class<?> clazz) throws IOException {
        String className = clazz.getName();
        String classFileName = className.replace('.', '/') + ".class";
        
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(classFileName)) {
            if (is != null) {
                byte[] bytecode = is.readAllBytes();
                printBytecodeWithMonitors(bytecode, className);
            }
        }
    }
    
    private static void printBytecodeWithMonitors(byte[] bytecode, String className) {
        System.out.println("=== Bytecode for " + className + " ===");
        
        for (int i = 0; i < bytecode.length; i++) {
            int opcode = bytecode[i] & 0xFF;
            
            if (opcode == 0xC2) { // MONITORENTER
                System.out.printf("%04d: MONITORENTER\n", i);
            } else if (opcode == 0xC3) { // MONITOREXIT
                System.out.printf("%04d: MONITOREXIT\n", i);
            } else if (isInterestingOpcode(opcode)) {
                System.out.printf("%04d: %s\n", i, getOpcodeName(opcode));
            }
        }
    }
    
    private static boolean isInterestingOpcode(int opcode) {
        // Только некоторые интересные опкоды для демонстрации
        return opcode == 0x2A || opcode == 0xB1 || opcode == 0xB2 || 
               opcode == 0x59 || opcode == 0x3C || opcode == 0x84;
    }
    
    private static String getOpcodeName(int opcode) {
        switch (opcode) {
            case 0x2A: return "ALOAD_0";
            case 0xB1: return "RETURN";
            case 0xB2: return "GETSTATIC";
            case 0x59: return "DUP";
            case 0x3C: return "ICONST_1";
            case 0x84: return "IADD";
            default: return String.format("0x%02X", opcode);
        }
    }
}
```

## 7. Использование для анализа

### Главный класс для демонстрации


```java
public class MonitorExampleDemo {
    public static void main(String[] args) throws Exception {
        // Анализ существующего класса
        BytecodeAnalyzer.analyzeSynchronizedMethods(SynchronizedExample.class);
        
        // Генерация класса с monitorenter/monitorexit
        BytecodeGenerator generator = new BytecodeGenerator();
        Class<?> generatedClass = generator.generateSynchronizedClass();
        
        // Создание экземпляра и вызов метода
        Object instance = generatedClass.getDeclaredConstructor().newInstance();
        Method incrementMethod = generatedClass.getMethod("incrementSync");
        incrementMethod.invoke(instance);
        
        // Дизассемблирование
        BytecodeDisassembler.disassembleClass(SynchronizedExample.class);
        
        // Тестирование ручного управления монитором
        ManualMonitorControlTest.test();
    }
}
```

Эти примеры показывают различные аспекты работы с `monitorenter` и `monitorexit` - от низкоуровневого байткода до высокоуровневых абстракций в Java.


```java

```