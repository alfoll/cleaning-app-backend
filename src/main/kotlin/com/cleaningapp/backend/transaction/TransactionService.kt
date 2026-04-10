package com.cleaningapp.backend.transaction

import java.util.UUID

interface TransactionService {

    // внутренние методы
    // создать транзакцию начисления монет за выполнение задачи
    fun recordTaskCompletion(command: TaskCompletionTransactionCommand)

    // создать транзакцию списания монет за покупку привилегии
    fun recordPrivilegePurchase(command: PrivilegePurchaseTransactionCommand)

    // создать транзакцию ресета баланса в случае выхода/удаления участника из хозяйства
    fun resetBalance(command: BalanceResetTransactionCommand)


    // внешний метод - для вызова из контроллера
    // транзакции участника
    fun getMyTransactions(householdId: UUID): List<TransactionResponseDTO>
}