# Security Notes: unfurl-foundry-substrate

Foundry substrate records model agent, tool, model, and prompt boundaries.

## Guidance

- Do not place provider credentials or raw secret values in substrate records.
- Treat tool descriptors and prompt bundles as governed inputs.
- Keep model/provider data contracts separate from runtime credentials.
- Preserve audit-friendly fields for agent decisions and tool calls.

