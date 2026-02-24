# HybridAssistant (Android)

Android-ассистент с голосом + чатом + управлением устройством и LLM.

## Что реализовано

- Переименование проекта: **HybridAssistant**, package `com.hybridassistant`.
- Чат:
  - текстовый ввод;
  - голосовой ввод;
  - кнопка `Copy` у каждого сообщения.
- Устройство:
  - открыть ссылку;
  - поиск в интернете;
  - открыть приложение;
  - поиск YouTube / Play Market;
  - клик по тексту через AccessibilityService.
- Модели (10 шт) с **реальными URL** GGUF (Hugging Face / TheBloke / Bartowski).
- Загрузка моделей через встроенный resumable downloader (OkHttp + Range):
  - прогресс-бар;
  - докачка после разрыва сети с того же места по кнопке "Продолжить";
  - автоматические повторные попытки при временных сетевых сбоях.
- Реальные ответы LLM через Cloud:
  - OpenRouter API (нужен API key);
  - выбор cloud-model в настройках.
- JNI scaffold для `llama.cpp` (локальный on-device путь).

## Как получать реальные ответы LLM

1. Откройте вкладку **Модели**.
2. Введите `OpenRouter API Key`.
3. Введите модель, например: `openai/gpt-4o-mini`.
4. Нажмите **Сохранить Cloud настройки**.
5. Пишите вопросы в чат — ассистент будет отвечать через реальный LLM API.

## Локальная модель (llama.cpp)

1. Положите `llama.cpp` в `app/src/main/cpp/third_party/llama.cpp`.
2. Sync проект в Android Studio.
3. Соберите проект.

## Запуск

1. `File -> Open` в Android Studio.
2. Дождитесь Gradle Sync.
3. Запустите `app` на Android 8.0+.
