package com.example.expense_tracking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sync")
@Data
public class SyncConfig {
    // Enable/disable automatic background sync
    private boolean enabled = true;
    // How often to run the sync
    private int intervalMinutes = 15;
    // How many days back to  fetch on incremental sync
    private int lookBackDays = 3;
    // Only sync data for users active within this 30 days
    private int activeUserDays = 30;
    // How many days of history to fetch for newly linked accounts
    private int initialSyncDays = 90;
}
