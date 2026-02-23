# HybridAssistant (Android)

Полноценный стартовый проект Android-ассистента с голосом + чатом + управлением устройством.

## Что уже реализовано

- Переименование проекта в **HybridAssistant**.
- Package: `com.hybridassistant`.
- Экран чата:
  - текстовый ввод;
  - голосовой ввод (SpeechRecognizer);
  - кнопка `Copy` на каждом сообщении.
- Экран моделей (10 LLM):
  - кнопки `Скачать / Выбрать / Удалить`.
- Реальный менеджер загрузок моделей через Android `DownloadManager`.
- Интеграция JNI-моста для `llama.cpp`:
  - `app/src/main/cpp/hybridassistant_jni.cpp`
  - `app/src/main/cpp/CMakeLists.txt`
- Управление устройством через интенты:
  - открыть ссылку;
  - поиск в интернете;
  - открыть приложение;
  - поиск в YouTube;
  - поиск в Play Market.
- AccessibilityService:
  - отдельная вкладка с переходом в настройки;
  - поддержка команды нажатия по тексту (`ClickByText`).

## Как включить настоящий llama.cpp

1. Положите исходники `llama.cpp` в:
   `app/src/main/cpp/third_party/llama.cpp`
2. Откройте проект в Android Studio и выполните Sync.
3. Соберите `app`.

Если папки `llama.cpp` нет — соберется fallback JNI-стаб.

## Открыть в Android Studio

1. `File -> Open` -> выбрать корень этого репозитория.
2. Дождаться Gradle Sync.
3. Запустить `app` на устройстве Android 8.0+.
