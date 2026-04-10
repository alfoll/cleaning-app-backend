package com.cleaningapp.backend.transaction

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class TransactionController(
    private val transactionService: TransactionService
) {
    // посмотреть мои транзакции в рамках хозяйства - GET /api/households/{householdId}/transactions/my
    @GetMapping("/households/{householdId}/transactions/my")
    fun getMyTransactions(@PathVariable householdId: UUID): List<TransactionResponseDTO> =
        transactionService.getMyTransactions(householdId)
}