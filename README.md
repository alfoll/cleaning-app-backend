# 🧹 CleaningApp Backend

**Backend-часть приложения для геймификации уборки и распределения домашних обязанностей.**

## 📋 О проекте

CleaningApp превращает скучные бытовые обязанности в увлекательную игру с элементами соревнования. Приложение помогает избегать конфликтов в общих жилых пространствах (квартирах, общежитиях) через систему виртуальной валюты и привилегий.

### 🎯 Ключевые возможности
- **Геймификация рутинных задач** с начислением внутренней валюты (ClutterCoin)
- **Система привилегий**, которые пользователи устанавливают сами
- **Рейтинги и лидерборды** для здоровой конкуренции
- **Лента активности** для отслеживания событий в домохозяйстве
- **JWT-аутентификация** и ролевая модель

## 🏗️ Архитектура

### Технологический стек
- **Backend:** Spring Boot 3.x, Kotlin
- **База данных:** PostgreSQL
- **Аутентификация:** JWT (JSON Web Tokens)
- **Миграции БД:** Liquibase
- **Сборка:** Gradle (Kotlin DSL)

### Сущности базы данных
<img width="1271" height="772" alt="image" src="https://github.com/user-attachments/assets/5991c447-fb2b-40b9-961a-fa38373bb57a" />

### Эндпойнты регистрации
1) Зарегистрировать юзера (сохранить в бд) - только с наличием валидного Firebase токена (передается Authorization Bearer <idToken>)
   <img width="850" height="512" alt="image" src="https://github.com/user-attachments/assets/f39ed3bb-5fd9-403f-935c-96385ac93c1f" />


### Эндпойнты юзера
1) Получить свой профиль
 <img width="860" height="521" alt="image" src="https://github.com/user-attachments/assets/11fd0f03-a687-42b5-8d09-d80cc1cb3175" />

2) Изменить свой профиль (имя + почта)
   <img width="880" height="515" alt="image" src="https://github.com/user-attachments/assets/9514c76f-c34f-4e44-9efc-1aa77f1e1e5d" />

3) Удалить свой профиль
   <img width="853" height="411" alt="image" src="https://github.com/user-attachments/assets/4516dfb8-659d-41e4-9c94-e9c408307402" />


