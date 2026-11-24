# Instructions for contributors

- Use Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Every behavior change or bug fix must include automated tests; run the relevant tests before committing.
- Never commit secrets, tokens, private keys, or credentials. Read integration keys only from environment variables.
- Keep this README accurate whenever setup, behavior, or architecture changes.
- Prefer simple, focused code and avoid abstractions without a concrete use.
- Use synthetic data only. Never add real personal, investor, customer, or employee data to source, fixtures, logs, or documentation.
- The ConviFin module is an educational demonstration, not regulated investment advice or proof of MiFID II compliance.
- The profiling score must be computed only by deterministic, unit-tested rules. LLM output may guide the interview and extract structured answers, never calculate or alter the score.
- Profiling answers and results are session-only in the MVP. Do not persist or log them.
