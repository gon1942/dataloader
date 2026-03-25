FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libgl1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
ENV PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py config.py transform.py ./

EXPOSE 8080

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080"]
