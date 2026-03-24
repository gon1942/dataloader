from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    paddle_base_url: str = os.getenv("PADDLE_BASE_URL", "http://localhost:8080")
    paddle_health_path: str = os.getenv("PADDLE_HEALTH_PATH", "/v1/models")
    paddle_convert_path: str = os.getenv("PADDLE_CONVERT_PATH", "/v1/chat/completions")
    request_timeout_seconds: float = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "120"))
    log_level: str = os.getenv("LOG_LEVEL", "INFO")

    def paddle_health_url(self) -> str:
        return self._join(self.paddle_health_path)

    def paddle_convert_url(self) -> str:
        return self._join(self.paddle_convert_path)

    def _join(self, path: str) -> str:
        return self.paddle_base_url.rstrip("/") + "/" + path.lstrip("/")


settings = Settings()
