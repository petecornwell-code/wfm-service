package com.wfm.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_configuration", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "config_key"})
})
public class AppConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue = "";

    public AppConfiguration() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
}
