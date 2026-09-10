<p align="center">
  <img src="docs/branding/header.svg" width="960" alt="Сообщения — свободный SMS-клиент для Android">
</p>

<p align="center">
  <a href="https://github.com/IgorNadein/Messages/releases/latest"><img src="https://img.shields.io/github/v/release/IgorNadein/Messages?style=flat-square&amp;color=6750a4" alt="Последний выпуск"></a>
  <a href="https://github.com/IgorNadein/Messages/actions/workflows/android-release.yml"><img src="https://github.com/IgorNadein/Messages/actions/workflows/android-release.yml/badge.svg" alt="Android Release APK"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-455a64?style=flat-square" alt="Android 7.0+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-455a64?style=flat-square" alt="GPLv3"></a>
</p>

<p align="center">
  <strong>SMS и MMS без рекламы, с современным интерфейсом, защищёнными чатами и расширяемой доставкой.</strong>
</p>

<p align="center">
  <a href="https://github.com/IgorNadein/Messages/releases/latest"><strong>Скачать APK</strong></a> ·
  <a href="https://github.com/IgorNadein/Messages/issues/new?template=bug_report.md">Сообщить о проблеме</a> ·
  <a href="docs/media-transport-architecture.md">Архитектура медиа</a>
</p>

## О приложении

**Сообщения** — независимое развитие открытого Android-клиента [DekuSMS](https://github.com/deku-messaging/Deku-SMS-Android). Приложение может работать как основной SMS-клиент, хранит переписку локально и использует системную базу Android для совместимости с другими приложениями.

| Повседневное общение | Защита и медиа | Интеграции |
| --- | --- | --- |
| SMS, MMS, групповые диалоги, поиск, архив и закрепление | Сквозное шифрование с Double Ratchet, фото, файлы и голосовые сообщения | HTTP(S), FTP/SFTP, SMTP, RabbitMQ и собственные облачные хранилища |
| Поддержка нескольких SIM-карт | Выбор MMS, Data SMS, совместимых SMS-пакетов или интернет-хранилища | Телефон как SMS-шлюз и маршрутизация входящих сообщений |

> Защищённые сообщения и специальные способы передачи медиа требуют совместимого приложения у собеседника. Обычные SMS и MMS продолжают работать стандартным способом и могут тарифицироваться оператором.

## Интерфейс

<table>
  <tr>
    <th>Входящие</th>
    <th>Защищённый диалог</th>
    <th>Первый запуск</th>
  </tr>
  <tr>
    <td><img src="artifacts/one-ui/oneui_vertical_inbox.png" width="230" alt="Список диалогов"></td>
    <td><img src="artifacts/one-ui/oneui_vertical_secure_conversation.png" width="230" alt="Защищённый диалог"></td>
    <td><img src="artifacts/one-ui/oneui_vertical_onboarding.png" width="230" alt="Настройка приложения по умолчанию"></td>
  </tr>
</table>

## Скачать и обновить

Откройте [последний выпуск](https://github.com/IgorNadein/Messages/releases/latest) и выберите файл `Messages-….apk` в разделе **Assets**. Поддерживается Android 7.0 и новее.

После первой установки обновления доступны прямо в приложении: **Настройки → Обновления**. Приложение проверяет стабильный GitHub Release, загружает APK с отображением прогресса и перед установкой сверяет размер, контрольную сумму, пакет, номер версии и сертификат подписи. Установку всегда подтверждает системный установщик Android; сообщения и настройки при обновлении сохраняются.

> APK другого издателя или сборка с другой подписью не сможет обновить уже установленное приложение. Это штатная защита Android.

## Собрать из исходников

Нужны JDK 17 или 21 и Android SDK 36. Из корня проекта:

```bash
./gradlew :app:assembleDebug
```

Отладочные APK появятся в `app/build/outputs/apk/debug/`. Debug-сборка устанавливается отдельно с суффиксом `.audit` и не заменяет опубликованную версию.

Проверки JVM:

```bash
./gradlew :app:testDebugUnitTest
```

[Подпись и выпуск APK](docs/github-updates.md) · [Архитектура передачи медиа](docs/media-transport-architecture.md) · [Матрица регрессий](REGRESSION_MATRIX.md)

## Происхождение и лицензия

Проект основан на DekuSMS и сохраняет историю и уведомления об авторских правах исходного проекта и его зависимостей. Исходный код распространяется по [GNU GPL v3](LICENSE). Название, оформление и новые изменения этого форка не означают одобрения со стороны авторов DekuSMS.

---

[Разработчик — IgorNadein](https://github.com/IgorNadein) · [Issues](https://github.com/IgorNadein/Messages/issues) · [Все выпуски](https://github.com/IgorNadein/Messages/releases)
