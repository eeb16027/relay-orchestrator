package com.relay.mock.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny self-contained "external system" that workflows call via
 * HTTP_REQUEST nodes, so demos never depend on internet access or a
 * third-party site like httpbin.org. It deliberately mimics the one
 * behavior that matters for this project: recognizing a duplicate
 * Idempotency-Key and refusing to double-process it - this is what
 * proves crash recovery doesn't duplicate side effects.
 *
 * State is in-memory only (ConcurrentHashMap - safe under the worker
 * pool's concurrent threads) and resets on app restart, or on demand
 * via /mock-world/reset for repeatable demo runs within one session.
 */
@RestController
@RequestMapping("/mock-world")
public class MockWorldController {

    private static final Logger log = LoggerFactory.getLogger(MockWorldController.class);

    private final Map<String, OrderRecord> ordersByIdempotencyKey = new ConcurrentHashMap<>();
    private final AtomicInteger totalCallsReceived = new AtomicInteger();

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) JsonNode body) {

        int callNumber = totalCallsReceived.incrementAndGet();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Mock world received a request with NO idempotency key (call #{})", callNumber);
            return ResponseEntity.ok(Map.of(
                    "orderId", UUID.randomUUID().toString(),
                    "duplicate", false,
                    "warning", "no idempotency key provided",
                    "totalCallsReceived", callNumber
            ));
        }

        OrderRecord existing = ordersByIdempotencyKey.get(idempotencyKey);
        if (existing != null) {
            existing.hitCount++;
            log.warn("Mock world DETECTED DUPLICATE call for idempotencyKey={} (hitCount={}) - " +
                    "returning the original order without reprocessing.", idempotencyKey, existing.hitCount);
            return ResponseEntity.ok(Map.of(
                    "orderId", existing.orderId,
                    "duplicate", true,
                    "hitCount", existing.hitCount,
                    "totalCallsReceived", callNumber
            ));
        }

        OrderRecord record = new OrderRecord(UUID.randomUUID().toString(), body, Instant.now());
        ordersByIdempotencyKey.put(idempotencyKey, record);
        log.info("Mock world placed NEW order orderId={} for idempotencyKey={} (call #{})",
                record.orderId, idempotencyKey, callNumber);

        return ResponseEntity.ok(Map.of(
                "orderId", record.orderId,
                "duplicate", false,
                "hitCount", 1,
                "totalCallsReceived", callNumber
        ));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        return ordersByIdempotencyKey.values().stream()
                .filter(r -> r.orderId.equals(orderId))
                .findFirst()
                .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                        "orderId", r.orderId,
                        "receivedAt", r.receivedAt.toString(),
                        "hitCount", r.hitCount
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Clears all in-memory state so a demo can be re-run cleanly without restarting the app. */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        int cleared = ordersByIdempotencyKey.size();
        ordersByIdempotencyKey.clear();
        totalCallsReceived.set(0);
        log.info("Mock world reset - cleared {} order record(s).", cleared);
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "distinctOrders", ordersByIdempotencyKey.size(),
                "totalCallsReceived", totalCallsReceived.get()
        ));
    }

    private static class OrderRecord {
        final String orderId;
        final JsonNode requestBody;
        final Instant receivedAt;
        int hitCount = 1;

        OrderRecord(String orderId, JsonNode requestBody, Instant receivedAt) {
            this.orderId = orderId;
            this.requestBody = requestBody;
            this.receivedAt = receivedAt;
        }
    }
}