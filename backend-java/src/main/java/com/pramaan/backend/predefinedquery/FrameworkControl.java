package com.pramaan.backend.predefinedquery;

/**
 * One control row from a compliance/security framework's control set (PCI DSS,
 * DPSE, ITPP, OS/database/middleware baselines, VAPT, C-SITE, ITDRM, Internal
 * Audit, …). Reference data only — see {@link FrameworkControlCatalog}.
 */
public record FrameworkControl(
        String framework,
        String controlCode,
        String controlName,
        String controlDescription,
        String sourceStatus,
        String sourceReference) {}
