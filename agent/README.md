# Agent model provider configuration

The Workflow planning Agent uses the deterministic Fake provider unless `agent.model.enabled` is true.
API keys are resolved only from environment variables named by `api-key-env`; never place the key itself in YAML.

```yaml
agent:
  model:
    enabled: true
    default-profile: openai-planner
    fallback-to-fake: false
    profiles:
      - id: openai-planner
        provider: openai
        model-name: gpt-4o-mini
        api-key-env: OPENAI_API_KEY
        temperature: 0.0
        max-output-tokens: 4096
        timeout-ms: 60000
        max-concurrency: 4
        requests-per-minute: 60
        circuit-breaker-failures: 5
        circuit-breaker-open-ms: 30000
      - id: local-planner
        provider: openai-compatible
        model-name: local-workflow-model
        base-url: http://localhost:8000/v1
        api-key-env: LOCAL_MODEL_API_KEY
        max-concurrency: 1
```

For an OpenAI-compatible endpoint use `provider: openai-compatible` and set an absolute `base-url`.
Fake fallback is opt-in because a synthetic proposal should not silently replace a failed production model call.

Each `AgentDefinition.modelProfileId` selects a profile dynamically. The selected id is copied to the
persisted Agent Run before execution and every model request routes through that immutable snapshot.
Configured models and their secrets are initialized lazily on first use, then cached per profile.
