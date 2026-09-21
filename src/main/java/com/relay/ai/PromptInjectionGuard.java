package com.relay.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort text-level filter against prompt injection embedded in
 * untrusted data (trigger payloads, upstream step outputs) before it
 * reaches the AI provider. This is deliberately NOT the primary
 * defense - keyword/regex filtering can be evaded by a creative enough
 * payload. The real structural protection is that approval gates and
 * the run's step cap are enforced against database state in
 * StepExecutionService / StepResultPersister, never against anything
 * the model outputs - so even content that fully evades this filter
 * cannot make a sensitive node execute without a real Approval record,
 * and cannot make a run loop past its max-step guardrail.
 */
@Component
public class PromptInjectionGuard {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuard.class);

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore (all|any|previous|the above) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (all|any|previous) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal (your|the) (system prompt|instructions)", Pattern.CASE_INSENSITIVE)
    );

    public String sanitize(String text) {
        String result = text;
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                log.warn("Potential prompt-injection pattern neutralized in AI node input: {}", pattern.pattern());
                result = matcher.replaceAll("[neutralized]");
            }
        }
        return result;
    }
}