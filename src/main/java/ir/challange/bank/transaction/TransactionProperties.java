package ir.challange.bank.transaction;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "transaction")
public record TransactionProperties(
        Duration pendingTtl,
        Duration expirePendingFixedDelay
) {}
