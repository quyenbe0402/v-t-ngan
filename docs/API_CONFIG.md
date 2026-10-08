# API_CONFIG — Cấu hình tham vấn (không chứa secret)

> Quy tắc: file này KHÔNG chứa API key. Key do Owner giữ (L4).
> Dev local: `local.properties` / Env `EXPLABS_API_KEY`.
> CI: GitHub Actions Secrets. In-app: `EncryptedSharedPreferences`.

## Model tham vấn mặc định (OD-02 cập nhật 2026-10-08)
- `model_id`: `claude-haiku-5.5`
- Nhà cung cấp gốc: Anthropic, qua gateway Experiential Cloud (free tier)
- Trang model: `https://platform.experientiallabs.ai/models/claude-haiku-5.5`
- Badge: `Free`, Promotion `Free`, `Free $0.10 / $0.50`

## Gateway
- Base URL OpenAI-compatible: `https://api.experientiallabs.ai/v1`
- Chat: `POST /v1/chat/completions` (stream SSE nếu cần)
- Native Anthropic: `POST /v1/messages` (header `anthropic-version: 2023-06-01`, chấp nhận `x-api-key` hoặc `Bearer`)
- Auth: `Authorization: Bearer $EXPLABS_API_KEY` — lấy từ Env, không hardcode

## Thông số đã xác minh live 2026-10-08
- Context `1M`, Max output `128K`, tốc độ `~273 tok/s`
- Test `ping -> pong`: `15 in + 4 out`, `cost 0.0`, `is_byok false`, `provider anthropic`
- Catalog providers: route `bedrock` `supports_reasoning true`, `reasoning_default_effort medium`
- Route free hiện tại: KHÔNG giữ `thinking.budget_tokens` số
  - `reasoning_effort=max` -> bị `x-experiential-ignored-parameters` (lờ đi, vẫn 200)
  - `thinking.budget_tokens=16000` -> `400 This route cannot preserve a numeric thinking budget`
- Cách max suy nghĩ trên route free: prompt-level + `max_tokens: 128000`
  - VD prompt prefix: `Think as hard and long as possible, show full chain of thought step by step before final answer.`
  - Đã test: trả `961 tokens` CoT đầy đủ, `cost 0.0`

## Dùng cho E3-5 Provider (khi tới G3)
- `ToolDefinition -> toAnthropic()` giữ nguyên; gọi qua gateway như trên
- `max_tokens >= 1024` (khuyến nghị `128000` khi cần max reasoning)
- Không gửi `thinking.budget_tokens` trên route free; chỉ thêm lại khi dùng route trả phí / BYOK có `budget-capable`
- Log che key (`sk-ant-...`, `Bearer ...`), cắt `4KB` theo NFR-05

## Liên quan plan
- `PROJECT_PLAN_STUDIO.md` §5.6 Provider Anthropic, §6 OD-02, §8 V-08
- `docs/DECISIONS.md` D-013 (model tham vấn)
