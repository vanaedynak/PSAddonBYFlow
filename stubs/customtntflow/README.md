# CustomTNTFlow stub API

Эти исходники используются только для сборки заглушечного `CustomTNTFlow-1.0.0.jar`, который лежит в каталоге `libs/` и нужен
Gradle для компиляции аддона. Здесь представлены минимальные классы API и пустые заглушки Bukkit, которых достаточно для 
компиляции.

Чтобы пересобрать JAR локально:

```bash
javac -d build/customtntflow-support stubs/customtntflow/support-src/**/*.java
javac -cp build/customtntflow-support -d build/customtntflow-api stubs/customtntflow/api-src/**/*.java
jar cf libs/CustomTNTFlow-1.0.0.jar -C build/customtntflow-api .
```

На рабочем сервере замените заглушку реальным файлом `CustomTNTFlow-*.jar`, если у вас есть доступ к оригинальному плагину.
