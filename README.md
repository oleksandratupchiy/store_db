# store_db — лабораторні роботи №1 і №2

Java 17+, Maven Wrapper, Spring Boot 4.1.1. Лабораторна №1 лишається без змін:
PostgreSQL `store_db`, JDBC, транзакції, Flyway V1/V2. [Звіт №2](docs/lab2_nosql.md).

## Передумови й безпека

- Windows PowerShell 5.1 або PowerShell 7, JDK 17+ (`java -version`).
- Docker Desktop має бути встановлений **і запущений**, з Linux containers, для наведеного Compose-сценарію.
  Без Docker можна встановити MongoDB, Redis і PostgreSQL окремо та задати ENV.
- Інтернет потрібний під час першого завантаження Maven-залежностей і Docker-образів.
- Python 3 і matplotlib потрібні лише для графіків (`python -m pip install matplotlib`).
- Виконуйте команди в корені репозиторію, гілка `lab2-nosql`.

Основна `store_db` не очищується, не мігрується CLI і не використовується генератором.
Compose створює **інший PostgreSQL-сервер** на порту 55432 і базу `store_db_benchmark`.
Локальний PostgreSQL на 5432 залишається окремим. Усі записи генератора захищені
перевіркою `current_database() = 'store_db_benchmark'`.
Кожний count/seed має власну схему `lab2_n1000_s42`; MongoDB має однойменну базу.
V1/V2 застосовуються Flyway до кожної нової тестової схеми. V3 з `db/lab2` додає індекси
лише там: звичайний Spring-застосунок її автоматично не підхоплює.

## Швидка демонстрація у PowerShell

```powershell
git branch --show-current
docker info
docker compose -f docker-compose.yml -f docker-compose.noavx.yml --profile bench up -d --wait
.\mvnw.cmd -B '-Dtest=Lab2Test' test
# Почніть з малого набору: створення SQL → імпорт Mongo → перевірка → вимірювання
.\scripts\lab2.ps1 generate -Count 100 -Seed 42
.\scripts\lab2.ps1 import -Count 100 -Seed 42
.\scripts\lab2.ps1 show -Count 100 -Seed 42
.\scripts\lab2.ps1 verify -Count 100 -Seed 42
.\scripts\lab2.ps1 benchmark -Count 100 -Seed 42 -Repeats 200 -Warmup 50
.\scripts\redis-demo.ps1
.\scripts\mongo-demo.ps1
```

**На цій машині Docker VM не надає AVX:** MongoDB 8 завершується з кодом 132.
Для демонстрації тут використано цей окремий варіант запуску:

```powershell
docker compose -f docker-compose.yml -f docker-compose.noavx.yml --profile bench up -d --wait
```

Це офіційний MongoDB **4.4.29**, застарілий варіант тільки для локальної лабораторної,
з окремим volume. Не перемикайте каталог даних між 8.0 і 4.4. На CPU з AVX
можна використовувати `docker compose --profile bench up -d --wait` з MongoDB 8.0
і робити нові заміри. Результати різних версій не слід змішувати в одному висновку.
`mongo-demo.ps1` автоматично вибирає доступний `mongosh` або `mongo`.

Якщо ExecutionPolicy забороняє `.ps1`, запустіть для поточного процесу
`Set-ExecutionPolicy -Scope Process Bypass` або використовуйте прямий Maven-виклик:

```powershell
.\mvnw.cmd -B compile exec:java '-Dexec.args=generate 1000 42'
.\mvnw.cmd -B compile exec:java '-Dexec.args=import 1000 42'
.\mvnw.cmd -B compile exec:java '-Dexec.args=benchmark 1000 42 200 50'
```

## Три масштаби та результати

```powershell
.\scripts\benchmark.ps1 -Sizes 1000,10000,100000 -Seed 42 -Repeats 200 -Warmup 50
python .\scripts\analyze_results.py # після щонайменше трьох запусків кожного основного масштабу
python .\scripts\plot_results.py
Get-ChildItem .\testing\results
```

Генератор відмовиться повторно заповнювати вже непорожній набір. Це навмисно:
нічого не видаляється. Повторний benchmark на тих самих даних:

```powershell
.\scripts\benchmark.ps1 -Sizes 1000,10000,100000 -Reuse
```

CSV з вимірюваннями: `testing/results/benchmark_*.csv`; `verify_*.csv` — лише коротка
перевірка, **не** повноцінний benchmark. SVG: `median_ms.svg`, `p95_ms.svg`.
Кожний запуск створює новий CSV. Аналіз обчислює середнє, вибіркове s і min/max
медіан між запусками. Графіки показують середні метрик між запусками та вуса min–max.
Числа у звіті мають відповідати цим реальним CSV.

У поточному workspace вже підготовлено набори 100, 1 000, 10 000 і 100 000
з seed=42, збережено реальні CSV та графіки. Для демонстрації достатньо `show`,
`verify`, Redis/Mongo demo; для повторного вимірювання використовуйте `-Reuse`.

## Імпорт із наявної store_db (тільки читання PostgreSQL)

