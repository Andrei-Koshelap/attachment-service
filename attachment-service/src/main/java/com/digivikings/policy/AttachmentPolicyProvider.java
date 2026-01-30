package com.digivikings.policy;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AttachmentPolicyProvider implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AttachmentPolicyProvider.class);

    private final boolean enabled;
    private final String namespace;
    private final String name;

    private final KubernetesClient k8s; // may be null if disabled/unavailable
    private final AtomicReference<AttachmentPolicyModel> cache =
            new AtomicReference<>(AttachmentPolicyModel.defaults());

    public AttachmentPolicyProvider(
            @Value("${app.policy.enabled:true}") boolean enabled,
            @Value("${app.policy.namespace:attachments-demo}") String namespace,
            @Value("${app.policy.name:default-attachment-policy}") String name
    ) {
        this.enabled = enabled;
        this.namespace = namespace;
        this.name = name;
        this.k8s = buildClient();

        refreshPolicySafe("startup");
    }

    public AttachmentPolicyModel getPolicy() {
        return cache.get();
    }

    @Scheduled(fixedDelayString = "${app.policy.refresh-ms:30000}")
    public void scheduledRefresh() {
        refreshPolicySafe("scheduled");
    }

    private void refreshPolicySafe(String reason) {
        if (!enabled || k8s == null) return;

        try {
            Optional<AttachmentPolicyModel> loaded = loadFromKubernetes();
            loaded.ifPresent(p -> {
                cache.set(p);
                log.info("AttachmentPolicy loaded ({}): {}/{}, maxFileSizeMb={}, uploadTtl={}, downloadTtl={}",
                        reason, namespace, name, p.maxFileSizeMb(), p.presign().uploadTtlSeconds(), p.presign().downloadTtlSeconds());
            });
        } catch (Exception e) {
            log.warn("Failed to refresh AttachmentPolicy ({}). Using cached/default policy. reason={}",
                    reason, e.toString());
        }
    }

    private Optional<AttachmentPolicyModel> loadFromKubernetes() {
        @SuppressWarnings("unchecked")
        Map<String, Object> cr = (Map<String, Object>) k8s
                .genericKubernetesResources("attachments.demo/v1alpha1", "AttachmentPolicy")
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (cr == null) {
            log.warn("AttachmentPolicy not found: {}/{}", namespace, name);
            return Optional.empty();
        }

        Object specObj = cr.get("spec");
        if (!(specObj instanceof Map<?, ?> spec)) {
            log.warn("AttachmentPolicy has no spec: {}/{}", namespace, name);
            return Optional.empty();
        }

        return Optional.of(parseSpec(spec));
    }

    private AttachmentPolicyModel parseSpec(Map<?, ?> spec) {
        int maxFileSizeMb = intOrDefault(spec.get("maxFileSizeMb"), AttachmentPolicyModel.defaults().maxFileSizeMb());
        Set<String> allowed = parseStringSet(spec.get("allowedMimeTypes"), AttachmentPolicyModel.defaults().allowedMimeTypes());

        Map<?, ?> presignMap = mapOrEmpty(spec.get("presign"));
        int uploadTtl = intOrDefault(presignMap.get("uploadTtlSeconds"), AttachmentPolicyModel.defaults().presign().uploadTtlSeconds());
        int downloadTtl = intOrDefault(presignMap.get("downloadTtlSeconds"), AttachmentPolicyModel.defaults().presign().downloadTtlSeconds());

        Map<?, ?> limitsMap = mapOrEmpty(spec.get("limits"));
        int maxUploadsPerMin = intOrDefault(limitsMap.get("maxUploadsPerUserPerMinute"),
                AttachmentPolicyModel.defaults().limits().maxUploadsPerUserPerMinute());
        int maxActiveUploads = intOrDefault(limitsMap.get("maxActiveUploadsPerUser"),
                AttachmentPolicyModel.defaults().limits().maxActiveUploadsPerUser());

        Map<?, ?> behaviorMap = mapOrEmpty(spec.get("behavior"));
        boolean notifyOnClean = boolOrDefault(behaviorMap.get("notifyOnClean"), AttachmentPolicyModel.defaults().behavior().notifyOnClean());
        boolean notifyOnInfected = boolOrDefault(behaviorMap.get("notifyOnInfected"), AttachmentPolicyModel.defaults().behavior().notifyOnInfected());

        // hardening clamps
        maxFileSizeMb = clamp(maxFileSizeMb, 1, 1024);
        uploadTtl = clamp(uploadTtl, 60, 3600);
        downloadTtl = clamp(downloadTtl, 60, 3600);
        maxUploadsPerMin = clamp(maxUploadsPerMin, 1, 10_000);
        maxActiveUploads = clamp(maxActiveUploads, 1, 10_000);

        return new AttachmentPolicyModel(
                maxFileSizeMb,
                allowed,
                new AttachmentPolicyModel.Presign(uploadTtl, downloadTtl),
                new AttachmentPolicyModel.Limits(maxUploadsPerMin, maxActiveUploads),
                new AttachmentPolicyModel.Behavior(notifyOnClean, notifyOnInfected)
        );
    }

    private KubernetesClient buildClient() {
        if (!enabled) return null;

        try {
            return new DefaultKubernetesClient();
        } catch (Exception e) {
            log.warn("Kubernetes client is not available; using default policy only. reason={}", e.toString());
            return null;
        }
    }

    private static Map<?, ?> mapOrEmpty(Object obj) {
        return (obj instanceof Map<?, ?> m) ? m : Collections.emptyMap();
    }

    private static int intOrDefault(Object value, int def) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return def;
    }

    private static boolean boolOrDefault(Object value, boolean def) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }

    private static Set<String> parseStringSet(Object value, Set<String> def) {
        if (!(value instanceof Collection<?> c)) return def;
        Set<String> out = new LinkedHashSet<>();
        for (Object o : c) {
            if (o == null) continue;
            String s = o.toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out.isEmpty() ? def : Collections.unmodifiableSet(out);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void destroy() {
        try {
            if (k8s != null) k8s.close();
        } catch (Exception ignored) {}
    }
}
