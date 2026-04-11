package com.cleaningapp.backend.activity

// тип события а не режим выборки - поэтому нет ALL
enum class ActivityType {
    HOUSEHOLD_CREATED, // создано хозяйство (обновлено - пока не нужно, удалено - не нужно для активности)

    USER_JOINED, // участник присоединился
    USER_LEFT, // участник вышел (при удалении профиля участника - тоже как выход)
    USER_REMOVED, // участника удалили

    TASK_CREATED, // задача создана (обновление - подумать, удаление - не нужно пока)
    TASK_ASSIGNED, // задачу забронировали
    TASK_UNASSIGNED, // задачу освободили
    TASK_COMPLETED, // задачу выполнили

    PRIVILEGE_CREATED, // привилегия создана (обновление - подумать, удаление - не нужно пока)
    PRIVILEGE_BOUGHT, // привилегию купили
}