Це необов'язкова окрема демонстрація. Платежів у наявному замовленні може не бути:
тоді число завершених замовлень буде 0. Генератор це замовлення не змінює.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/store_db'
$env:DB_USER = 'postgres'
$credential = Get-Credential -UserName postgres -Message 'Пароль локального PostgreSQL'
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
.\scripts\lab2.ps1 import-source
Remove-Item Env:DB_PASSWORD
```

Імпорт відкриває read-only repeatable-read транзакцію, читає сторінки по 500,
робить upsert за `_id` у `lab2_import.order_history`; звіряє до 10 зразків.
Видалення не виконується. Повторний імпорт оновлює наявні документи, але не видаляє
документи замовлень, які перестали відповідати правилу завершення. Це batch-знімок,
а не повноцінна онлайн-синхронізація. Імпортуйте лише дозволені навчальні дані.

## ENV

| Змінна | Локальне значення за замовчуванням |
|---|---|
| `LAB2_PG_URL` | `jdbc:postgresql://localhost:55432/store_db_benchmark` |
| `LAB2_PG_USER` | `lab2` |
| `LAB2_PG_PASSWORD` | `lab2_local_only` (тільки демонстрація Compose) |
| `LAB2_MONGO_URI` | `mongodb://localhost:27017/?serverSelectionTimeoutMS=5000` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | лише `import-source`, як у першій лабораторній |
| `LAB2_PG_PORT`, `LAB2_MONGO_PORT`, `LAB2_REDIS_PORT` | порти Compose: 55432, 27017, 6379 |

Якщо змінюєте порт Compose, змініть відповідний URI для Java.
ENV задається через `$env:NAME = 'value'`. `.env` читає Compose, Java CLI читає
змінні процесу; не зберігайте паролі в Git. Зміна пароля контейнера з наявним
volume не змінює пароль ролі PostgreSQL автоматично.

Щоб використати існуючий локальний PostgreSQL-сервер **для окремої тестової бази**,
створіть через pgAdmin/psql нову базу `store_db_benchmark` та задайте:

```powershell
# Виконати в psql, підключеному до postgres, один раз:
# CREATE DATABASE store_db_benchmark;
$env:LAB2_PG_URL = 'jdbc:postgresql://localhost:5432/store_db_benchmark'
$env:LAB2_PG_USER = 'postgres'
$credential = Get-Credential -UserName postgres
$env:LAB2_PG_PASSWORD = $credential.GetNetworkCredential().Password
docker compose up -d --wait mongo redis
```

## Перевірки і типові проблеми

- `docker info` / `docker compose ps`: якщо daemon недоступний — запустіть Docker Desktop,
  дочекайтесь готовності Linux engine. Обмежене середовище може потребувати дозволу на доступ до Docker.
- Порт зайнятий: `Get-NetTCPConnection -LocalPort 27017` (або інший порт);
  змініть ENV-порт Compose та Java URI, не зупиняйте чужий сервіс.
- PostgreSQL недоступний: `docker compose logs postgres-bench`, перевірте URL, порт, користувача,
  пароль і стан контейнера. В основній `store_db` нічого виправляти генератором не потрібно.
- `relation does not exist`: спочатку `generate` з тим самим count/seed, потім `import`.
- `Dataset already exists`: застосуйте `-Reuse`, або інший seed. Скрипти не очищують дані.
- `SQL/Mongo mismatch` / `Dataset count mismatch`: повторіть `import` для того самого набору;
  вимірювання при розбіжності припиняється. Не редагуйте дані під час benchmark.
- Maven потребує доступу до `%USERPROFILE%\.m2` та Maven Central. JDK 17+ має бути в PATH.
  Попередній дефект Wrapper з порожнім `Target` у Windows виправлено в `mvnw.cmd`.
- Redis-демо сумісне з Windows PowerShell 5.1: аргументи передаються іменованим
  масивом, а `redis-cli -e` повідомляє про помилки сервера ненульовим кодом.
  Перевірка аргументів і кодів завершення без Docker:
  `powershell.exe -NoProfile -File testing/check-demo-scripts.ps1`.
- Старі `CheckoutServiceTest` виконують записи та потребують fixtures `900001`.
  Не запускайте повний `mvn test` проти основної бази. Новий `Lab2Test` не підключається до БД.
  Безпечний повний запуск: `.\scripts\test-isolated.ps1` (Compose PostgreSQL, окрема
  схема `lab2_n10_s900001`; повторно — `.\scripts\test-isolated.ps1 -Reuse`).
  Скрипт відновлює ENV після тестів, fixtures стосуються лише цієї тестової схеми.
- `docker compose stop` зупиняє тільки лабораторні контейнери і зберігає дані.
  Не використовуйте `down -v`, якщо потрібні збережені тестові набори.

Для Unix: `./mvnw -B compile exec:java '-Dexec.args=benchmark 1000 42 200 50'`;
команди Docker і Python аналогічні.

## Скриншоти для захисту

1. `docker compose ps` — три healthy-сервіси, окремий порт PostgreSQL.
2. `generate`/Flyway — V1, V2, V3 та кількість створених замовлень.
3. `show` і `Equivalence PASS` — однакові SQL/Mongo документи.
4. MongoDB документ із вкладеними items/payments та індекси (`getIndexes()`).
5. CSV на трьох масштабах, обидва SVG-графіки.
6. Redis CRUD/TTL та рейтинг із `WITHSCORES`.
7. За потреби — тести `Lab2Test` з `BUILD SUCCESS`.
