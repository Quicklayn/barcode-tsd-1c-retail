# Разработка через OpenSpec

OpenSpec является единственным SDD-процессом проекта. Любое изменение кода,
контрактов, автоматизации, правил агентов или CI сначала оформляется как активное
изменение в `openspec/changes/<change-name>/`.

## Требования к рабочему месту

- Git;
- Node.js 20 или новее;
- PowerShell 7 (`pwsh`) для npm-команд и локальных хуков;
- JDK 17 и Android SDK для полного Android-прогона;
- платформа 1С, Apache и локальная read-only выгрузка `RT3/` только для
  интеграционного режима `Mvp`.

## Первичная настройка

Из корня репозитория выполните:

```powershell
.\scripts\setup\Initialize-Development.ps1
```

Скрипт выполняет `npm ci`, назначает версионируемые хуки через
`core.hooksPath=.githooks` и запускает быстрый quality gate. Команда
идемпотентна: ее можно повторять после обновления репозитория.

## Ежедневный цикл

1. Найдите активное изменение или создайте новое через навык
   `openspec-propose`:

   ```powershell
   npm exec -- openspec list
   npm exec -- openspec status --change <change-name>
   ```

2. До реализации завершите `proposal.md`, delta-спеки, `design.md` и
   `tasks.md`. Для выполнения получите актуальный контекст:

   ```powershell
   npm exec -- openspec instructions apply --change <change-name> --json
   ```

3. Реализуйте только задачи активного изменения. `RT3/**` не изменяется;
   backend 1С находится в `extension/**`, Android-клиент в `android/**`,
   контракт между ними в `docs/api/**`.

4. Отмечайте задачу в `tasks.md` только после ее проверки. Перед передачей на
   ревью выполните:

   ```powershell
   .\scripts\quality\Invoke-QualityGate.ps1 -Mode Fast -DiffMode Working
   .\scripts\quality\Invoke-QualityGate.ps1 -Mode Full -DiffMode Working
   ```

5. После независимого ревью, закрытия всех задач и синхронизации delta-спек
   проверьте и архивируйте изменение:

   ```powershell
   npm run openspec:validate
   npm exec -- openspec archive <change-name> --yes
   npm run openspec:validate
   ```

Архив `openspec/changes/archive/<date>-<change-name>/` сохраняется в Git как
доказательство принятых решений и выполненных проверок.

## Quality gates

| Режим | Назначение | Проверки |
|---|---|---|
| `Fast` | короткая обратная связь | строгий OpenSpec, синтаксис PowerShell, структура расширения/HTTP-сервиса/роли 1С, согласованность контракта, покрытие diff спецификацией |
| `Full` | эквивалент обязательного CI | все из `Fast`, Android assemble, JVM unit tests, lint и проверка `minSdk=26` в APK |
| `Mvp` | интеграционная приемка | все из `Full`, пересоздание временной ИБ 1С, web-публикация и Android-smoke с очисткой данных приложения |

`Mvp` зависит от локальной инфраструктуры и запускается вручную:

```powershell
.\scripts\quality\Invoke-QualityGate.ps1 -Mode Mvp -DiffMode Working
```

## Git hooks и CI

- `pre-commit` запускает `Fast` по staged diff;
- `pre-push` запускает `Full` по отправляемому диапазону и требует закрытых
  задач активного изменения; перед push рабочее дерево должно быть чистым, а
  дополнительные refs следует отправлять отдельно;
- `.github/workflows/quality-gate.yml` повторяет обязательные process,
  contract и Android-проверки на каждом pull request и push в `main`;
- `.github/workflows/mvp-smoke.yml` запускается вручную на self-hosted Windows
  runner с labels `self-hosted`, `Windows`, `X64`, `barcode-tsd`.

Self-hosted runner должен заранее содержать платформу 1С, Apache, Android SDK,
эмулятор и локальную выгрузку `RT3/`. Workflow использует `clean: false`, чтобы
checkout не удалял эту игнорируемую папку.

Хуки дают раннюю обратную связь, но не являются границей безопасности. Для
аварийной локальной операции доступны `git commit --no-verify` и
`git push --no-verify`; обязательным источником результата остается GitHub
Actions `Quality Gate`. В ruleset/branch protection репозитория этот workflow
нужно назначить required check; файлы репозитория не могут включить эту внешнюю
настройку автоматически.

## Граница перехода

Обязательный OpenSpec-процесс действует для изменений после архива
`establish-openspec-development-workflow`. Более ранние коммиты считаются
legacy-историей и не переоформляются задним числом. Старые архивы неизменяемы:
новая реализация всегда требует нового активного change.

## Субагенты

- `tsd-orchestrator` выбирает активное изменение и интегрирует результат;
- `tsd-1c-developer` пишет только в `extension/**`;
- `tsd-kotlin-developer` пишет только в `android/**`;
- `tsd-reviewer` не изменяет файлы и проверяет уже интегрированный diff.

Оркестратор владеет OpenSpec, `docs/api/**`, общими скриптами и CI, если явно
не назначена другая непересекающаяся область.

## Обновление правил и навыков

Набор `comol/ai_rules_1c` устанавливается только в проект официальным
manifest-aware установщиком. Глобальная установка в `~/.codex` не используется.
Версия `generatedBy` внутри управляемых skills относится к prompt bundle;
исполняемая версия OpenSpec всегда берется из корневого `package-lock.json`.

```powershell
$rulesSource = Join-Path $env:TEMP "ai_rules_1c"
git clone https://github.com/comol/ai_rules_1c.git $rulesSource
& "$rulesSource\install.ps1" update -Source $rulesSource -Tools codex -NonInteractive -AssumeYes -McpMode auto
& "$rulesSource\install.ps1" doctor -Source $rulesSource -Tools codex -NonInteractive
```

Если временный clone уже существует, сначала обновите его обычным `git pull`
или укажите новый пустой путь. Установщик обновляет управляемые файлы по
`.ai-rules.json` и сохраняет проектные overlays. После обновления перезапустите
Codex, чтобы перечитать профили агентов, навыки и MCP-конфигурацию.

## Восстановление

- зависимости или локальный OpenSpec отсутствуют: повторите
  `.\scripts\setup\Initialize-Development.ps1`;
- хуки отключены или указывают не туда: выполните
  `git config core.hooksPath .githooks` или повторите bootstrap;
- нужно временно вернуть стандартный поиск хуков Git: выполните
  `git config --unset core.hooksPath`;
- quality gate не видит спецификацию: добавьте в тот же diff активные
  OpenSpec-артефакты и не обходите проверку изменением скрипта;
- `Full` не находит `apkanalyzer`: установите Android SDK Command-line Tools и
  убедитесь, что Android SDK доступен через окружение.
